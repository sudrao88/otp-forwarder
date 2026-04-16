# OTP Forwarder - Android App Plan

## Context

Build a new Android app (new GitHub repo) that reads incoming SMS messages, detects OTPs, classifies them by type (transaction, login, parcel delivery, etc.), and forwards them via SMS to configured recipients based on a rule engine. The app is fully offline/local with on-device storage only.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **DI:** Hilt
- **Database:** Room
- **On-device AI:** Google AI Edge SDK (Gemini Nano, available on supported devices)
- **Background:** Foreground Service (for immediate SMS processing) + WorkManager (retry failed sends)
- **Architecture:** Single-module, MVVM with clean domain layer
- **Min SDK:** 26 (Android 8.0), Target SDK: 35

## Project Structure

```
otp-forwarder/
├── .github/workflows/          # CI + release APK builds
├── app/src/main/java/com/otpforwarder/
│   ├── OtpForwarderApp.kt     # Application class (Hilt init)
│   ├── MainActivity.kt
│   ├── di/                     # Hilt modules
│   ├── receiver/
│   │   └── SmsReceiver.kt     # BroadcastReceiver entry point
│   ├── service/
│   │   └── OtpProcessingService.kt  # Short-lived foreground service
│   ├── data/
│   │   ├── local/              # Room DB, DAOs, Entities
│   │   ├── repository/         # Repository implementations
│   │   └── mapper/             # Entity <-> Domain mappers
│   ├── domain/
│   │   ├── model/              # Otp, OtpType, ForwardingRule, Recipient
│   │   ├── detection/          # OtpDetector interface + RegexOtpDetector
│   │   ├── classification/     # OtpClassifier interface + implementations
│   │   │   ├── OtpClassifier.kt           # Interface
│   │   │   ├── GeminiOtpClassifier.kt     # Gemini Nano (AI Edge SDK)
│   │   │   ├── KeywordOtpClassifier.kt    # Weighted keyword scoring
│   │   │   └── TieredOtpClassifier.kt     # Orchestrator: Gemini → Keyword fallback
│   │   ├── engine/             # RuleEngine
│   │   └── usecase/            # ProcessIncomingSms, ForwardOtp, ManageRules
│   ├── ui/
│   │   ├── navigation/         # NavGraph
│   │   ├── theme/              # Material 3 theme
│   │   ├── screen/             # home/, rules/, recipients/, settings/
│   │   └── component/          # Shared composables
│   └── util/                   # NotificationHelper, PermissionHelper
├── gradle/libs.versions.toml   # Version catalog
└── app/build.gradle.kts
```

## Data Model

### Domain Models

- **OtpType** enum: `ALL`, `TRANSACTION`, `LOGIN`, `PARCEL_DELIVERY`, `REGISTRATION`, `PASSWORD_RESET`, `GOVERNMENT`, `UNKNOWN`
- **ClassifierTier** enum: `GEMINI_NANO`, `KEYWORD`
- **Otp**: code, type, sender, originalMessage, detectedAt, confidence (0.0-1.0), classifierTier
- **Recipient**: id, name, phoneNumber, isActive
- **ForwardingRule**: id, name, otpType (`ALL` = match any type), isEnabled, priority (lower = higher), senderFilter (optional regex on sender), bodyFilter (optional regex on message body)

### Room Entities

- `recipients` table
- `forwarding_rules` table (indexed on priority)
- `rule_recipient_cross_ref` table (ruleId FK, recipientId FK — many-to-many join table)
- `otp_log` table (denormalized with forwarding result info, auto-pruned beyond 12 hours)

### Key Query

```sql
SELECT r.*, GROUP_CONCAT(rec.name) as recipientNames
FROM forwarding_rules r
JOIN rule_recipient_cross_ref xref ON r.id = xref.ruleId
JOIN recipients rec ON xref.recipientId = rec.id
WHERE r.isEnabled = 1 
  AND rec.isActive = 1
  AND (r.otpType = 'ALL' OR r.otpType = :otpType)
GROUP BY r.id
ORDER BY CASE WHEN r.otpType = 'ALL' THEN 1 ELSE 0 END, r.priority ASC
```

