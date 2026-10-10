# MVP verification — 10 October 2026

Environment: macOS Apple Silicon; Android Studio JDK 21; Gradle 8.14; Android SDK 36; Android 16 (API 36.1) emulator.

## Automated results

Final combined command:

```sh
./gradlew connectedDebugAndroidTest assembleDebug testDebugUnitTest lintDebug
```

Result: **BUILD SUCCESSFUL**.

- **24 JVM tests passed**: 14 domain cases (including 1,000 deterministic generated split scenarios), plus ten named JSON receipt fixtures asserting item names/quantities/amounts, fees, VAT, discount, total and reconciliation.
- **6 Android instrumentation tests passed**: actual bundled ML Kit recognition of a synthetic receipt image; PNG breakdown and nudge contents; draft recovery race; invalid quantity and money across Activity recreation; full friend → manual receipt → assignment → save → partial payment → saved-receipt edit flow; and Room transactional edit/delete preserving payment history.
- Android lint: **0 errors**. Remaining advisory warnings concern available dependency upgrades and optional KTX substitutions. Gradle emits a deprecation notice for future Gradle 9 compatibility; project pins Gradle 8.14.
- Design-document lint: **0 errors**, one advisory that tokens are documented in prose instead of YAML.
- Premium UI static audit: **0 findings**. This checker is web-oriented and is supplemental; native UI evidence comes from Android tests and emulator screenshots.
- Capture, assignment, ledger and friend-detail screenshots captured and visually inspected. Capture also inspected at a 320dp-wide phone viewport; scrolling keeps the actions available. The full-flow test proved a ₱200 shared item creates ₱100 debt; a ₱40 payment leaves ₱60; editing the item/printed total to ₱300 updates debt to ₱110.

## Bugs found and fixed

- Quantity validity reset while raw text survived Activity recreation: regression failed before the fix and passed afterward.
- Invalid monetary text overwritten during recreation: regression failed before the fix and passed afterward.
- Opening history during draft recovery could overwrite the unfinished receipt: real Room/DataStore regression failed before the readiness guard and passed afterward.
- Payment snackbar could cover the Edit receipt action: captured the overlapping screen, moved SnackbarHost into the measured bottom layout, and the end-to-end test then passed without bypassing touch input.
- Payment dialog now retains input on save failure and dismisses only on success.

Independent code review identified the first three findings; all were addressed. Emulator tests use test receipts, not user financial records.

## Field validation still needed

- Ten real receipts, with at least eight parsing to the correct printed total (REC-9). Current corpus is synthetic and explicitly marked.
- Physical document scanner, first-use Play services module download, faded/skewed thermal paper, gallery and external share providers on the actual phone.
- One real dinner and the under-30-second capture-to-done target.
- Notification delivery after a real reboot and under vendor battery restrictions; scheduling is approximate by WorkManager design.
- TalkBack and large-text behavior beyond the emulator checks; two weeks of real collection outcomes.
- GenAI cleanup and backup/import are deferred. Do not treat this build as the sole copy of irreplaceable ledger history.

## Local deliverables

`artifacts/receipt-parser-0.1.0-debug.apk` is the installable development build. `artifacts/` also holds screenshots and the verification log and is intentionally Git-ignored. Source, Room schema, tests, Gradle wrapper, docs and a future GitHub Actions build workflow are committed. No remote or external deployment was created.
