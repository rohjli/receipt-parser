# Implementation ledger

Plan: docs/superpowers/plans/2026-10-10-tab-mvp.md

Ruling: Implement in the user-supplied empty workspace; initialize local Git and name Gradle project receipt-parser. No additional worktree needed because no repository or competing edits exist.
Ruling: Existing parser absent and PRD URL inaccessible. User explicitly authorized implementation from tickets.
Ruling: Use direct native implementation in this session; existing user authorization covers routine reversible design and build choices.
Ruling: Add participant allocation columns and item assignment metadata beyond the abbreviated REC-2 schema: required to reconstruct editing faithfully and keep fee/discount/rounding separate without storing balances.
Ruling: Nearest peso uses HALF_UP. Robert absorbs centavo and printed-total residual, shown separately in preview.
Pre-flight: domain receipt types consumed by repository and UI; monetary fields Long, IDs String, owner key __self__. Persist assignment mode independent of generated shares.

Task 1: domain baseline complete — 14 failing tests observed against stubs; implemented money/parser/split/ledger; all 14 passed. Added ten explicitly synthetic JSON parser fixtures (real-receipt gate remains open).
Task 2: persistence complete — six Room tables, derived balances, transaction replacement and payment preservation; emulator RepositoryTest passed.
Task 3: UI assembled and launched; screenshot inspected. Debug APK and Android lint passed. Device end-to-end verification in progress.
Ruling: Common ledger state is activity-scoped to give all screens one consistent read snapshot; receipt draft shared across Fix/Assign. This differs from REC-1's literal one ViewModel per screen and avoids redundant observers in this local MVP.
Ruling: Exported breakdown uses a native Canvas renderer with vertical per-person sections at 1080px, rather than screenshotting a Compose card. This keeps long dinner groups readable without offscreen composition or tiny columns.
Ruling: Do not run simultaneous Gradle builds in the same output directory; an overlapping build produced a transient test task failure. Subsequent serial build and JVM run passed.
Verification: Android 16 Espresso runner required explicit espresso-core 3.7.0 to replace a transitive version using removed InputManager reflection. UI runner then reached Save split; notification permission handling in tests needed a GrantPermissionRule.
Independent review: three P2 findings — restored quantity validation, monetary text overwritten on rotation, and editing before draft recovery. Fixes and regression checks pending.
Review fixes: emulator regression reproduced quantity Continue enabled after recreation, then reproduced lost invalid money after fixing quantity. A separate real Room/DataStore test reproduced old-history edit overwriting recovering draft. Added readiness guard, preserved raw monetary input, recomputed restored quantity validity. Payment dialog now closes only after successful storage.

Task 3: complete — emulator flow passed after moving snackbar into measured bottom layout so it cannot intercept Edit receipt. Captured assignment, ledger and friend screenshots.
Task 4: complete for MVP scope — partial payments, undo support, GCash text chooser/copy, daily reminders and quiet-hour deferral, 1080px PNG. Actual bundled OCR and share-renderer instrumentation passed; physical scanner/reminder gates remain field work.
Task 5: verification complete — final serial combined build, 24 JVM tests, 6 emulator tests, lint 0 errors. Independent review findings fixed with failing-then-passing regressions. Sources/ticket scope/remaining gates documented.
Ruling: Follow user's integration choice: preserve local main baseline and leave remote GitHub creation to them. No publishing, pushing or Linear completion claims.
