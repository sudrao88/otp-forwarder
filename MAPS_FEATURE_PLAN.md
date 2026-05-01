# Maps-Link Rule — Implementation Plan

## Goal
A rule shape: *"if an incoming SMS contains a Google Maps link, open it in Google Maps for navigation."* Composable with existing conditions (sender, body keyword, OTP type). Default UX is a high-priority notification with a one-tap "Navigate" action; an opt-in per-rule auto-launch flag uses a full-screen intent for hands-free start.

## Architectural prerequisite (bundled fix)
The current pipeline gates rule evaluation on OTP detection — `ProcessIncomingSmsUseCase.kt:38` short-circuits with `Result.NotOtp` whenever `OtpDetector.detect` returns null, so `ruleEngine.evaluate` (the only call site, line 42) never runs for non-OTP messages. This means existing actions like `SetRingerLoud` and `PlaceCall` silently fail on plain texts even when a sender/body rule would otherwise match. The Maps feature requires lifting this gate, and the same change fixes the latent bug for ringer/call.

## Decisions locked in
- **Rule shape**: composable. New condition `ContainsMapsLink` + new action `OpenMapsNavigation(autoLaunch)`.
- **Maps URLs detected**: `maps.google.com/...`, `(www.)?google.com/maps/...`, `goo.gl/maps/...`, `maps.app.goo.gl/...`. First match wins.
- **Action**: `Intent.ACTION_VIEW` on the URL with package preference `com.google.android.apps.maps`. No URL resolution / `google.navigation:` rewriting.
- **Default UX**: post a high-importance notification; tap fires the View intent. Safe under Android 10+ background-launch restrictions.
- **Auto-launch (opt-in per rule)**: full-screen-intent notification (incoming-call style). Requires `USE_FULL_SCREEN_INTENT` (granted by default <14, runtime opt-in 14+).
- **Provider scope**: Google Maps only for v1. No Waze, Apple Maps, generic geo handler.

---

## Phase 1 — Decouple rule engine from OTP detection
Make rules evaluate against every SMS. OTP detection still runs and enriches, but is no longer a precondition. Fixes the latent ringer/call bug as a side effect.

- Add `IncomingSms(sender, body, otp: Otp?, receivedAt)` domain type.
- `RuleCondition.matches(sms: IncomingSms)`:
  - `OtpTypeIs(ALL)` matches every SMS; specific OTP type matches only when `sms.otp?.type == this.type`.
  - `SenderMatches`, `BodyContains` read from `sms.sender` / `sms.body`.
- `RuleEngine.evaluate(sms: IncomingSms)`.
- `ProcessIncomingSmsUseCase`: run detector → `Otp?`, classify only if detected, build `IncomingSms`, evaluate rules. Drop the early `Result.NotOtp` gate. Empty match list → `NoMatchingRule`.
- `ExecuteRuleActionsUseCase` takes `IncomingSms`. `ForwardSmsAction` forwards the raw body when `otp == null`, code-formatted payload when present.
- `OtpLogEntry` / `OtpLogEntity`: make `code`, `otpType`, `confidence`, `classifierTier` nullable. Migration **v3 → v4** to relax NOT NULL constraints.
- `NotificationHelper.notifyForwarded`: handle null OTP cleanly.
- Tests: update existing rule-engine and use-case tests; add a test asserting `SetRingerLoud` fires on a non-OTP SMS that matches a `SenderMatches` rule.

**Commit**: `feat(rules): evaluate rules on every SMS, decouple from OTP detection`

## Phase 2 — Maps URL detector + `ContainsMapsLink` condition
Pure infrastructure. No new action yet — adding the condition alone lets a rule match on Maps SMS, even if its action is forward / ring / call.

- `domain/detection/MapsLinkDetector.kt` with `fun findMapsLink(body: String): String?`. Single regex covering the four host patterns above; case-insensitive.
- `RuleCondition.ContainsMapsLink(connector)`. `matches` calls `MapsLinkDetector.findMapsLink(sms.body) != null`.
- `RuleConditionEntity.TYPE_MAPS_LINK = "MAPS_LINK"`. Mapper updates (no new columns; `pattern` unused for this type).
- Unit tests: positive cases per host, negatives (apple maps, plain text, lookalike domains), URLs embedded in longer text.

