# Tungaon UI refresh — 10 October 2026

## Direction and changes

User selected a balanced interface: warm personality with compact, practical screens. Preserve the approved emerald-and-rose identity.

- Compact capture masthead and clear scan/photo/manual hierarchy; static privacy copy and a useful draft-resume card.
- Shared panel, heading, section, balance, receipt-divider and action components. Native Material controls remain canonical.
- Check/Split progress, fixed receipt actions and selected-item semantics. A native people-picker bottom sheet replaces the bulk controls that required scrolling back to the top.
- Saving shows the participant count, printed total and mismatch warning beside the action. Existing split, rounding and credit rules remain unchanged.
- Ledger and friend detail share a prominent balance card and consistent transaction hierarchy; Friends show a balance summary; Settings use a fixed save action.
- Full navigation labels use a two-row arrangement above 1.3× text scale. Monetary rows stack at increased text scale. The page measures its footer outside the scroll body and consumes Scaffold insets before handling the IME.

## Verification

Final command: `./gradlew assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest` with Android Studio JDK 21.

- Build successful; 24 JVM tests and 8 device tests passed on Android API 36.1.
- Two new regression tests initially failed against the old UI: receipt actions were off-screen, and selected items had no reachable assignment action. Both now pass. Existing workflow tests now target fixed actions directly and still cover invalid input/recreation and receipt → payment → edit balances.
- Lint: 0 errors, 29 advisory warnings. Premium static audit: 0 findings. DESIGN.md lint: 0 errors, one existing prose-format advisory.
- Native screenshots inspected for Capture, assignment, the open people-picker sheet, ledger, friend detail and Settings. Narrow 320dp / 200% system-text checks exposed navigation wrapping, which was corrected and rechecked. Capture actions remain scrollable and the correction action remains visible at that size.
- Emulator IME toolbar and focused field layout were inspected. Full phone keyboard and TalkBack checks remain device follow-ups; this is not a claim of exhaustive accessibility certification.
- No production financial data or receipt images were used in testing. Android test examples remain test fixtures.

Local APK: `artifacts/tungaon-0.1.0-ui-refresh-debug.apk`.
Screenshots and static audit: `artifacts/ui-refresh/` (ignored build evidence).

## Reference-led refinement

The user then supplied a modern rounded bill-splitting/scanning reference. Implemented its visual character using Tungaon’s emerald and rose:

- White surfaces on a pale neutral background, larger corner radii, soft elevation and a centered emerald gradient balance card.
- Rose new-receipt action, raised scan navigation tile and pastel friend shortcuts. Shortcuts use saved friends and open their history; Add friend uses the existing dialog.
- Receipt viewfinder illustration with the approved logo, emerald corners and rose scan line. Real scanning still uses ML Kit; the illustration has no fake progress or seeded receipt data.
- Preserved every navigation destination, receipt validation, payment history, error recovery and persistent action footer.

Verification for this refinement:

- `assembleDebug testDebugUnitTest lintDebug`: successful, 24 JVM tests, 0 lint errors / 29 warnings.
- `connectedDebugAndroidTest`: 9 tests passed. Added coverage for creating a friend from the ledger shortcut and opening their history. Existing payment/edit tests now use the shortcut and still verify the resulting balance.
- Visually inspected native Capture, Ledger and Friend screens, plus Capture and Ledger at 320dp / 200% text. The header’s decorative badge initially crowded the brand at large text; it now hides along with the receipt illustration. Ledger filters wrap onto separate rows when needed. Verified capture actions remain reachable by scrolling and restored emulator display settings afterward.
- Premium supplemental static audit: 0 findings. DESIGN.md lint: 0 errors, one prose-format warning. Native tests/rendering are the meaningful checks; the static audit primarily targets web projects.
- Device tests preceded the final large-text-only header/filter adjustments, which was rebuilt, linted, unit-tested and manually rechecked. Physical camera and TalkBack remain follow-up checks.

Current APK: `artifacts/tungaon-0.1.0-modern-ui-debug.apk`.
Current screenshots: `artifacts/reference-refresh/`. Ledger/friend screenshots use synthetic test data only.

## Release verification — v0.1.0-alpha.2

Rebuilt the final tree with version name `0.1.0-alpha.2` and version code `2`. `assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest` passed: 24 JVM tests, 9 Android emulator tests, 0 lint errors and 29 advisory warnings. This includes the final header/filter adjustments. The APK signing certificate matches the original alpha.1 artifact, whose SHA-256 was checked against GitHub’s recorded asset digest. The application ID remains unchanged for in-place upgrades. Settings now displays the build version, and the CI APK artifact uses the Tungaon name.
