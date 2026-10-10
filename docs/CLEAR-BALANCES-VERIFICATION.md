# Clear sections verification — 11 October 2026

Implemented the approved Clear sections ledger design on `feat/clear-balances`, based on `59c678a`, in the attached worktree. The original checkout's unrelated changes were left intact.

## Behavior

- Positive balances appear under **Owe you**, credits under **You owe**, and zero balances under initially expanded **No balance**. Counts refer to people, including archived friends whose ledger history remains available.
- Positive cards have a prominent amount and full-width **Record payment** action. Zero rows are grouped in a compact panel with avatars, amounts and history links.
- Home reuses `PaymentDialog` and TabApp's payment transaction/Undo callback. Opening or cancelling writes nothing; successful saves close the dialog; failed saves retain the input and show the error. Partial, full and overpayment arithmetic remains owned by the existing ledger.
- Font scaling stacks positive and zero amounts when needed. No schema, data migration, balance calculation, or production seed data was introduced.

## Checks

Environment: Android Studio JDK 21, Android SDK 36, Android 16 emulator `emulator-5554`.

```sh
env JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ANDROID_HOME=/Users/rohjli/Library/Android/sdk ANDROID_SERIAL=emulator-5554 \
  ./gradlew testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.receiptparser.tab.ClearBalanceSectionsTest
```

- JVM suite: **60 passed**, no failures or skips.
- New Compose/Room suite: **5 passed**, no failures or skips. Covers sign grouping and archived zero-person history; initially expanded/saveable disclosure; cancel, partial/full payment and reactive repository Undo; 1.8× font scaling at the device's actual density; failed-save field retention.
- Debug build: **passed**.
- Android lint: **passed**, no errors; 30 warnings remain in the report.
- `git diff --check`: passed.
- Supplemental Premium UI static audit: zero findings. This is a web-oriented checker; Android test and screenshot evidence carries the native UI verification.
- Independent code review and scoped re-review: no important production defects found.

The original three regression tests were run before implementation and failed because the new groups, expanded history rows and Record payment action did not exist. A later ambiguous amount selector was corrected because the amount appears in both the hero and the person card. The large-text test was corrected to preserve real device density.

## Artifacts and limits

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Synthetic component screenshots: [normal text](screenshots/clear-balances/normal.png) and [large text](screenshots/clear-balances/large-text.png). They render HomeScreen directly, without the full app navigation scaffold; captured copies also remain in the ignored `artifacts/clear-balances/` directory.
- The full pre-existing device suite was not rerun. The complete JVM suite and targeted new device suite were run.
- Undo was exercised through the actual repository and reactive Home view; the production Snackbar callback wiring was reviewed, but the Snackbar tap was not covered by a new integration test.
- TalkBack traversal and physical-device verification were not performed.
