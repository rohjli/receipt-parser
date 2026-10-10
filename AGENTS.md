# Repository Guidelines

## Project Structure & Module Organization

Tab is a native Android receipt-splitting app with one Gradle module, `app`. Kotlin sources live in `app/src/main/java/com/receiptparser/tab/`:

- `domain/`: Android-independent receipt parsing, money calculations, splitting, and ledger logic.
- `data/`: Room storage, repositories, settings, reminders, and sharing.
- `ml/`: image preparation and on-device ML Kit OCR.
- `ui/`: Compose screens, navigation, viewmodels, and shared components.

Resources live in `app/src/main/res/`. JVM tests and receipt fixtures are under `app/src/test/`; device tests are under `app/src/androidTest/`. Commit Room schema exports in `app/schemas/`. Consult `DESIGN.md` and `UX-CONTRACT.md` before changing UI behavior; `docs/` contains implementation, verification, and Linear ticket coverage notes.

## Build, Test, and Development Commands

Use JDK 17 or newer and Android SDK platform 36. Configure the SDK through Android Studio or an untracked `local.properties`.

- `./gradlew assembleDebug`: build the APK in `app/build/outputs/apk/debug/`.
- `./gradlew installDebug`: install on a connected emulator or device.
- `./gradlew testDebugUnitTest`: run JVM tests.
- `./gradlew lintDebug`: run Android lint.
- `./gradlew connectedDebugAndroidTest`: run device tests on a booted emulator or connected device.

CI runs JVM tests, lint, and the debug build.

## Coding Style & Naming Conventions

Follow official Kotlin style with four-space indentation. Use PascalCase for types and composables, camelCase for functions and properties, and UPPER_SNAKE_CASE for constants. Name files after their primary type or responsibility. Use Android Studio formatting; no standalone formatter is configured. Reuse `Components.kt` and `Theme.kt` for shared UI patterns.

## Testing Guidelines

Use JUnit 4 for domain tests and AndroidJUnit4, Compose UI, and Espresso for device tests. Name files `*Test.kt` and methods descriptively, such as `threeWayHundredRoundsResidualToRobert`. Add regression tests for changed parsing, rounding, persistence, and recovery behavior. No numeric coverage threshold is configured. Synthetic receipt fixtures do not establish real-receipt OCR accuracy.

## Architecture & Data Safety

Store money as integer `Long` centavos using `Money` helpers. Keep domain code free of Android dependencies. Derive balances from shares and payments. Add migrations and updated schema exports for database changes. Never commit personal receipt images, secrets, or local configuration.

## Commit & Pull Request Guidelines

The initial commit uses `feat: ...`; follow that prefix style with concise imperative descriptions and appropriate `fix:`, `test:`, or `docs:` prefixes. Keep commits focused. PRs should explain behavior changes, link relevant `REC-#` tickets, report validation and remaining limitations, and include screenshots for UI changes.
