# Tab design context

## Product and audience
Native Android tool for Robert splitting a Philippines dinner bill with friends. English UI, PHP currency, device-local dates/time. Source: Linear P-REC-2 / REC-1–22. No existing visual system.

## Visual direction
A crisp indigo dinner tab: cool paper, ink-blue actions, restrained mint success. Receipt-like item rows and oversized tabular totals make checking the bill quick. Capture has a simple framed receipt motif; no decorative photos or gradients. Large readable type and generous thumb targets; compact assignment chips.

## Tokens and runtime owner
Owner: ui/Theme.kt (Material 3 ColorScheme and Typography). All screens consume MaterialTheme; no independent screen palettes.
- Primary #3448AE; on-primary #FFFFFF.
- Background #F7F8FC; surface #FFFFFF.
- Ink #19213A; muted #586077.
- Success #236B50; error #BA1A1A.
- Shapes: Material 3 default fields and chips, 20dp panels, full-round avatars.
- Spacing: 4 / 8 / 12 / 16 / 24 / 32dp.
- Typography: Android sans-serif body and bold sans-serif display; monospace monetary readouts. System fonts avoid network and font swaps.
- Body 16sp, labels 12sp, section title 22sp, display 36sp. Scale with system settings.

## Canonical UI map
Material 3 owns buttons, text fields, dialogs, chips, navigation bar, checkboxes, date picker and SnackbarHost. ui/Components.kt owns Page, SectionHeading, MoneyField, EmptyState, Avatar and amount rows. ui/Theme.kt owns tokens; domain/Money.kt owns all money parsing/formatting. Native Compose semantics and 48dp touch targets. No web UI, CSS, browser URL state or HTML controls are applicable.

## Behavior
See UX-CONTRACT.md. Persistent bottom navigation for Capture, Ledger, Friends, Settings; draft screens have explicit back/discard handling. Save only after a successful local transaction. Error text is actionable and retained until retry/dismissal. Destructive receipt deletion confirms. Archived friends preserve history and may be restored. New install begins empty; sample receipt is an explicit demo action, never seeded debt.

## Verification
Android build/lint, domain tests, Room/Compose instrumentation, emulator rendering at phone size. Physical camera and TalkBack are follow-up checks. No animation is essential to information.
