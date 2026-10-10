# Tungaon

A native Android app for splitting a dinner receipt and keeping a private running tab with friends. Built from the **Tab — receipt split app** Linear project (REC-1–24).

## Download

Get the APK from the [Tungaon alpha releases](https://github.com/rohjli/receipt-parser/releases). These are debug-signed builds for testing on Android 8.0+. Install updates over the existing app to keep your local ledger; do not uninstall first.

## Run locally

Open this folder in Android Studio, let Gradle sync, and run **app** on an Android 8.0+ device. For document scanning, use a device with Google Play services. Scanner components may need a first download; text recognition is bundled and runs on-device. Gallery and manual entry remain available when the scanner is unavailable.

Command line (JDK 17 or newer; build verified with Android Studio's JDK 21):

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest  # running emulator/device required
```

If Java isn't on your Mac's path:

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
```

Install SDK platform 36 and let Android Studio write `local.properties`, or set `ANDROID_HOME` to your Android SDK. Gradle project name is `receipt-parser`; package is `com.receiptparser.tab`; launcher name is **Tungaon**. Build output: `app/build/outputs/apk/debug/app-debug.apk`.

## Try it

1. Add friends in **Friends**.
2. **Capture** → scan, choose a photo, enter manually, or explicitly try the sample receipt.
3. Correct names, quantities, line totals and fees if needed. VAT can be included or added. A reconciliation difference is visible and stays with your own share.
4. Pick attendees. Untagged items belong to everyone, including you. Select one or more items, tap **Assign items**, then pick their people in the sheet. “Mine only” is also available by long press.
5. Choose equal or proportional fees, exempt anyone who shouldn't pay fees, and **Save split**.
6. Open the friend from **Ledger** to record a full/partial payment, view history, or prepare a nudge. **Receipts** lets you edit, delete, or share a breakdown image.
7. Set your GCash number and reminder preferences in **Settings**. Sharing always opens the system chooser; Tungaon never sends a message automatically.

The app starts with no friends or debt. The sample is an explicit action and only affects balances after Save split.

## Money and storage

All money is integer centavos; friends' final amounts round HALF_UP to the nearest peso. Robert/“You” absorbs fractional and reconciliation differences. No self friend row is created. Fee, discount and rounding adjustments remain separate. Balances are calculated from receipt shares minus payments. Overpayment becomes credit. Receipt edits replace shares atomically without modifying payments.

Room stores ledger data and app-private images; DataStore stores settings and the current draft. No backend, analytics, account or cloud ledger. Cloud app backup is disabled. **Uninstalling or losing the device loses the ledger until export/import backup is added.**

## Structure

- `domain/`: JVM-only money, OCR row reconstruction, receipt parsing, splitting and debt age.
- `data/`: Room, repository, settings, reminders and sharing.
- `ml/`: bounded image decoding, orientation correction, private JPEG and bundled ML Kit OCR.
- `ui/`: Compose screens, typed navigation, shared controls and Hilt view models.

## MVP boundaries

See [ticket coverage](docs/TICKET-COVERAGE.md), [design](DESIGN.md), [UX contract](UX-CONTRACT.md), and [verification](docs/VERIFICATION.md).

Real receipts were not supplied: the ten JSON fixtures are **synthetic regression cases**, not evidence of the REC-9 “8 out of 10 real receipts” gate. Scanner quality, physical-device reboot reminders, the under-30-second dinner target and two weeks of collection usage still need field testing. GenAI cleanup (REC-23) and JSON backup (REC-24) remain future work.