## OTP Detection & Classification Strategy

### Detection (RegexOtpDetector)

Two-layer approach:
1. **Keyword pre-filter** (fast reject): check for words like `otp`, `code`, `verify`, `pin`, `one-time`, `passcode`
2. **Regex extraction** with confidence levels:
   - Explicit OTP patterns (confidence 0.95): `OTP is 123456`
   - Contextual patterns (confidence 0.75): `code is 123456`
   - Bare code with keyword context (confidence 0.50): fallback

### Classification (Two-Tier)

The `OtpClassifier` interface has two implementations, orchestrated by `TieredOtpClassifier`:

#### Tier 1: Gemini Nano (`GeminiOtpClassifier`)

- Uses **Google AI Edge SDK** with the on-device `GenerativeModel` (AICore backend)
- Available on **Android 14+ (SDK 34+)** on supported devices (Pixel 8+, Samsung S24+, etc.)
- Prompt-based classification — sends the SMS body with a structured prompt:
  ```
  Classify this SMS into exactly one category:
  TRANSACTION, LOGIN, PARCEL_DELIVERY, REGISTRATION,
  PASSWORD_RESET, GOVERNMENT, or UNKNOWN.
  Reply with only the category name.

  SMS: "{message body}"
  ```
- Check availability at runtime via `GenerativeModel.isAvailable()`
- Latency: ~200-500ms, acceptable since processing runs in a foreground service

#### Tier 2: Weighted Keyword Scoring (`KeywordOtpClassifier`)

Fallback for all devices where Gemini Nano is unavailable. Uses **weighted multi-keyword scoring** instead of naive first-match:

Each category has weighted keywords (higher weight = stronger signal):
- TRANSACTION: `transaction` (0.9), `payment` (0.8), `debit` (0.9), `credit` (0.7), `credited` (0.9), `debited` (0.9), `INR` (0.8), `Rs.` (0.8), `bank` (0.4), `account` (0.3), `a/c` (0.7), `UPI` (0.8), `NEFT` (0.7), `IMPS` (0.7)
- LOGIN: `log in` (0.9), `sign in` (0.9), `authenticate` (0.8), `2FA` (0.9), `two-factor` (0.9), `verification code` (0.6), `login` (0.8)
- PARCEL_DELIVERY: `deliver` (0.8), `delivered` (0.9), `shipment` (0.9), `parcel` (0.9), `tracking` (0.8), `courier` (0.9), `dispatch` (0.7), `out for delivery` (1.0), `AWB` (0.9)
- REGISTRATION: `register` (0.8), `sign up` (0.9), `signup` (0.9), `create account` (0.9), `new account` (0.8), `welcome` (0.4)
- PASSWORD_RESET: `reset password` (1.0), `reset your password` (1.0), `forgot` (0.7), `recover` (0.7), `change password` (0.9)
- GOVERNMENT: `Aadhaar` (1.0), `PAN` (0.8), `tax` (0.6), `govt` (0.7), `DigiLocker` (0.9), `UMANG` (0.9), `e-filing` (0.9), `ITR` (0.9)

**Scoring logic:**
1. Scan message body (case-insensitive) against all categories
2. Sum matched keyword weights per category
3. Highest scoring category wins, with a **minimum threshold of 0.5** — below it, classify as UNKNOWN
4. On tie, prefer the more specific category (fewer total keywords matched = more specific)

**Sender reputation boost:** Known sender ID prefixes add +0.5 to their category:
- `HDFCBK`, `SBIBNK`, `ICICIB`, `AXISBK`, `KOTAKB` → TRANSACTION
- `AMZNIN`, `FKRTIN`, `SWIGGY`, `ZOMATO` → PARCEL_DELIVERY
- `IKITWT`, `GLOGIN`, `MSFTNO` → LOGIN
- `GOVTIN`, `AABORL`, `ITDEPT` → GOVERNMENT
- (Expandable — users can add custom sender mappings in a future version)

#### Tier Orchestration (`TieredOtpClassifier`)

```
classify(sender, body) →
  if GeminiOtpClassifier.isAvailable() →
    result = GeminiOtpClassifier.classify(body)
    if result parse succeeds → return result
    else → fall through
  return KeywordOtpClassifier.classify(sender, body)
```