**Commit**: `feat(rules): add ContainsMapsLink condition`

## Phase 3 — `OpenMapsNavigation` action (notification-tap path)
Add the action with notification-tap UX. `autoLaunch` field is stored but ignored this phase.

- `RuleAction.OpenMapsNavigation(autoLaunch: Boolean = false)`.
- `RuleActionEntity.TYPE_OPEN_MAPS = "OPEN_MAPS"`. Add column `mapsAutoLaunch INTEGER NOT NULL DEFAULT 0`. Migration **v4 → v5**.
- Mapper updates.
- `OpenMapsActionUseCase`: extract URL via `MapsLinkDetector.findMapsLink(sms.body)`. If null → `Status.SKIPPED` ("No Maps link found"). Otherwise post a high-importance notification ("Navigate to <sender>'s location") whose tap fires `Intent.ACTION_VIEW` with package `com.google.android.apps.maps`. Fallback chooser if Maps not installed.
- New notification channel (e.g. `maps_navigation`, IMPORTANCE_HIGH).
- Wire into `ExecuteRuleActionsUseCase` + DI binding (`ActionsModule`).
- Unit test for the action use-case (skipped/success branches via a fake notifier).

**Commit**: `feat(rules): add OpenMapsNavigation action with tap-to-navigate notification`

## Phase 4 — Auto-launch (full-screen intent)
- `OpenMapsActionUseCase`: when `autoLaunch=true`, attach a `fullScreenIntent` (PendingIntent for Maps) to the notification. Falls back to tap UX if the OS suppresses the FSI.
- Manifest: `<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />`.
- `PermissionHelper`: detect Android 14+ runtime requirement and expose state for the rule editor to surface a hint when auto-launch is enabled but permission is missing.
- Optional: separate notification channel with `setBypassDnd` consideration for the auto-launch path (driving use case).

**Commit**: `feat(rules): per-rule auto-launch for OpenMapsNavigation via full-screen intent`

## Phase 5 — UI (Edit Rule + Rules list)
- `EditRuleViewModel`:
  - `ActionKind` adds `OPEN_MAPS`.
  - `ActionUi` adds `OpenMaps(actionUid, autoLaunch)` with mapping helpers.
  - Same for `ContainsMapsLink` in the condition UI model.
- `EditRuleScreen`:
  - Condition picker: "Contains Google Maps link".
  - Action picker: "Open Google Maps navigation"; below it, an "Auto-launch (driving)" toggle row + permission hint when toggled on but FSI permission missing.
- `RulesViewModel.describeAction`: human-readable description for `OpenMapsNavigation` (with/without auto-launch).
- Soft validation: warn (not block) if the rule has `OpenMapsNavigation` without `ContainsMapsLink` — the action skips gracefully when no link is found, but the warning prevents user confusion.

**Commit**: `feat(ui): edit rule support for ContainsMapsLink condition and OpenMapsNavigation action`

## Phase 6 — Verification + edge cases
- `./gradlew test` clean pass.
- `./gradlew assembleDebug` + manual smoke on device:
  - SMS with each of the four URL host forms → notification appears, tap opens Maps.
  - SMS with no Maps link → no fire (rule with only `OpenMaps` skipped; ringer rule still fires from Phase 1 changes).
  - Auto-launch ON, screen off + unlocked, FSI permission granted → Maps launches.
  - Auto-launch ON, FSI permission denied (Android 14+) → falls back to tap notification with a banner hint.
  - Maps app uninstalled → chooser shows browser; tap still works.
- Update `PLAN.md` if any user-facing screen text changed.

**Commit**: `chore(rules): verify maps-link feature end-to-end`

---

## Out of scope (not in this branch)
- Apple Maps / Waze / generic `geo:` URI handling.
- Resolving short links server-side to extract coordinates for `google.navigation:` deep-link.
- Multiple Maps links per message (we always take the first).
- Maps links in MMS / RCS (the app is SMS-only).
- A general "non-OTP message log" view — Phase 1 keeps non-OTP rule firings in the existing log table with nullable OTP fields, but no new UI surface for them.
