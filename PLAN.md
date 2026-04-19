# OTP Forwarder — Multi-Condition / Multi-Action Rules

## Context

The current Add/Edit Rule screen forces a single OTP type and two optional regex filters, and the only outcome is "forward SMS to the rule's recipients." That model is too narrow:

- A user may want to match on multiple criteria (e.g., *parcel OTP AND from SWIGGY*, or *transaction OTP OR a body containing "credited"*).
- OTP types and regexes should be presented in plain English so a non-technical user understands that `[sender]` / `[text]` are user-supplied values.
- A rule should be able to trigger more than just an SMS forward — also "set the phone to loud mode" and "place a call."

This redesign makes conditions and actions first-class, many-per-rule objects with a natural-language UI, and adds the infrastructure (permissions, use cases, action dispatcher) to execute the new action types silently in the background from the existing `OtpProcessingService` pipeline.

Confirmed during planning:

- **Connector semantics**: per-pair AND/OR, evaluated strictly left-to-right (no precedence).
- **Loud mode**: `RINGER_MODE_NORMAL` + `STREAM_RING` to max + exit Do Not Disturb if Notification Policy access is granted.
- **Call targets**: reuse the existing `Recipient` table for both the forward and call actions.

---

## Phase 1 — Domain model & Room schema

Goal: replace the flat `ForwardingRule` with ordered `conditions` + `actions`, migrate existing data non-destructively, and keep the app compiling at the data layer before touching business logic.

### 1.1 Domain models (new / modified)

`app/src/main/java/com/otpforwarder/domain/model/ForwardingRule.kt` — restructure:

```kotlin
data class ForwardingRule(
    val id: Long = 0,
    val name: String,
    val isEnabled: Boolean = true,
    val priority: Int,
    val conditions: List<RuleCondition>,   // ordered; index 0 has no connector
    val actions: List<RuleAction>
)

sealed interface RuleCondition {
    val connector: Connector   // operator joining THIS condition to the previous one; ignored for index 0
    data class OtpTypeIs(val type: OtpType, override val connector: Connector) : RuleCondition
    data class SenderMatches(val pattern: String, override val connector: Connector) : RuleCondition
    data class BodyContains(val pattern: String, override val connector: Connector) : RuleCondition
}

enum class Connector { AND, OR }

sealed interface RuleAction {
    data class ForwardSms(val recipientIds: List<Long>) : RuleAction
    data object SetRingerLoud : RuleAction
    data class PlaceCall(val recipientId: Long) : RuleAction
}
```

Keep `OtpType` enum as-is. The `otpType`, `senderFilter`, `bodyFilter` fields on the old model are dropped — they become condition instances.

New files: `domain/model/RuleCondition.kt`, `domain/model/RuleAction.kt`, `domain/model/Connector.kt`.

### 1.2 Room schema (bump DB version 1 → 2)

`app/src/main/java/com/otpforwarder/data/local/`:

- **Modify** `ForwardingRuleEntity`: drop `otpType`, `senderFilter`, `bodyFilter` columns; keep `id`, `name`, `isEnabled`, `priority`.
- **New** `RuleConditionEntity` (table `rule_conditions`):
  - `id` PK, `ruleId` FK→rules (CASCADE), `orderIndex` Int, `connector` String ("AND"/"OR"),
  - `conditionType` String ("OTP_TYPE"/"SENDER_REGEX"/"BODY_REGEX"),
  - `otpTypeValue` String? (only for OTP_TYPE),
  - `pattern` String? (only for SENDER_REGEX / BODY_REGEX).
  - Indexed on `ruleId`.
- **New** `RuleActionEntity` (table `rule_actions`):
  - `id` PK, `ruleId` FK→rules (CASCADE), `orderIndex` Int,
  - `actionType` String ("FORWARD_SMS"/"RINGER_LOUD"/"PLACE_CALL"),
  - `callRecipientId` Long? (PLACE_CALL only, FK→recipients SET_NULL).
  - Indexed on `ruleId`.
