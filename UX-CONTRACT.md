# Tab UX contract

Sources: docs/superpowers/specs/2026-10-10-tab-mvp-design.md and Linear REC-1–22.

| Flow | State / recovery | Canonical owner |
| --- | --- | --- |
| Capture | Idle, scanning, reading, error with gallery/manual retry; cancel leaves previous state | CaptureScreen / CaptureViewModel |
| Draft | Persist changes locally; navigate back with discard confirmation; save atomically | ReceiptViewModel / repository |
| Correction | Decimal keyboard, textual field errors, live reconciliation, mismatch allowed with explanation | MoneyField / FixScreen |
| Assignment | Attendees separate from item owners; everyone default; accessible mine-only button and long press; live totals | AssignScreen / SplitEngine |
| Ledger | Empty invitation, reactive balances, credits distinct, archive retained | HomeScreen / Room Flow |
| Friend | Chronological running balances; partial/full payment; dates cannot be future | FriendScreen |
| Delete | Receipt confirmation names consequence; payments preserved; friend archive is reversible | Material AlertDialog |
| Sharing | Android chooser, no automatic send; empty GCash leads to settings | ShareService |
| Settings | DataStore local save; quiet hours and delay validated | SettingsScreen |

English locale with Philippine peso formatting (two decimals); dates use local timezone and unambiguous month names. A receipt is counted only once saved. Busy controls disable duplicate submission. Navigation and dialogs use Compose semantics; text scales with OS. Drafts and all ledger data stay in private storage. Automatic cloud backup disabled until explicit export/import exists. Database does not destructively migrate. Browser-only audit rules do not apply to this native app.
