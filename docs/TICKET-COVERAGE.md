# Linear ticket coverage

Project: [Tab — receipt split app](https://linear.app/paddle-next/project/tab-receipt-split-app-92614fb87f51). Read 10 October 2026. This is local implementation coverage, not a claim that field gates passed; Linear statuses were left unchanged for review.

| Ticket | Local MVP coverage | Remaining acceptance or difference |
| --- | --- | --- |
| REC-1 | Kotlin/Compose/Material 3, SDK 26+, Hilt, package layers, JVM tests, dependencies | Shared ledger and receipt-flow ViewModels instead of one per screen |
| REC-2 | Six Room tables, reactive transactional snapshot, derived balances, repository | Allocation/assignment columns added to make edits reversible |
| REC-3 | Add, list, rename, archive/restore friends; last-used ordering | Friend colours currently share the theme accent |
| REC-4 | Typed capture → fix → assign → ledger navigation, reconcile shortcut, discard confirmation | Physical under-30-second usability check outstanding |
| REC-5 | Single-page FULL scanner, private JPEG, cancel/error handling | Scanner module download and physical thermal receipt tests outstanding |
| REC-6 | Android photo picker and ACTION_SEND image intake | Imported image is oriented/scaled; optional re-crop not implemented |
| REC-7 | Bundled ML Kit OCR off main thread; full text and geometric lines retained | Real blurry/faded receipt accuracy not yet measured |
| REC-8 | New JVM parser, y-center reconstruction, rightmost money, routing, persistence | Replacement implementation explicitly authorized; original parser unavailable |
| REC-9 | Ten clearly labeled synthetic JSON fixtures, parameterized parser tests, debug OCR fixture export | Ten real receipt fixtures and 8/10 acceptance remain open |
| REC-10 | Editable item/name/qty/line total, add/delete, receipt preview, fees/VAT/discount, live reconciliation | Delete button replaces swipe; thumbnail is scrollable; mismatch allowed with disclosure |
| REC-11 | Collapsible skipped rows; tap to promote | New row begins at zero for correction |
| REC-12 | Exact split engine, fee modes, discount, VAT, exclusions, residual and invariants | Rounding HALF_UP documented |
| REC-13 | Last group, attendees, multi-item selection, tags, totals and transactional Done | Separate group and assignment chip rows for clarity |
| REC-14 | Everyone default, mine-only button/long press, per-person fee controls | Fee checkbox replaces long-press on friend chip |
| REC-15 | Reactive balances, credits, open-since, settled disclosure, archive | Emulator checks plus real daily usage gate |
| REC-16 | Friend detail, chronological charges/payments, running balance, item/fee breakdown, rename/archive | Field usability check |
| REC-17 | Reuse fix/assign for editing; replace allocations atomically; delete confirms; payments retained | Room regression test covers core contract |
| REC-18 | Full payment + undo, partial amount/date/note, overpayment credits | Visible Paid in full action replaces swipe |
| REC-19 | Unique daily WorkManager, positive overdue balances, quiet hours, permission, home intent | Physical reboot/background scheduling check outstanding |
| REC-20 | Per-friend/all-balances nudge, GCash settings prompt, chooser + clipboard | Per-receipt text variant deferred; image breakdown available |
| REC-21 | Private 1080px PNG and FileProvider chooser; optional own share | Native Canvas with stacked person sections instead of Compose columns; very long export capped |
| REC-22 | DataStore GCash, reminder delay, quiet hours, fee mode, own-share toggle | English/PHP only |
| REC-23 | Deferred as specified by ticket | Device-gated GenAI cleanup follows real receipt validation |
| REC-24 | Settings export/import of versioned JSON-and-photo ZIP; validated replacement preview; exact ledger/settings preservation; Room recovery marker | Unencrypted user-managed files; finish/discard drafts first. Physical provider and phone-transfer field check remains. See BACKUP-RESTORE.md. |

No fabricated receipts are represented as real-world evidence. Milestone 2’s real receipt gate, milestone 3’s actual dinner gate, and milestone 4’s two-week outcome gate need the user's field use.