Gemini failures (timeout, garbled output, unavailable) silently fall back to keyword scoring. The selected tier is logged in `OtpLogEntity` for observability.

## Rule Engine Design

- Rules are non-exclusive: OTP can match multiple rules → forwarded to multiple recipients
- Each rule maps to multiple recipients (many-to-many)
- Deduplication per-recipient across all matched rules
- Optional `senderFilter` regex on SMS sender address
- Optional `bodyFilter` regex on SMS message body
- Both filters must match if both are set (AND logic)
- Priority as tiebreaker within same specificity level
- `otpType = ALL` means match any OTP type

## Processing Pipeline

```
SMS_RECEIVED broadcast
  → SmsReceiver (goAsync(), extract PDUs, start foreground service)
    → OtpProcessingService (foreground, short-lived ~5s)
      → ProcessIncomingSmsUseCase:
        1. OtpDetector.detect()
        2. TieredOtpClassifier.classify() → (OtpType, ClassifierTier)
        3. RuleEngine.evaluate()
        4. ForwardOtpUseCase → SmsManager.sendTextMessage()
        5. Log to Room DB (including classifierTier)
        6. Show notification
      → stopSelf()
  → (on failure) RetryWorker via WorkManager
```

## Screen Layouts

### Navigation: Bottom Nav Bar (4 tabs)

```
[ Home ]  [ Rules ]  [ Recipients ]  [ Settings ]
```

---

### Home Screen

Shows OTPs forwarded in the last 12 hours. Primary surface of the app.

```
┌──────────────────────────────┐
│  OTP Forwarder         [ON]  │  ← Top bar with master enable/disable toggle
├──────────────────────────────┤
│                              │
│  Today                       │  ← Section header (relative date)
│  ┌──────────────────────────┐│
│  │ ● HDFC Bank        2m ago││  ← Sender + relative time
│  │   Code: 482910           ││  ← Extracted OTP code
│  │   LOGIN → Mom, Dad       ││  ← OTP type → forwarded recipients
│  │   ✓ Sent                 ││  ← Status (Sent / Failed / Retrying)
│  ├──────────────────────────┤│
│  │ ● Amazon            1h ago│
│  │   Code: 7731             ││
│  │   PARCEL → Mom           ││
│  │   ✓ Sent                 ││
│  └──────────────────────────┘│
│                              │
│  Yesterday                   │  ← Only if within 12h window
│  ┌──────────────────────────┐│
│  │ ● SBI             11h ago││
│  │   Code: 339201           ││
│  │   TRANSACTION → Dad      ││
│  │   ✗ Failed               ││  ← Tap to retry
│  └──────────────────────────┘│
│                              │
│  ─ ─ ─ empty state ─ ─ ─    │  ← When no OTPs in last 12h:
│  "No OTPs forwarded in the   │    illustration + message
│   last 12 hours"             │
│                              │
├──────────────────────────────┤
│ [ Home ]  [Rules] [Recip] [⚙]│
└──────────────────────────────┘
```

---

### Rules Screen

List of forwarding rules with FAB to add new ones. Tap a rule to edit.

```
┌──────────────────────────────┐
│  Forwarding Rules            │
├──────────────────────────────┤
│                              │
│  ┌──────────────────────────┐│
│  │ Bank OTPs           [ON] ││  ← Rule name + enable toggle
│  │ TRANSACTION → Mom, Dad   ││  ← OTP type → recipients
│  │ Priority: 1              ││
│  ├──────────────────────────┤│
│  │ All Login OTPs      [ON] ││
│  │ LOGIN → Dad              ││
│  │ Priority: 2              ││
│  ├──────────────────────────┤│
│  │ Catch-all          [OFF] ││
│  │ ALL → Mom                ││
│  │ Priority: 10             ││
│  └──────────────────────────┘│
│                              │
│                         [+]  │  ← FAB to add rule
├──────────────────────────────┤
│ [Home]  [ Rules ] [Recip] [⚙]│
└──────────────────────────────┘
```

**Add/Edit Rule (bottom sheet or new screen):**

