# Tungaon UX contract

Sources: docs/superpowers/specs/2026-10-10-tab-mvp-design.md and Linear REC-1–22.

| Flow | State / recovery | Canonical owner |
| --- | --- | --- |
| Capture | Idle, scanning, reading, error with gallery/manual retry; cancel leaves previous state | CaptureScreen / CaptureViewModel |
| Draft | Persist changes locally; navigate back with discard confirmation; save atomically | ReceiptViewModel / repository |
| Correction | Decimal keyboard, textual field errors, live reconciliation, mismatch allowed with explanation | MoneyField / FixScreen |
| Assignment | Guided item-by-item assignment; attendees separate from owners; everyone visibly checked by default; Just me shortcut; live item shares; View all items and final review before save | AssignScreen / SplitEngine |
| Ledger | Empty invitation, reactive balances, credits distinct, archive retained | HomeScreen / Room Flow |
| Friend | Chronological running balances; partial/full payment; dates cannot be future | FriendScreen |
| Delete | Receipt confirmation names consequence; payments preserved; friend archive is reversible | Material AlertDialog |
| Sharing | Android chooser, no automatic send; empty GCash leads to settings | ShareService |
| Settings | Fixed save action; DataStore local save; quiet hours and delay validated | SettingsScreen |

English locale with Philippine peso formatting (two decimals); dates use local timezone and unambiguous month names. A receipt is counted only once saved. Busy controls disable duplicate submission. Navigation and dialogs use Compose semantics; text scales with OS. Drafts and all ledger data stay in private storage. Automatic cloud backup remains disabled; portable export/import is explicitly user initiated through Android’s document picker. Database does not destructively migrate. Browser-only audit rules do not apply to this native app.

## Layout and interaction owners

- `Components.Page`: one vertical scroll owner and optional measured footer. `PrimaryAction` keeps action geometry stable while busy. Fields remain scrollable above the keyboard and footer.
- `ReceiptStep`: current Check/Split step in text and semantics. Correction and saving retain existing validation and transaction boundaries.
- `AssignScreen`: shows one item and its effective owners as full-row checkboxes. Everyone explicitly restores the default empty-owner-set rule; Just me chooses the payer. Valid changes persist immediately. A zero-person selection stays transient and blocks navigation and table changes until resolved; it never silently writes everyone. Previous item and system Back return to the preceding item; Back from review returns to the current item, while Back from the first item keeps the existing discard confirmation. Current item and checkbox state survive activity recreation.
- `AssignScreen` review: View all items can open review at any point with a valid selection. Each item has an Edit people action; fees, rounding and reconciliation remain visible. Save split/Save changes appear only on review and retain the existing atomic save. Progress is the current item position, not a claim that every item was reviewed.
- `AssignScreen` attendee summary and sheet: People on this receipt shows static avatars with visible names and You / Paying. Add people and Edit people open the same attendee sheet; both are disabled while saving or while the current item has an empty pending owner selection. Avatar initials are decorative, and attendee names remain readable with large text. Shared-by-everyone items include added attendees; explicitly chosen owners remain fixed. Removing an attendee removes their ownership, and a visible notice identifies items that return to everyone after losing their sole owner. Add friend remains available. No long press is needed for assignment.
- `BalanceCard`, `AmountRow`, `ReceiptDivider`: shared balance/receipt presentation. Credit labels, payment undo, and stored ledger derivation remain unchanged.
- Static privacy/payer badges are text, not no-op controls. Errors use a polite live region. Heading semantics identify screen and section titles.

## Reference-inspired navigation and shortcuts

- Ledger offers real friend shortcuts in a horizontally scrollable row. Add friend invokes the existing creation dialog; a saved friend tile opens that friend’s history. Archived friends remain discoverable through Friends → Archived.
- Capture remains the launch destination and the highlighted scan destination. Bottom navigation keeps Capture, Ledger, Friends and Settings with labels, selection semantics, and 48dp minimum interaction targets.
- The receipt viewfinder is decorative only. Camera permission/capture/cropping belong to the existing ML Kit scanner, and no animation implies a scan is underway while idle.

## Ledger balance sections

- HomeScreen groups the existing derived person balances by sign: positive under **Owe you**, negative under **You owe**, and zero under **No balance**. Counts refer to people, including archived people already represented in the ledger. Archiving never hides an outstanding balance or removes history.
- **No balance** is initially expanded; the disclosure state survives recreation. Newly added friends with no receipt history belong here without a paid/settled status claim.
- **Record payment** on a positive balance opens the shared `PaymentDialog` used by FriendScreen, prefilled with that balance. Opening or cancelling the dialog writes nothing. Amount, date, note, validation, overpayment credit messaging and busy/error behavior remain shared.
- TabApp owns the successful payment transaction and Snackbar Undo. The dialog closes only on successful persistence. Reactive balances then move a fully paid person into **No balance**, retain a partially paid person under **Owe you**, or place an overpayment under **You owe**. Undo restores the derived prior balance.
- Positive cards, credit cards and zero-balance rows preserve access to friend history. Credit cards do not offer an action that incorrectly records money received.

## Portable backup and restore

- Settings → Backup & restore exports a versioned ZIP with all saved ledger rows, referenced receipt photos and saved settings. Unfinished drafts and unsaved settings are excluded; finish or discard any draft before starting either operation. Files are not encrypted and the page explains their private contents.
- Create backup uses Android CreateDocument; Choose backup uses OpenDocument. Cancelling either picker changes nothing. Success is reported only after file streams close successfully. Invalid, incomplete, unsupported or oversized archives never change the ledger.
- Restore shows creation date and record/photo counts, then requires **Replace and restore**. It replaces current saved data and settings, without merging. Cancel discards only the staged preview. A newly created draft blocks restore even after file inspection.
- Operations block duplicate actions and app navigation while active. Completion clears stale payment Undo and navigation history so imported IDs cannot be acted on from an old screen.
- A Room transaction replaces exact records and writes a pending settings marker. DataStore replacement completes next; startup/retry must finish a pending marker before exposing the app. A failed recovery keeps the app blocked with a visible Retry action. Reads wait during recovery; writes reject while recovery is required.
- Receipt images are staged to new private paths and synced before the transaction; previous private photos are retained to avoid deleting an image needed by a failed restore. Backup files contain only photos referenced by saved receipts.
