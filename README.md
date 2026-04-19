# OTP Forwarder

An offline Android app that reads incoming SMS messages, detects one-time
passcodes, classifies them by type (transaction, login, parcel delivery,
etc.), and forwards them by SMS to the recipients you configure — all on
device, no cloud.

## Features

- **On-device OTP detection** via a two-layer regex pipeline with confidence
  scoring.
- **Two-tier classification**: Gemini Nano (Google AI Edge SDK) where
  available, with a weighted keyword-scoring fallback that works on every
  supported device.
- **Rule engine** with optional sender / body regex filters, multi-recipient
  fan-out, per-recipient deduplication, priority ordering, and an `ALL`
  catch-all type.
- **Home dashboard** showing OTPs forwarded in the last 12 hours, with status
  (sent / failed / retrying) and tap-to-retry for failures.
- **Background reliability**: foreground service handles the immediate send,
  WorkManager retries failures with exponential backoff.
- **Fully local storage** (Room). No network calls, no analytics.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Hilt for DI, Room for storage, Navigation Compose
- WorkManager for retry, Google AI Edge SDK for on-device Gemini Nano
- Min SDK 26 (Android 8.0), Target SDK 35

## Project layout

```
app/src/main/java/com/otpforwarder/
├── data/            # Room, repositories, mappers, SMS sender, settings
├── di/              # Hilt modules
├── domain/          # Models, detection, classification, rule engine, use cases
├── receiver/        # SMS broadcast receiver (pipeline entry point)
├── service/         # Foreground OTP processing service
├── ui/              # Compose screens, ViewModels, navigation, theme
├── util/            # Notification + permission helpers
└── worker/          # WorkManager retry worker
```

The full design is documented in [`PLAN.md`](PLAN.md).

## Build

Requires JDK 17 and the Android SDK (Android Studio Ladybug or newer
recommended).

```bash
# Debug build
./gradlew assembleDebug

# Unit tests
./gradlew test

# Release build (minified + R8)
./gradlew assembleRelease
```

CI on GitHub Actions runs `./gradlew test`, `./gradlew lintDebug`,
`./gradlew assembleDebug`, and `./gradlew assembleRelease` on every push and
pull request, and uploads the debug + release APKs as artifacts.

## Permissions

Requested at onboarding (runtime, install-grouped):

| Permission          | Why it is needed                                       |
| ------------------- | ------------------------------------------------------ |
| `RECEIVE_SMS`       | Observe incoming SMS messages to detect OTPs           |
| `SEND_SMS`          | Forward the extracted OTP to configured recipients     |
| `POST_NOTIFICATIONS`| Surface "Sent / Failed / Retrying" status (Android 13+) |

Requested per-rule or on demand (only when the relevant action is configured):

| Permission                     | Why it is needed                                                    |
| ------------------------------ | ------------------------------------------------------------------- |
| `CALL_PHONE`                   | Place outbound calls for rules that use the "Place call" action     |
| `MODIFY_AUDIO_SETTINGS`        | Raise ringer volume for rules that use the "Set ringer loud" action |
| `ACCESS_NOTIFICATION_POLICY`   | Bypass Do Not Disturb when the "Set ringer loud" action fires       |

Declared but non-prompting:

| Permission                            | Why it is needed                                        |
| ------------------------------------- | ------------------------------------------------------- |
| `FOREGROUND_SERVICE`                  | Run the short-lived OTP processing service              |
| `FOREGROUND_SERVICE_SHORT_SERVICE`    | Declare the `shortService` foreground-service type (API 34+) |

No internet permission is declared — the app never reaches the network.

## Privacy

- All SMS content, OTP logs, rules, and recipients live only in the app's
  local Room database.
- Classification runs entirely on device (Gemini Nano or the built-in keyword
  scorer).
- OTP logs are auto-pruned beyond 12 hours.

## License

This project is distributed without a license file yet — please file an issue
if you plan to reuse the code.