```
┌──────────────────────────────┐
│  ← Add Rule                  │
├──────────────────────────────┤
│                              │
│  Rule Name                   │
│  [ Bank OTPs               ]│
│                              │
│  OTP Type                    │
│  [ TRANSACTION          ▼  ]│  ← Dropdown: ALL, TRANSACTION, LOGIN, etc.
│                              │
│  Recipients                  │
│  [✓] Mom                     │  ← Multi-select checklist
│  [✓] Dad                     │
│  [ ] Sister                  │
│  [ + Add new recipient ]     │  ← Opens add recipient bottom sheet inline
│                              │
│  Priority                    │
│  [ 1                       ]│
│                              │
│  Filters (optional)          │
│  Sender regex                │
│  [ HDFCBK.*              ]│  ← Match on SMS sender
│  Body regex                  │
│  [ .*credited.*           ]│  ← Match on SMS body
│                              │
│         [ Save Rule ]        │
└──────────────────────────────┘
```

---

### Recipients Screen

Tap a recipient to edit. Shows which rules they belong to.

```
┌──────────────────────────────┐
│  Recipients                  │
├──────────────────────────────┤
│                              │
│  ┌──────────────────────────┐│
│  │ Mom                 [ON] ││  ← Name + active toggle
│  │ +91 98765 43210          ││  ← Phone number
│  │ Rules: Bank OTPs, Catch… ││  ← Associated rules summary
│  ├──────────────────────────┤│
│  │ Dad                 [ON] ││
│  │ +91 98765 43211          ││
│  │ Rules: Bank OTPs, Login… ││
│  └──────────────────────────┘│
│                              │
│                         [+]  │  ← FAB to add recipient
├──────────────────────────────┤
│ [Home]  [Rules]  [ Recip ] [⚙]│
└──────────────────────────────┘
```

**Add/Edit Recipient (bottom sheet or new screen):**

```
┌──────────────────────────────┐
│  ← Edit Recipient            │
├──────────────────────────────┤
│  Name                        │
│  [ Mom                     ]│
│                              │
│  Phone Number                │
│  [ +91 98765 43210        ]│
│                              │
│  Assigned Rules              │
│  [✓] Bank OTPs              │  ← Multi-select checklist
│  [ ] Login OTPs             │
│  [✓] Catch-all              │
│  [ + Add new rule ]          │  ← Navigates to Add Rule screen
│                              │    with this recipient pre-checked
│       [ Save Recipient ]     │
└──────────────────────────────┘
```

---

### Settings Screen

```
┌──────────────────────────────┐
│  Settings                    │
├──────────────────────────────┤
│                              │
│  Permissions                 │
│  ┌──────────────────────────┐│
│  │ Receive SMS     ✓ Granted││
│  │ Send SMS        ✓ Granted││
│  │ Notifications   ✗ Denied ││  ← Tap to open system settings
│  └──────────────────────────┘│
│                              │
│  Classification              │
│  ┌──────────────────────────┐│
│  │ Active: Gemini Nano      ││  ← or "Keyword Scoring"
│  │ Gemini Nano: Available   ││  ← or "Unavailable (device
│  │                          ││     not supported)"
│  └──────────────────────────┘│
│                              │
│  Forwarding                  │
│  ┌──────────────────────────┐│
│  │ Include original message ││
│  │ in forwarded SMS    [ON] ││
│  └──────────────────────────┘│
│                              │
│  About                       │
│  ┌──────────────────────────┐│
│  │ Version 1.0.0            ││
│  │ View on GitHub →         ││
│  └──────────────────────────┘│
│                              │
├──────────────────────────────┤
│ [Home]  [Rules] [Recip]  [⚙] │
└──────────────────────────────┘
```

---

### First Launch / Permission Onboarding

Shown once before the app is usable.

```
┌──────────────────────────────┐
│                              │
│       📱 OTP Forwarder       │
│                              │
│  This app needs permission   │
│  to read and send SMS to     │
│  forward your OTPs.          │
│                              │
│  ○ Receive SMS               │
│  ○ Send SMS                  │
│  ○ Notifications             │
│                              │
│    [ Grant Permissions ]     │
│                              │
└──────────────────────────────┘
```

