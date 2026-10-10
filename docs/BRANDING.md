# Tungaon branding — 10 October 2026

Approved identity: shared-receipt T, emerald #087F5B and rose #E8739E (palette option 2). The two colors represent the owner and his wife. Cream #FFF7E8 is the neutral backing.

## Implementation

- Public app name, capture header, Settings footer, recovery messages, clipboard labels and exported receipt branding use Tungaon.
- Native vector mark in `app/src/main/res/drawable/ic_tungaon.xml`; monochrome companion for notifications and Android themed icons. Geometry recreates the approved image concept in scalable Android paths.
- Adaptive launcher icons have a cream background and a two-color foreground inset into the safe zone. Android 13+ gets a monochrome layer.
- `res/values/colors.xml` owns the shared brand colors; Compose, launcher assets, native window theme and PNG exports consume them. `ui/Theme.kt` owns Material role variants.
- Application ID, Kotlin package, database name, settings keys and WorkManager IDs remain compatible with existing installations.

## Verification

- `assembleDebug testDebugUnitTest lintDebug`: passed; 24 JVM tests, no failures; lint has zero errors and 29 advisory warnings.
- `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.receiptparser.tab.CaptureAndShareTest`: both synthetic OCR and PNG-sharing tests passed on API 36.1.
- Premium static audit: zero findings (web-oriented supplemental check).
- Emulator capture screen visually inspected at normal width and 320dp. Android app-icon preview and sibling Settings screen captured under `artifacts/branding/`.
- Contrast: white on emerald 5.00:1; dark text on rose 5.69:1; white on deep rose 7.05:1; muted text on cream 6.50:1.
- Build: `artifacts/tungaon-0.1.0-debug.apk`. Screenshots and APKs are local ignored artifacts.

Physical-device and TalkBack checks remain follow-up verification. This change does not establish real-receipt OCR accuracy.