- **Repurpose** `RuleRecipientCrossRef` → `ActionRecipientCrossRef` (`actionId` FK→`rule_actions` CASCADE, `recipientId` FK→`recipients` CASCADE). Primary key `(actionId, recipientId)`. This generalizes "which recipients does this forward action target".
- **New** `RuleWithDetails` Room relation class aggregating rule + ordered conditions + ordered actions (+ per-forward-action recipients).
- **Migration_1_2** (real migration, not destructive): for each existing rule, create conditions from its `otpType`/`senderFilter`/`bodyFilter` (with `AND` connectors), create one `FORWARD_SMS` action, and migrate its `rule_recipient_cross_ref` rows to `action_recipient_cross_ref` rows keyed on the new action id. Add migration to the Hilt `DatabaseModule` (`di/DatabaseModule.kt`).
- Update `AppDatabase` entity list + `version = 2`, add `ruleConditionDao()` / `ruleActionDao()` or fold into `ForwardingRuleDao`.

### 1.3 DAO changes

`app/src/main/java/com/otpforwarder/data/local/ForwardingRuleDao.kt`:

- Replace `getMatchingRulesWithRecipients(otpType)` with `getEnabledRulesWithDetails(): List<RuleWithDetails>` — type matching now happens in-memory via the condition engine (OtpType is one condition among many, and conditions can be OR-combined so a SQL pre-filter is incorrect).
- Preserve ordering by `priority ASC` and insert/delete helpers for conditions + actions. Use `@Transaction` for writes so `deleteOldConditions → insert new ones` is atomic.

### 1.4 Mappers

`app/src/main/java/com/otpforwarder/data/mapper/ForwardingRuleMapper.kt` — rewrite:

- `RuleWithDetails.toDomain()` builds a `ForwardingRule` with ordered `conditions` + `actions` (loading forward-action recipient ids from `ActionRecipientCrossRef`).
- `ForwardingRule.toEntities()` returns `(ForwardingRuleEntity, List<RuleConditionEntity>, List<RuleActionEntity>, List<ActionRecipientCrossRef>)` — repository writes them in a transaction.

### 1.5 Repository

`data/repository/ForwardingRuleRepositoryImpl.kt` — transactional insert/update across rules + conditions + actions + xrefs. On update, delete old condition/action rows and reinsert (simpler than diffing; counts are tiny).

### Phase 1 verification

- `./gradlew assembleDebug` compiles.
- Room schema JSON updates cleanly; no existing callers of the old DAO left dangling (stub the new engine entry point for now if needed).

---

## Phase 2 — Rule engine rewrite

Goal: evaluate the new condition list and return each matching rule's full action list (not just recipients).

`app/src/main/java/com/otpforwarder/domain/engine/RuleEngine.kt` — rewrite `matchesFilters` as `evaluateConditions`:

1. Load all enabled rules via `getEnabledRulesWithDetails()`.
2. For each rule, evaluate conditions left-to-right:
   ```
   result = eval(conditions[0])
   for i in 1..conditions.lastIndex:
       next = eval(conditions[i])
       result = when (conditions[i].connector) { AND -> result && next; OR -> result || next }
   ```