## Implementation Phases

Each phase should be run in a **new conversation** (`/clear` or new terminal). Copy the prompt below into Claude Code.

---

### Phase 1: Project Skeleton ← START HERE

**What to build:**
- Android project with Gradle Kotlin DSL and version catalog (`gradle/libs.versions.toml`)
- All dependencies: Compose BOM, Material 3, Hilt, Room, Navigation Compose, WorkManager
- `OtpForwarderApp.kt` application class with `@HiltAndroidApp`
- `MainActivity.kt` with `@AndroidEntryPoint`, sets Compose content
- Material 3 theme (`ui/theme/`)
- Bottom navigation scaffold with 4 tabs: Home, Rules, Recipients, Settings
- Placeholder composable screen for each tab
- `AndroidManifest.xml` with application and activity declaration
- Verify `./gradlew assembleDebug` passes

**Prompt:**
```
Read PLAN.md and CLAUDE.md. Implement Phase 1: Project Skeleton.

Create the full Android project structure from scratch. Set up Gradle with Kotlin DSL and a version catalog (gradle/libs.versions.toml) for all dependencies: Compose BOM, Material 3, Hilt, Room, Navigation Compose, WorkManager. Create the Application class with @HiltAndroidApp, MainActivity with @AndroidEntryPoint, Material 3 theme, and a bottom navigation scaffold with 4 tabs (Home, Rules, Recipients, Settings) each with a placeholder composable. Set up AndroidManifest.xml.

Run ./gradlew assembleDebug after each major file to catch errors early. Fix any errors before moving on. Commit when done.
```

---

### Phase 2: Data Layer

**What to build:**
- Domain models: `OtpType` enum (with `ALL`), `Otp`, `Recipient`, `ForwardingRule`
- Room entities: `RecipientEntity`, `ForwardingRuleEntity`, `RuleRecipientCrossRef`, `OtpLogEntity`
- Room DAOs: `RecipientDao`, `ForwardingRuleDao`, `OtpLogDao` with the key query from the plan
- `AppDatabase` with all entities and type converters
- Repository interfaces in `domain/` and implementations in `data/repository/`
- Entity ↔ domain mappers in `data/mapper/`
- Hilt module to provide database and DAOs

**Prompt:**
```
Read PLAN.md and CLAUDE.md. Implement Phase 2: Data Layer.

Create domain models: OtpType enum (ALL, TRANSACTION, LOGIN, PARCEL_DELIVERY, REGISTRATION, PASSWORD_RESET, GOVERNMENT, UNKNOWN), Otp, Recipient, ForwardingRule (with senderFilter and bodyFilter). Create Room entities: RecipientEntity, ForwardingRuleEntity, RuleRecipientCrossRef (many-to-many join table), OtpLogEntity (auto-pruned beyond 12 hours). Create DAOs with the key query from PLAN.md. Create AppDatabase, repository interfaces and implementations, entity-to-domain mappers, and a Hilt module for DI.

Run ./gradlew assembleDebug after each major file. Fix errors before moving on. Commit when done.
```

---

### Phase 3: OTP Detection & Classification

**What to build:**
- `OtpDetector` interface with `detect(sender: String, body: String): Otp?`
- `RegexOtpDetector` implementation with two-layer approach: keyword pre-filter + regex extraction with confidence levels (0.95, 0.75, 0.50)
- `ClassifierTier` enum: `GEMINI_NANO`, `KEYWORD`
- `OtpClassifier` interface with `suspend fun classify(sender: String, body: String): Pair<OtpType, ClassifierTier>`
- `GeminiOtpClassifier`: uses Google AI Edge SDK `GenerativeModel` with structured prompt, checks availability at runtime
- `KeywordOtpClassifier`: weighted multi-keyword scoring with sender reputation boost (see keyword weights and sender mappings in plan)
- `TieredOtpClassifier`: orchestrator that tries Gemini first, falls back to keyword scoring on failure/unavailability
- Add `com.google.ai.edge:generative-ai` dependency to version catalog
- Unit tests for detection (confidence levels, edge cases) and keyword classifier (each OTP type, scoring, sender boost, ties, threshold)

