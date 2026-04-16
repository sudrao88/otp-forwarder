# OTP Forwarder - Android App Plan

## Context

Build a new Android app (new GitHub repo) that reads incoming SMS messages, detects OTPs, classifies them by type (transaction, login, parcel delivery, etc.), and forwards them via SMS to configured recipients based on a rule engine. The app is fully offline/local with on-device storage only.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **DI:** Hilt
- **Database:** Room
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
│   │   ├── classification/     # OtpClassifier interface + RegexOtpClassifier
│   │   ├── engine/             # RuleEngine
│   │   └── usecase/            # ProcessIncomingSms, ForwardOtp, ManageRules, GetHistory
│   ├── ui/
│   │   ├── navigation/         # NavGraph
│   │   ├── theme/              # Material 3 theme
│   │   ├── screen/             # dashboard/, rules/, recipients/, history/, settings/
│   │   └── component/          # Shared composables
│   └── util/                   # NotificationHelper, PermissionHelper
├── gradle/libs.versions.toml   # Version catalog
└── app/build.gradle.kts
```

## Data Model

### Domain Models

- **OtpType** enum: `TRANSACTION`, `LOGIN`, `PARCEL_DELIVERY`, `REGISTRATION`, `PASSWORD_RESET`, `GOVERNMENT`, `UNKNOWN`
- **Otp**: code, type, sender, originalMessage, detectedAt, confidence (0.0-1.0)
- **Recipient**: id, name, phoneNumber, isActive
- **ForwardingRule**: id, name, otpType (null = catch-all), recipientId, isEnabled, priority (lower = higher), senderFilter (optional regex)

### Room Entities

- `recipients` table
- `forwarding_rules` table (FK to recipients, indexed on recipientId and priority)
- `otp_log` table (denormalized with forwarding result info)

### Key Query

```sql
SELECT * FROM forwarding_rules 
WHERE isEnabled = 1 AND (otpType = :otpType OR otpType IS NULL)
ORDER BY CASE WHEN otpType IS NULL THEN 1 ELSE 0 END, priority ASC
```

## OTP Detection & Classification Strategy

### Detection (RegexOtpDetector)

Two-layer approach:
1. **Keyword pre-filter** (fast reject): check for words like `otp`, `code`, `verify`, `pin`, `one-time`, `passcode`
2. **Regex extraction** with confidence levels:
   - Explicit OTP patterns (confidence 0.95): `OTP is 123456`
   - Contextual patterns (confidence 0.75): `code is 123456`
   - Bare code with keyword context (confidence 0.50): fallback

### Classification (RegexOtpClassifier)

Keyword/regex matching on full message body:
- TRANSACTION: `transaction`, `payment`, `debit`, `credit`, `INR`, `bank`
- LOGIN: `log in`, `sign in`, `authenticate`, `2FA`
- PARCEL_DELIVERY: `deliver`, `shipment`, `parcel`, `tracking`, `courier`
- REGISTRATION: `register`, `sign up`, `create account`
- PASSWORD_RESET: `reset password`, `forgot`, `recover`
- GOVERNMENT: `Aadhaar`, `PAN`, `tax`, `govt`

### ML/Embedding Evaluation

**Verdict: Not for v1.** Architecture supports drop-in ML later via `OtpClassifier` interface. Ship regex, review `UNKNOWN` classifications from real usage, add ML in v2 if warranted.

## Rule Engine Design

- Rules are non-exclusive: OTP can match multiple rules → forwarded to multiple recipients
- Deduplication per-recipient
- Optional `senderFilter` regex on rules
- Priority as tiebreaker within same specificity level
- `otpType = null` means catch-all rule

## Processing Pipeline

```
SMS_RECEIVED broadcast
  → SmsReceiver (goAsync(), extract PDUs, start foreground service)
    → OtpProcessingService (foreground, short-lived ~5s)
      → ProcessIncomingSmsUseCase:
        1. OtpDetector.detect()
        2. OtpClassifier.classify()
        3. RuleEngine.evaluate()
        4. ForwardOtpUseCase → SmsManager.sendTextMessage()
        5. Log to Room DB
        6. Show notification
      → stopSelf()
  → (on failure) RetryWorker via WorkManager
```

## Implementation Phases

### Phase 1: Project Skeleton ← START HERE
- Create project, configure Gradle with version catalog, set up Hilt, Compose theme, navigation scaffold
- Git init, push to GitHub

### Phase 2: Data Layer
- Room entities, DAOs, database, repositories, mappers

### Phase 3: OTP Detection & Classification
- `OtpDetector` interface + `RegexOtpDetector`
- `OtpClassifier` interface + `RegexOtpClassifier`

### Phase 4: Rule Engine
- `RuleEngine.evaluate()`, `ForwardOtpUseCase`, `ProcessIncomingSmsUseCase`

### Phase 5: Android Integration
- Manifest permissions, `SmsReceiver`, `OtpProcessingService`
- `NotificationHelper`, `PermissionHelper`, `RetryWorker`

### Phase 6: Compose UI
- Dashboard, Recipients, Rules (CRUD), History, Settings
- Permission onboarding flow

### Phase 7: Polish & Release
- Error handling, ProGuard rules, GitHub Actions CI
- README, tag v1.0.0, release APK
