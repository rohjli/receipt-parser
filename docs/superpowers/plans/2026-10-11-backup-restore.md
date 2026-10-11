# Backup and restore implementation plan

Goal: portable, user-controlled recovery of saved Tungaon data on another installation.

Architecture: a versioned ZIP contains JSON ledger records and saved settings, plus referenced receipt images with hashes. Android's document picker selects the destination/source. A validated preview requires explicit replacement confirmation. Room commits replacement rows and a pending settings record together; startup completes that record before exposing the app. A shared storage gate prevents concurrent writes.

Stack: Kotlin serialization, java.util.zip, Room migration 1→2, DataStore, Compose, Storage Access Framework. No backend or new dependency.

## Approved scope and constraints

- User chose replacement, not merge, with preview and explicit confirmation.
- Include saved receipts, all six ledger tables, images and saved settings; preserve IDs and exact centavos/allocations. Never recompute historic splits.
- Finish or discard unfinished drafts before either operation. Unsaved settings are excluded.
- Unencrypted local archive; disclose personal data before export. User chooses whether a document provider stores it locally or in cloud storage.
- Reject invalid, newer, oversized, incomplete or unsafe archives before mutation.
- Preserve old photos until restore succeeds. No destructive migration or application-ID change.
- Recovery after commit is idempotent and blocks app use until complete; errors remain visible with retry.

## Work and verification

- [x] Archive agent: serializable models, bounded streaming ZIP, format/hash/reference/money validation; JVM tests for round trip, corruption, traversal, unsupported schema and bounds.
- [x] Storage agent: coordinated export/import, Room marker/migration, settings restoration, gate; device tests for exact restoration, draft blocking, precommit failure and postcommit recovery.
- [x] UI agent: existing Settings panel and dedicated backup screen, preview/cancel/replace, busy/error states; Compose tests for consequences, cancellation, drafts and large text.
- [x] Root: document pickers registered at app scope, recovery gate before app viewmodels, block interactions during operations, reset navigation and stale Undo after restore, consistent background-reminder reads.
- [x] Independent review: review archive trust boundary, migration/recovery and UI lifecycle; resolve important findings.
- [x] Final verification: all JVM tests, complete device suite, lint and debug build; inspect native screen and preserve synthetic evidence.

## Review focus

Provider failure including close must never report export success. A newly created draft must block confirmation. Process death between database and settings must recover. Restored photo paths must resolve within private storage. Stale payment Undo must not mutate imported records. Archive validation must not silently discard records or coerce money.