**Prompt:**
```
Read PLAN.md and CLAUDE.md. Implement Phase 3: OTP Detection & Classification.

Create OtpDetector interface and RegexOtpDetector with two-layer detection: keyword pre-filter (otp, code, verify, pin, one-time, passcode) then regex extraction with confidence levels (0.95 for explicit "OTP is 123456", 0.75 for "code is 123456", 0.50 for bare code with keyword context).

Create the two-tier classification system:
1. OtpClassifier interface with `suspend fun classify(sender: String, body: String): Pair<OtpType, ClassifierTier>`
2. GeminiOtpClassifier — uses Google AI Edge SDK GenerativeModel to prompt Gemini Nano for classification. Check isAvailable() at runtime. Parse the response to an OtpType, falling through on any failure.
3. KeywordOtpClassifier — weighted multi-keyword scoring per the plan: each keyword has a weight, scores are summed per category, highest wins above 0.5 threshold. Add sender reputation boost (+0.5) for known sender ID prefixes.
4. TieredOtpClassifier — tries Gemini first, silent fallback to keyword on failure/unavailability. Returns the tier used alongside the classification.

Add ClassifierTier enum (GEMINI_NANO, KEYWORD) to domain/model/. Add the classifierTier field to the Otp model. Add com.google.ai.edge:generative-ai to the version catalog.

Write unit tests for RegexOtpDetector and KeywordOtpClassifier. Cover each OTP type, scoring logic, sender reputation boost, tie-breaking, minimum threshold, and edge cases.

Run ./gradlew assembleDebug and ./gradlew test after changes. Fix errors before moving on. Commit when done.
```

---

### Phase 4: Rule Engine

**What to build:**
- `RuleEngine` class: takes an `Otp`, queries matching rules, returns list of `(ForwardingRule, List<Recipient>)` pairs
- Matching logic: otpType match (or ALL), senderFilter regex, bodyFilter regex, AND logic for both filters
- Deduplication: same recipient across multiple rules only gets one forward
- Priority ordering: specific type rules before ALL, then by priority number
- `ForwardOtpUseCase`: takes Otp + recipient, sends SMS via `SmsManager.sendTextMessage()`
- `ProcessIncomingSmsUseCase`: orchestrates detect → classify (via TieredOtpClassifier) → evaluate rules → forward → log to DB (including classifierTier)
- Unit tests for RuleEngine: test type matching, filter matching, deduplication, priority ordering

**Prompt:**
```
Read PLAN.md and CLAUDE.md. Implement Phase 4: Rule Engine.

Create RuleEngine that evaluates an Otp against forwarding rules: match on otpType (or ALL), apply senderFilter and bodyFilter regex (AND logic if both set), deduplicate recipients across matched rules, order by specificity then priority. Create ForwardOtpUseCase (sends SMS via SmsManager) and ProcessIncomingSmsUseCase (orchestrates: detect → classify → evaluate rules → forward → log to Room DB). Write unit tests for RuleEngine covering type matching, filter matching, deduplication, and priority ordering.

Run ./gradlew assembleDebug and ./gradlew test after changes. Fix errors before moving on. Commit when done.
```

---

### Phase 5: Android Integration

**What to build:**
- `AndroidManifest.xml` permissions: `RECEIVE_SMS`, `SEND_SMS`, `POST_NOTIFICATIONS`
- `SmsReceiver` BroadcastReceiver: registered for `SMS_RECEIVED`, uses `goAsync()`, extracts PDUs, starts foreground service
- `OtpProcessingService` foreground service: short-lived (~5s), calls `ProcessIncomingSmsUseCase`, then `stopSelf()`
- `NotificationHelper`: creates notification channel, shows forwarding result notifications
- `PermissionHelper`: checks and requests runtime permissions
- `RetryWorker` (WorkManager): retries failed SMS forwards with exponential backoff
- Register receiver, service, and worker in manifest

