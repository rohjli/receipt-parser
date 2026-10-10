# Tab MVP design

Source: Linear project P-REC-2, “Tab — receipt split app”, REC-1 through REC-24, read 2026-10-10. User authorized a local MVP and asked to leave remote GitHub creation to them. Existing parser is unavailable; user authorized implementing from the tickets.

## Outcome
Robert fronts a dinner bill, scans it, assigns items, and keeps an offline running balance for friends. Capture-to-save targets under 30 seconds; this is a usability target to validate on a real dinner, not a claim from automated tests.

## Architecture
Native Android, Kotlin, Compose Material 3, minSdk 26. Hilt, Room, bundled ML Kit text recognition, ML Kit Document Scanner, DataStore, WorkManager. Pure Kotlin domain types, parser and split engine have no Android imports. No backend or account. App-private receipt images. UI exposes capture, correction, assignment, ledger/archive, friend detail, friends and settings. Material native controls own selection, dialogs and dates.

## Core behavior
Capture opens scanner, gallery picker or manual entry. Scanner cancellation changes nothing; unavailable scanner offers gallery/manual. OCR is off the main thread and retains raw text and bounding-box lines. Empty OCR offers retry/manual. Parser reconstructs rows by vertical center and rightmost monetary token, routes fees/totals, preserves skipped text, and reconciles items + additive fees - discount with printed total. Inclusive VAT never adds twice. Ambiguous VAT defaults inclusive and stays editable. Reconciled scans skip correction. Draft is kept through configuration changes and locally persisted for process recovery.

Correction shows image, rows, quantities, line totals, fees, VAT mode, discount and printed total. Invalid money blocks continuation; mismatch alone does not. User can promote skipped rows and edit saved receipts.

Assignment includes Robert as a special owner, never a friend row. Unassigned items split among all attendees, including Robert. Explicit tags split among tagged people; integer division remainder goes to Robert even if he is untagged. Long press toggles mine-only; visible equivalent is available. Fees split equally or proportionally among eligible attendees, Robert included. Zero fee weights fall back to equal eligible distribution. Discounts are proportional to item shares. Friend total rounds to nearest peso, HALF_UP; residual is Robert's. Reconciliation difference also remains Robert's and is visibly disclosed. Each item conserves centavos; total always conserves printed total. Excluded friend fee share remains zero, with rounding adjustment stored separately.

## Persistence
Six core tables: friend, receipt, receipt_participant, line_item, item_share, payment. Monetary fields are Long centavos. Participant persists fee, discount and rounding allocations; item persists explicit assignment metadata so an unassigned row stays unassigned after editing. Robert shares use a reserved owner key in item_share without a friend FK. Friends archive, never hard delete. Receipt edits replace item/participant/share rows transactionally, retain receipt identity/date and payments. Balances derived from shares + allocations - payments; never cached. Payments may exceed debt and yield credit. Full payment has undo. Timeline interleaves charges/payments with running balance. Credits are displayed as “You owe”.

## Collection
Partial/full payments, local date and note. Nudge opens Android chooser, never sends automatically. GCash number in DataStore; absent number prompts settings. Receipt breakdown exported as a PNG with optional own share. WorkManager daily reminder respects local quiet hours, notification permission, positive overdue balances and configurable delay. Age determined by oldest debt remaining after chronological payment allocation, not the oldest receipt ever.

## Scope and gates
MVP implements foundation, capture/correction/assignment, ledger/editing, payments, settings and collection. REC-23 (GenAI) and REC-24 (backup) explicitly deferred. REC-9 infrastructure and synthetic regression fixtures do not satisfy the ten-real-receipt gate. Real receipt accuracy, physical scanner/thermal-paper behavior, 30-second dinner flow, reboot notifications and two-week collection outcomes require field validation. No tickets will be marked done merely because source exists.

## Verification
JVM tests for centavo parsing, row reconstruction, VAT/fees/discounts, 3-way 100-peso split, unassigned/mine-only, ineligible fees, rounding conservation, input rejection and oldest outstanding debt. Room integration test covers save/edit/delete/payment balances. Compose smoke test covers manual receipt → assignment → home, payment and invalid inputs. Assemble debug APK, lint, emulator install and screenshots. Do not fabricate real fixtures.
