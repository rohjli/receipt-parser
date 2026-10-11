# Tungaon design context

## Product and audience
Native Android tool for Robert splitting a Philippines dinner bill with friends. English UI, PHP currency, device-local dates/time. Source: Linear P-REC-2 / REC-1–22. Brand approved on 10 October 2026: Tungaon, emerald and rose shared-receipt mark.

## Visual direction
Reference-led direction: airy white surfaces, a softly shaded emerald balance card, pastel friend tiles, and a raised rose scan action. Cream remains in the launcher and shared receipt artwork. The two halves of the receipt-shaped T carry the owner’s and his wife’s favorite colors. Receipt-like item rows and oversized tabular totals make checking the bill quick. Capture uses the shared-receipt T mark in a decorative receipt viewfinder. A subtle emerald gradient is reserved for balance summaries; rose highlights the scan line and main navigation action. Large readable type and generous thumb targets; compact assignment chips.

## Tokens and runtime owner
Owner: ui/Theme.kt (Material 3 ColorScheme and Typography). All screens consume MaterialTheme; no independent screen palettes.
- Brand emerald / primary #087F5B; on-primary #FFFFFF.
- Brand rose / secondary container #E8739E with dark #3D1023 text; secondary text/actions #9B315A with white text.
- App background #F8FAF8; surface #FFFFFF. Brand cream #FFF7E8 remains available for exported receipts and launcher backing.
- Ink #182D26; muted #4C5E54.
- Emerald conveys the brand; success must also have a textual label. Error #BA1A1A.
- Brand colors are owned by res/values/colors.xml, consumed by Theme.kt, vector drawables, native window theme, and shared PNG rendering. Other Material role colors live in Theme.kt.
- ic_tungaon.xml is the native vector master of the approved split-receipt T. Adaptive launcher icons use cream backing, safe-zone insets, and an Android 13 monochrome variant. Notification icons use the monochrome mark.
- Public name: Tungaon. Keep application ID, database, preference keys, and scheduled-work IDs stable so installed copies retain their data.
- Shapes: 8dp extra-small, 12dp small, 16dp medium, 24dp panels, 32dp feature panels; full-round avatars. Theme.kt owns Material shapes.
- Spacing: 4 / 8 / 12 / 16 / 24 / 32dp.
- Typography: Android sans-serif body and bold sans-serif display; monospace item amounts and sans-serif balance displays. System fonts avoid network and font swaps.
- Body 16sp, labels 12sp, section title 20sp, screen heading 28sp, balance display 38sp. Scale with system settings.

## Canonical UI map
Material 3 owns buttons, text fields, dialogs, chips, checkboxes, date picker and SnackbarHost. TabApp owns the rounded bottom navigation surface with native selectable tab semantics. ui/Components.kt owns Page (scrolling body plus measured fixed footer), PrimaryAction, ReceiptStep, SectionLabel, Panel, BalanceCard, ReceiptDivider, MoneyField, EmptyState, Avatar, BrandHeader, ReceiptViewfinder and amount rows. ui/Theme.kt owns tokens; domain/Money.kt owns all money parsing/formatting. Native Compose semantics and 48dp touch targets. No web UI, CSS, browser URL state or HTML controls are applicable.

## Behavior
See UX-CONTRACT.md. Persistent bottom navigation for Capture, Ledger, Friends, Settings; draft screens have explicit back/discard handling. Save only after a successful local transaction. Error text is actionable and retained until retry/dismissal. Destructive receipt deletion confirms. Archived friends preserve history and may be restored. New install begins empty; sample receipt is an explicit demo action, never seeded debt.

## Verification
Android build/lint, domain tests, Room/Compose instrumentation, emulator rendering at phone size. Physical camera and TalkBack are follow-up checks. No animation is essential to information.

## UI refresh — balanced, warm and practical

User-selected direction: balanced warmth with compact practical screens. The signature is a shared receipt: subtle perforated dividers and clear tabular amounts, with emerald for actions/balance summaries and rose for selections. The logo is a compact masthead rather than a full-screen hero.

- Capture offers one prominent scan action, followed by photo/manual alternatives; privacy is static explanatory text. A recoverable draft replaces new-receipt actions.
- Check and Split show two explicit steps. The action footer is measured below the scrolling content, never overlaid on fields; it accounts for the keyboard.
- Assignment uses the user-selected Guided flow: one receipt item at a time, explicit Everyone and Just me shortcuts, and full-width people checkbox rows with rose selection. Previous/Next actions stay in the measured footer. View all items opens a review with direct Edit people actions. People on this receipt shows the existing 40dp initial avatars with full names below and You labeled Paying. The bubbles are a static attendance summary; labeled Add people and Edit people actions open the attendee sheet. People wrap into more rows instead of hiding offscreen, and attendance remains separate from item ownership. Valid selections update the draft immediately; clearing every checkbox blocks progression without overwriting the last valid assignment. Saving is available only on review.
- Ledger and friend detail share an emerald BalanceCard; credits remain explicitly labeled. Receipt rows use a consistent amount hierarchy and perforated divider.
- Friends show a balance summary alongside each name. Settings use labeled sections and a fixed save action.
- Amount rows stack at increased font scale. Bottom navigation uses two rows above 1.3× system font scale to preserve full labels; decorative capture artwork steps aside for the text. Forms retain native keyboard and validation semantics. The refresh introduces no animation dependency, web layer, new storage, or calculation rule.

## Supplied reference — modern rounded mobile UI

User supplied a two-screen bill-splitting/scanning reference and requested the same UI character in Tungaon’s green and pink. This refines the previously selected balanced direction.

- Ledger: compact brand masthead, centered emerald gradient balance, rose new-receipt action, horizontal pastel shortcuts sourced from actual friends, then balance/receipt filters and ledger rows. Add friend opens the existing dialog; tiles open existing friend history. No nearby presence, profile photos, fabricated balances, or seeded friends.
- Capture: white receipt-viewfinder panel with green corner marks and a rose scan line; illustration is decorative and not a fake camera preview. The Scan receipt action still opens the real ML Kit document scanner. Photo/manual recovery stays reachable. Artwork is hidden above 1.3× text scale.
- Navigation: a white rounded surface and raised rose Capture tile, preserving all four labeled destinations and native selected tab semantics. It uses two rows at large font sizes.
- Shared panels use soft elevation instead of prominent borders. Rounded shapes and neutral surfaces also apply to correction, assignment, friend history and settings.

## Ledger — clear sections

User-approved direction: keep the existing emerald summary, rose actions, friend shortcuts and navigation, while making balance direction and payment actions explicit.

- Group people under **Owe you**, **You owe** (credits), and **No balance**, with people counts. Zero balances do not imply a payment or a prior shared receipt.
- Positive balance cards show the friend, oldest outstanding date, prominent amount labeled **Owes you**, and a full-width emerald **Record payment** action. The card still opens friend history.
- **No balance** starts expanded and can be collapsed. Its compact avatar/name/zero-amount rows retain access to history; supporting copy reads **Nothing owed either way**.
- Use native shared theme colors, shapes and amount presentation. Amounts stack when text is enlarged. The hero counts people with positive balances using **From 1 person** / **From N people**.

## Backup and restore

Settings links to a dedicated Backup & restore page using the existing Page, Panel, SectionLabel and action components. Emerald identifies backup creation; destructive restore confirmation uses the existing error color plus explicit replacement wording. The preview lists backup date and receipt, friend, payment and photo counts. File selection belongs to Android's document picker. Draft blocking, unencrypted-file disclosure, saved-settings scope, progress, errors and recovery are expressed in text, with scrollable confirmation content at large font scales.