**Prompt:**
```
Read PLAN.md and CLAUDE.md. Implement Phase 5: Android Integration.

Add manifest permissions (RECEIVE_SMS, SEND_SMS, POST_NOTIFICATIONS). Create SmsReceiver BroadcastReceiver that listens for SMS_RECEIVED, uses goAsync(), extracts PDUs, and starts the foreground service. Create OtpProcessingService as a short-lived foreground service that calls ProcessIncomingSmsUseCase then stopSelf(). Create NotificationHelper (channel setup + forwarding result notifications), PermissionHelper (runtime permission checks/requests), and RetryWorker (WorkManager with exponential backoff for failed forwards). Register everything in AndroidManifest.xml.

Run ./gradlew assembleDebug after changes. Fix errors before moving on. Commit when done.
```

---

### Phase 6: Compose UI

**What to build:**
- **Home screen**: LazyColumn showing OTPs forwarded in last 12h, grouped by relative date (Today/Yesterday). Each card shows sender, relative time, OTP code, type, forwarded recipients, status (Sent/Failed/Retrying). Tap failed to retry. Master enable/disable toggle in top bar. Empty state when no OTPs.
- **Rules screen**: LazyColumn of rule cards showing name, enable toggle, OTP type, assigned recipient names, priority. FAB to add. Tap to edit.
- **Add/Edit Rule screen**: Form with rule name, OTP type dropdown (ALL + all types), multi-select recipient checklist, `[ + Add new recipient ]` button (opens inline bottom sheet to create recipient), priority field, sender regex field, body regex field. Save button.
- **Recipients screen**: LazyColumn of recipient cards showing name, active toggle, phone number, associated rule names. FAB to add. Tap to edit.
- **Add/Edit Recipient screen**: Form with name, phone number, multi-select rule checklist, `[ + Add new rule ]` button (navigates to Add Rule screen with this recipient pre-checked). Save button.
- **Settings screen**: Permission status list (tap denied to open system settings), classification status section (shows active tier — "Gemini Nano" or "Keyword Scoring" — and Gemini Nano availability), "Include original message" toggle, app version, GitHub link.
- **Permission onboarding screen**: Shown on first launch when permissions not granted. Lists required permissions with Grant button.
- ViewModels for each screen with Hilt `@HiltViewModel`

**Prompt:**
```
Read PLAN.md and CLAUDE.md. Implement Phase 6: Compose UI. Refer to the Screen Layouts section in PLAN.md for exact layout specs.

Build all screens:
1. Home: LazyColumn of OTPs from last 12h, grouped by date, each card shows sender/time/code/type/recipients/status, tap failed to retry, master on/off toggle in top bar, empty state.
2. Rules list: cards with name/toggle/type/recipients/priority, FAB to add, tap to edit.
3. Add/Edit Rule: name, OTP type dropdown, multi-select recipient checklist with "Add new recipient" (inline bottom sheet), priority, sender regex, body regex, save button.
4. Recipients list: cards with name/toggle/phone/rules, FAB to add, tap to edit.
5. Add/Edit Recipient: name, phone, multi-select rule checklist with "Add new rule" (navigates to Add Rule with recipient pre-checked), save button.
6. Settings: permission status, classification status (active tier + Gemini Nano availability), include original message toggle, about section.
7. Permission onboarding: first launch flow.

Create @HiltViewModel for each screen. Run ./gradlew assembleDebug after each screen. Fix errors before moving on. Commit when done.
```

---

### Phase 7: Polish & Release

**What to build:**
- Error handling: try/catch around SMS send, graceful handling of missing permissions, invalid regex in filters
- ProGuard/R8 rules for Room, Hilt, Kotlin serialization
- GitHub Actions CI workflow: build on push/PR, run tests, upload debug APK as artifact
- README.md with app description, screenshots placeholder, build instructions, permissions explanation
- Tag `v1.0.0`, build release APK

**Prompt:**
```
Read PLAN.md and CLAUDE.md. Implement Phase 7: Polish & Release.

Add error handling: try/catch around SMS sends, graceful handling of missing permissions, invalid regex in rule filters. Add ProGuard/R8 rules for Room, Hilt, and Kotlin serialization. Create .github/workflows/ci.yml (build + test on push/PR, upload debug APK artifact). Create README.md with app description, feature list, build instructions, and permissions explanation. Verify everything builds cleanly with ./gradlew assembleDebug and ./gradlew test.

Commit when done.
```
