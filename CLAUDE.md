# CLAUDE.md

## Project
Android app - OTP Forwarder. See PLAN.md for full spec and screen layouts.

## Build
- Run `./gradlew assembleDebug` to verify changes compile
- Run `./gradlew test` to run unit tests
- Always build after creating or modifying files to catch errors early

## Rules
- Follow the project structure in PLAN.md exactly
- Use Kotlin, Jetpack Compose, Material 3, Hilt, Room
- Min SDK 26, Target SDK 35
- Use version catalog (gradle/libs.versions.toml) for all dependencies
- Don't add libraries not in the plan
- Package name: com.otpforwarder
- Commit after each completed phase with a descriptive message
