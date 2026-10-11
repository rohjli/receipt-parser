# Backup and restore verification — 11 October 2026

Implemented on `feat/backup-restore`, based on release commit `760069d`, in an isolated worktree. The original checkout’s unrelated edits were preserved.

## Behavior and scope

- Settings → Backup & restore creates or opens a portable ZIP through Android document pickers.
- Saved receipts/photos, active and archived friends, payments, exact allocations/rounding, and saved settings are included. Current drafts must be saved or discarded before either operation.
- Preview validates the whole file and shows date/counts before explicit replacement. Cancellation and invalid input never replace the ledger.
- Room migration 1→2 adds an internal recovery marker. Ledger replacement and marker creation share one transaction; settings application is idempotently recovered before normal use.
- Readers wait while recovery is pending; writes are rejected. Payment Undo and reminder generations prevent stale actions from acting on a restored ledger.

## Automated checks

Final command, using Android Studio JDK 21 and the Android 16 API 36.1 emulator:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest
```

**BUILD SUCCESSFUL**: 74 JVM tests and all 33 device tests passed, no failures/errors/skips. Debug APK built; lint reported zero errors and 30 warnings. `git diff --check` passed. The final run includes the strict missing-field validation and generation-aware Undo assertions.

- JVM suite: 74 passed, including 14 portable archive regressions. Missing required manifest/table/settings/friend fields were observed failing before the strict-presence fix.
- New backup device coverage: five storage tests, four Compose tests, and one real MainActivity navigation/recreation test.
- Storage tests cover exact ledger/settings/photo round trip, a draft created after preview, a SQLite-triggered transaction failure preserving old data, pending-marker replay, blocked observers resuming after retry, migration preserving existing payment credit, and stale Undo rejection.
- UI tests cover preview/confirmation/cancel, draft blocking, busy controls, errors, large text, and Settings navigation surviving recreation.
- An initial full device run passed 29/33. Four existing ledger/receipt tests raced asynchronous readiness: ClearBalance asserted before its displayed Room snapshot refreshed, and three Flow tests used controls before draft loading/navigation completed. Their original assertions remain; bounded waits now target enabled controls, expected destinations, and displayed state. The focused 10-test rerun passed. Independent review accepted these test changes.
- Independent archive/storage/UI review and a fresh whole-change review found and resolved queued Undo, incomplete JSON defaults, and restore-completion lifecycle issues. Scoped re-review reported no blocking findings.
- Premium UI static audit reported zero findings; it is supplemental, not a native accessibility certification.

## Simplify follow-up

Three parallel read-only reviews covered reuse, code quality, and efficiency. Cleanup removed unused restore callbacks and reminder dependencies, named the UI-state mapping, reused the participant index, and verified restored photo hashes during copying instead of reading each photo twice. Recovery guards, archive validation boundaries, and the file format are unchanged.

After cleanup, `testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest` ran with the device class filter `com.receiptparser.tab.BackupStorageTest,com.receiptparser.tab.BackupUiTest,com.receiptparser.tab.BackupNavigationTest`: **74 JVM tests and 10 focused device tests passed**, with zero failures/errors/skips. Build passed; lint remained at zero errors and 30 warnings. The storage round-trip test also checks that a same-length corrupted staged photo preserves the ledger/settings and removes failed restore files. `git diff --check` passed. The full 33-device-test run above preceded this cleanup.

## Visual evidence

Synthetic confirmation dialogs were inspected at normal and actual device 180% system font scale; dialog content scrolls and both actions remain reachable. The emulator’s system font scale was restored to 100%.

- [Normal confirmation](screenshots/backup-restore/confirmation.png)
- [Large-text confirmation](screenshots/backup-restore/large-text.png)

## Limits

Recovery tests seed a committed marker and force settings-validation failure to model an interrupted restore; they do not kill the OS process during a disk write or simulate hardware failure. Photo round-trip tests use synthetic bytes and validate exact content/hash/path preservation. Physical-phone transfer, TalkBack traversal and third-party cloud document providers remain field checks. Files are unencrypted and user managed. Previous private photos are retained after replacement; only currently referenced photos are exported.
