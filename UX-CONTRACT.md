# Tungaon UX contract

Sources: docs/superpowers/specs/2026-10-10-tab-mvp-design.md and Linear REC-1–22.

| Flow | State / recovery | Canonical owner |
| --- | --- | --- |
| Capture | Idle, scanning, reading, error with gallery/manual retry; cancel leaves previous state | CaptureScreen / CaptureViewModel |
| Draft | Persist changes locally; navigate back with discard confirmation; save atomically | ReceiptViewModel / repository |
| Correction | Decimal keyboard, textual field errors, live reconciliation, mismatch allowed with explanation | MoneyField / FixScreen |
| Assignment | Attendees separate from item owners; everyone default; accessible mine-only button and long press; live totals; selected items expose selected semantics, fixed assignment action opens a Material bottom sheet | AssignScreen / SplitEngine |
| Ledger | Empty invitation, reactive balances, credits distinct, archive retained | HomeScreen / Room Flow |
| Friend | Chronological running balances; partial/full payment; dates cannot be future | FriendScreen |
| Delete | Receipt confirmation names consequence; payments preserved; friend archive is reversible | Material AlertDialog |
| Sharing | Android chooser, no automatic send; empty GCash leads to settings | ShareService |
| Settings | Fixed save action; DataStore local save; quiet hours and delay validated | SettingsScreen |

English locale with Philippine peso formatting (two decimals); dates use local timezone and unambiguous month names. A receipt is counted only once saved. Busy controls disable duplicate submission. Navigation and dialogs use Compose semantics; text scales with OS. Drafts and all ledger data stay in private storage. Automatic cloud backup disabled until explicit export/import exists. Database does not destructively migrate. Browser-only audit rules do not apply to this native app.

## Layout and interaction owners

- `Components.Page`: one vertical scroll owner and optional measured footer. `PrimaryAction` keeps action geometry stable while busy. Fields remain scrollable above the keyboard and footer.
- `ReceiptStep`: current Check/Split step in text and semantics. Correction and saving retain existing validation and transaction boundaries.
- `AssignScreen`: tapping rows selects items; `Assign N items` opens Material `ModalBottomSheet`. People chips update selected items immediately using existing ownership rules. `Done assigning` closes the sheet and clears row selection; dismissing only closes it. No explicit owners means everyone shares. The visible Mine only action remains available.
- `BalanceCard`, `AmountRow`, `ReceiptDivider`: shared balance/receipt presentation. Credit labels, payment undo, and stored ledger derivation remain unchanged.
- Static privacy/payer badges are text, not no-op controls. Errors use a polite live region. Heading semantics identify screen and section titles.

## Reference-inspired navigation and shortcuts

- Ledger offers real friend shortcuts in a horizontally scrollable row. Add friend invokes the existing creation dialog; a saved friend tile opens that friend’s history. Archived friends remain discoverable through Friends → Archived.
- Capture remains the launch destination and the highlighted scan destination. Bottom navigation keeps Capture, Ledger, Friends and Settings with labels, selection semantics, and 48dp minimum interaction targets.
- The receipt viewfinder is decorative only. Camera permission/capture/cropping belong to the existing ML Kit scanner, and no animation implies a scan is underway while idle.