3. Rules with zero conditions always match (user-visible warning in the UI, but don't block).
4. Return matching rules with their full action list (not just recipients anymore).

A single condition evaluator `fun RuleCondition.matches(otp: Otp): Boolean` handles the three sealed cases. Invalid regex silently evaluates to `false` (preserves current behavior, see `RuleEngine.matchesRegex` at `domain/engine/RuleEngine.kt:36`).

### Phase 2 verification

Unit tests in `app/src/test/java/.../RuleEngineTest.kt` (rewritten):
- Left-to-right evaluation with mixed connectors, e.g. `[A(AND)B(OR)C]` truth table.
- OtpType condition matching all `OtpType.entries` including `ALL`.
- Regex condition with invalid pattern → `false`, rule doesn't crash.
- Rule with zero conditions → always matches.

`./gradlew test` passes.

---

## Phase 3 — Action dispatch

Goal: replace the single `ForwardOtpUseCase` with three action-specific use cases and a dispatcher wired into the existing SMS processing pipeline.

### 3.1 New use cases under `domain/usecase/actions/`

- `ForwardSmsActionUseCase` — wraps existing `SmsSender`; iterates recipient ids, looks up `Recipient`, dedupes by `id`, calls `smsSender.send(...)`. This replaces today's `ForwardOtpUseCase` at `domain/usecase/ForwardOtpUseCase.kt:16`.
- `SetRingerLoudActionUseCase` — injected `AudioManager` + `NotificationManager`:
  - If `Build.VERSION.SDK_INT >= M` and `notificationManager.isNotificationPolicyAccessGranted`, call `setInterruptionFilter(INTERRUPTION_FILTER_ALL)`.
  - `audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL`.
  - `audioManager.setStreamVolume(STREAM_RING, getStreamMaxVolume(STREAM_RING), 0)`.
- `PlaceCallActionUseCase` — uses `TelecomManager.placeCall(Uri.parse("tel:$number"), null)`. Guards on `PackageManager.checkSelfPermission(CALL_PHONE) == PERMISSION_GRANTED`; if not granted, marks the action as failed and logs. This works without a UI/activity, which matters because SMS processing runs in a foreground service.

### 3.2 Dispatcher

`ExecuteRuleActionsUseCase` — takes `(otp, actions)`, runs each action in sequence on `Dispatchers.Default` (or `Main` where Android framework requires it — `AudioManager` is fine on any thread), collects per-action success/failure, returns a result list.

### 3.3 Hilt wiring

`di/` modules — provide `AudioManager`, `NotificationManager`, `TelecomManager` from `@ApplicationContext`.

### 3.4 Pipeline wire-up

Modify `ProcessIncomingSmsUseCase` at `domain/usecase/ProcessIncomingSmsUseCase.kt:30`:

- After `ruleEngine.evaluate(otp)` returns matching rules with actions, call `executeRuleActions(otp, rule.actions)` for each rule.
- Deduplication: forward-action recipient dedup moves into `ForwardSmsActionUseCase` across all matched rules (pass an `alreadySentTo: MutableSet<Long>` through the loop, same as the current `attempted` map).
- `OtpLogEntity` currently stores `ruleName`, `recipientNames`, `status` — extend `recipientNames` semantics to include a short per-action summary (e.g., `"Forwarded: Mom, Dad; Rang loudly; Called Dad"`). Keep the schema stable (no new columns) to avoid another migration.

### Phase 3 verification

- New unit test `ExecuteRuleActionsUseCaseTest.kt`:
  - Dispatcher runs every action.
  - Per-action failure isolation (e.g. `PlaceCall` fails without `CALL_PHONE` → forward still succeeds).
- `./gradlew assembleDebug` + `./gradlew test` pass.

---

## Phase 4 — Android integration & permissions

Goal: declare the new permissions, surface them in the onboarding / Settings UI, and ensure the foreground service has what it needs to execute actions silently.

### 4.1 Manifest

`app/src/main/AndroidManifest.xml` — add:

```xml
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />
```

(`ACCESS_NOTIFICATION_POLICY` is a special permission — granted via `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`, not at install.)

### 4.2 Service

`OtpProcessingService` at `service/OtpProcessingService.kt` — no structural change; it already runs actions via use cases, and the new dispatcher plugs into the existing pipeline. The service already holds a short foreground-service lifetime which is sufficient for the two new actions (audio manager is synchronous; `TelecomManager.placeCall` returns immediately and the call continues independent of the caller).

### 4.3 Permission helper

`util/PermissionHelper.kt` — extend:

- Add `hasCallPhone()`, `hasNotificationPolicyAccess()`.
- Expose a helper to launch `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` intent — invoked from Settings screen and from the rule editor when the user selects "loud mode" for the first time.

### 4.4 Settings screen

`ui/screen/settings/SettingsScreen.kt` — extend the Permissions section to show **Call phone** and **Do Not Disturb access** rows with the same pattern as existing rows; tapping a denied row opens the relevant system settings.

### Phase 4 verification

- Install on device, revoke `CALL_PHONE`, verify the Settings row shows denied and tap routes to system settings.
- Notification Policy row routes to the correct special-access screen.

---

## Phase 5 — UI redesign: Edit Rule screen

Goal: rewrite `EditRuleScreen` + `EditRuleViewModel` for the new model while keeping the route + nav args identical so `ui/navigation/NavGraph.kt` doesn't change.

### 5.1 Layout

```
┌──────────────────────────────┐
│ ← Add Rule                   │
├──────────────────────────────┤
│  Rule Name                   │
│  [ Bank OTPs               ] │
│                              │
│  If the message...           │   ← section header
│  ┌──────────────────────────┐│
│  │ Is a payment OTP      [x]││   ← condition chip/row, [x] removes
│  └──────────────────────────┘│
│         [ AND ▾ ]             │   ← connector button; tap toggles AND/OR
│  ┌──────────────────────────┐│
│  │ is from [ HDFCBK ]    [x]││   ← inline text field inside row
│  └──────────────────────────┘│
│         [ OR  ▾ ]             │
│  ┌──────────────────────────┐│
│  │ has [ credited ] in the  ││
│  │ body                  [x]││
│  └──────────────────────────┘│
│  [ + Add condition ]         │
│                              │
│  Then...                     │   ← section header
│  ┌──────────────────────────┐│
│  │ Forward the text to      ││
│  │   [✓] Mom                ││
│  │   [✓] Dad                ││
│  │   [ + Add recipient ]  [x]││
│  └──────────────────────────┘│
│  ┌──────────────────────────┐│
│  │ Set the phone to loud    ││
│  │ mode                   [x]││
│  └──────────────────────────┘│
│  ┌──────────────────────────┐│
│  │ Place a call to          ││
│  │ [ Dad          ▾ ]     [x]││
│  └──────────────────────────┘│
│  [ + Add action ]            │
│                              │
│        [ Save Rule ]         │
└──────────────────────────────┘
```

### 5.2 Condition row copy (plain English)

OtpType options in the "Add condition" menu:

- `TRANSACTION` → **"Is a payment OTP"**
- `LOGIN` → **"Is a login OTP"**
- `PARCEL_DELIVERY` → **"Is a parcel delivery OTP"**
- `REGISTRATION` → **"Is a registration OTP"**
- `PASSWORD_RESET` → **"Is a password reset OTP"**
- `GOVERNMENT` → **"Is a government OTP"**
- `ALL` → **"Is any OTP"**
- `UNKNOWN` → hidden from the picker (internal).

Sender / body conditions render with an inline `OutlinedTextField` so it's obvious the bracketed value is user-controlled:

- `is from [ _____ ]` with placeholder `"e.g. HDFCBK"` and helper text *"Matches the SMS sender. Supports regex."*
- `has [ _____ ] in the body` with placeholder `"e.g. credited"` and helper text *"Matches text anywhere in the SMS. Supports regex."*

Put a short helper label under the "If the message..." header: *"The message must match these conditions. Tap AND / OR between them to change how they combine."*

### 5.3 Action row copy

In the "Add action" menu:

- **"Forward the text to..."** → opens the existing recipient multi-select (reused component).
- **"Set the phone to loud mode"** → no config; on add, if `hasNotificationPolicyAccess() == false`, show an inline prompt with a **"Grant"** button that routes to Notification Policy settings (non-blocking — user can still save the rule without the grant; execution will fall back to ringer-only).
- **"Place a call to..."** → recipient dropdown (single-select from `Recipient` list, including an inline "+ Add recipient"). On add, if `hasCallPhone() == false`, show an inline prompt with a **"Grant"** button that requests the runtime permission via the existing permission launcher pattern.

### 5.4 ViewModel

Rewrite `EditRuleViewModel` to hold:

```kotlin
data class EditRuleUiState(
    val isEditing: Boolean,
    val name: String,
    val nameError: String?,
    val conditions: List<ConditionUi>,   // ordered
    val actions: List<ActionUi>,         // ordered
    val allRecipients: List<Recipient>,
    val priority: String,                 // kept for tie-break ordering
    val showLoudModePermissionHint: Boolean,
    val showCallPermissionHint: Boolean
)
```

`ConditionUi` / `ActionUi` are UI-local sealed types mirroring the domain sealed types plus transient fields (e.g., per-field error strings). The VM exposes `addCondition(type)`, `updateCondition(index, …)`, `toggleConnector(index)`, `removeCondition(index)`, and the action equivalents. `save()` validates: name non-blank, at least one condition, at least one action, at least one recipient selected on each forward action, non-null recipient on each call action, no blank regex patterns.

### 5.5 Rules list

`RulesScreen.kt` / `RulesViewModel.kt` card subtitle changes from `"TRANSACTION → Mom, Dad"` to a one-line summary:

> `If payment OTP AND from HDFCBK · Forwards to Mom, Dad · Rings loudly`

Truncate with ellipsis; full detail still visible on tap.

### Phase 5 verification

- Launch app, create a rule with 3 conditions mixed AND/OR and 3 actions via the new UI.
- Edit an existing (migrated) rule: verify it round-trips through the new editor without data loss.
- Rules list subtitle renders correctly for migrated and new rules.

---

## Phase 6 — Tests & end-to-end verification

Goal: lock in correctness with tests and a device smoke run before shipping.

### 6.1 Automated tests

- `RuleEngineTest.kt` (from Phase 2) — multi-condition AND/OR, empty conditions, invalid regex, all `OtpType` values.
- `ExecuteRuleActionsUseCaseTest.kt` (from Phase 3) — dispatcher fan-out + per-action failure isolation.
- **Migration_1_2 test**: construct an in-memory DB with v1 data (via `MigrationTestHelper` from `androidx.room:room-testing`), run migration, assert:
  - Rule row preserved (id, name, priority, enabled).
  - One condition row per populated filter, connector `AND`, correct order.
  - One `FORWARD_SMS` action row with the original recipients in `ActionRecipientCrossRef`.

Run with `./gradlew test` (and `./gradlew connectedDebugAndroidTest` for the Room migration test if it requires instrumentation).

### 6.2 Manual device smoke test

1. Create a rule with 3 conditions mixed AND/OR and 3 actions.
2. Send a test SMS matching the rule: `adb emu sms send TESTSND "Your OTP is 123456, payment of Rs.500 credited"`.
3. Verify:
   - Home screen log shows rule fired with the compact multi-action summary.
   - Forward SMS arrives on the target device.
   - Phone ringer is loud (set to silent beforehand).
   - Outbound call is placed to the configured recipient.
4. Toggle a connector from AND to OR and re-send a borderline SMS to verify evaluation shifts.

### 6.3 Permission flows

- Revoke `CALL_PHONE`: verify in-app hint shows on the rule editor, verify saved call action marks as failed (not crashing) at execution time.
- Revoke Notification Policy access: verify loud-mode action degrades to ringer-only.

### 6.4 Upgrade test

Install a v1 build with seeded rules + recipients, upgrade to the v2 build in place, verify old rules still fire with equivalent behavior (single condition from old `otpType`, optional sender/body conditions, single forward action with the same recipients).

---

## Reused components & helpers

- `SmsSender` (`domain/sms/SmsSender.kt`) — reused verbatim by `ForwardSmsActionUseCase`.
- Recipient repository, `InlineAddRecipientSheet` composable in `EditRuleScreen.kt:225` — reused for the forward/call recipient pickers.
- `NotificationHelper` in `util/NotificationHelper.kt` — reused; `notifyForwarded` gets a small label tweak to say "1 rule fired" regardless of action type, since non-SMS actions don't have a recipient list.
- The `inFlight` / `scope.launch` pattern in `OtpProcessingService.kt:50` — unchanged; the new dispatcher just runs more suspend work inside the existing scope.
- Existing `OtpLogEntity` — reused; `recipientNames` column now carries a compact multi-action summary.
- Permission prompt pattern in `ui/screen/onboarding/` — reused for the two new permissions.

---

## Critical files to modify

| File | Change | Phase |
| --- | --- | --- |
| `domain/model/ForwardingRule.kt` | Replace flat fields with `conditions` + `actions` sealed hierarchies | 1 |
| `domain/model/` (new) `RuleCondition.kt`, `RuleAction.kt`, `Connector.kt` | New sealed types + enum | 1 |
| `data/local/ForwardingRuleEntity.kt` | Drop otpType/senderFilter/bodyFilter columns | 1 |
| `data/local/` (new) `RuleConditionEntity.kt`, `RuleActionEntity.kt`, `ActionRecipientCrossRef.kt` | New tables | 1 |
| `data/local/RuleRecipientCrossRef.kt` | Delete (replaced by `ActionRecipientCrossRef.kt`) | 1 |
| `data/local/AppDatabase.kt` | Bump to v2, update entity list, register `Migration_1_2` via `DatabaseModule` | 1 |
| `data/local/ForwardingRuleDao.kt` | Replace SQL join query with `getEnabledRulesWithDetails()`; add condition + action CRUD | 1 |
| `data/mapper/ForwardingRuleMapper.kt` | Rewrite to map the new aggregate | 1 |
| `data/repository/ForwardingRuleRepositoryImpl.kt` | Transactional insert/update across rules + conditions + actions + xrefs | 1 |
| `domain/engine/RuleEngine.kt` | Replace `matchesFilters` with left-to-right condition evaluation | 2 |
| `domain/usecase/ForwardOtpUseCase.kt` | Move into `actions/ForwardSmsActionUseCase.kt` | 3 |
| `domain/usecase/actions/` (new) `SetRingerLoudActionUseCase.kt`, `PlaceCallActionUseCase.kt`, `ExecuteRuleActionsUseCase.kt` | Three action use cases + dispatcher | 3 |
| `domain/usecase/ProcessIncomingSmsUseCase.kt` | Call dispatcher instead of direct SMS forward; share dedup set | 3 |
| `di/` Hilt modules | Provide `AudioManager`, `NotificationManager`, `TelecomManager` from `@ApplicationContext` | 3 |
| `util/PermissionHelper.kt` | Add `CALL_PHONE` + Notification Policy helpers | 4 |
| `AndroidManifest.xml` | Add 3 new `<uses-permission>` entries | 4 |
| `ui/screen/settings/SettingsScreen.kt` / VM | Add two permission rows (CALL_PHONE, Notification Policy) | 4 |
| `ui/screen/rules/EditRuleScreen.kt` | Full rewrite matching new layout | 5 |
| `ui/screen/rules/EditRuleViewModel.kt` | Full rewrite with condition/action lists | 5 |
| `ui/screen/rules/RulesScreen.kt` | Update card subtitle generator | 5 |
| `app/src/test/java/.../RuleEngineTest.kt` | Rewrite: cover multi-condition AND/OR, empty conditions, regex failure | 2 / 6 |
| `app/src/test/java/.../ExecuteRuleActionsUseCaseTest.kt` (new) | Cover dispatcher fan-out + per-action failure isolation | 3 / 6 |
| `app/src/androidTest/.../MigrationTest.kt` (new) | Validate `Migration_1_2` with `MigrationTestHelper` | 6 |
