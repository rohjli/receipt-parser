# MVP verification — 10 October 2026

Environment: macOS Apple Silicon; Android Studio JDK 21; Gradle 8.14; Android SDK 36; Android 16 (API 36.1) emulator.

## Automated results

Final combined command:

```sh
./gradlew connectedDebugAndroidTest assembleDebug testDebugUnitTest lintDebug
```

Result: **BUILD SUCCESSFUL**.

- **24 JVM tests passed**: 14 domain cases (including 1,000 deterministic generated split scenarios), plus ten named JSON receipt fixtures asserting item names/quantities/amounts, fees, VAT, discount, total and reconciliation.
- **6 Android instrumentation tests passed**: actual bundled ML Kit recognition of a synthetic receipt image; PNG breakdown and nudge contents; draft recovery race; invalid quantity and money across Activity recreation; full friend → manual receipt → assignment → save → partial payment → saved-receipt edit flow; and Room transactional edit/delete preserving payment history.
- Android lint: **0 errors**. Remaining advisory warnings concern available dependency upgrades and optional KTX substitutions. Gradle emits a deprecation notice for future Gradle 9 compatibility; project pins Gradle 8.14.
- Design-document lint: **0 errors**, one advisory that tokens are documented in prose instead of YAML.
- Premium UI static audit: **0 findings**. This checker is web-oriented and is supplemental; native UI evidence comes from Android tests and emulator screenshots.
- Capture, assignment, ledger and friend-detail screenshots captured and visually inspected. Capture also inspected at a 320dp-wide phone viewport; scrolling keeps the actions available. The full-flow test proved a ₱200 shared item creates ₱100 debt; a ₱40 payment leaves ₱60; editing the item/printed total to ₱300 updates debt to ₱110.

## Bugs found and fixed

- Quantity validity reset while raw text survived Activity recreation: regression failed before the fix and passed afterward.
- Invalid monetary text overwritten during recreation: regression failed before the fix and passed afterward.
- Opening history during draft recovery could overwrite the unfinished receipt: real Room/DataStore regression failed before the readiness guard and passed afterward.
- Payment snackbar could cover the Edit receipt action: captured the overlapping screen, moved SnackbarHost into the measured bottom layout, and the end-to-end test then passed without bypassing touch input.
- Payment dialog now retains input on save failure and dismisses only on success.

Independent code review identified the first three findings; all were addressed. Emulator tests use test receipts, not user financial records.

## Field validation still needed

- Ten real receipts, with at least eight parsing to the correct printed total (REC-9). Current corpus is synthetic and explicitly marked.
- Physical document scanner, first-use Play services module download, faded/skewed thermal paper, gallery and external share providers on the actual phone.
- One real dinner and the under-30-second capture-to-done target.
- Notification delivery after a real reboot and under vendor battery restrictions; scheduling is approximate by WorkManager design.
- TalkBack and large-text behavior beyond the emulator checks; two weeks of real collection outcomes.
- GenAI cleanup and backup/import are deferred. Do not treat this build as the sole copy of irreplaceable ledger history.

## Local deliverables

`artifacts/receipt-parser-0.1.0-debug.apk` is the installable development build. `artifacts/` also holds screenshots and the verification log and is intentionally Git-ignored. Source, Room schema, tests, Gradle wrapper, docs and a future GitHub Actions build workflow are committed. No remote or external deployment was created.

## Receipt parsing correction — 10 October 2026

The reported restaurant receipts exposed two layouts missing from the original corpus: an item/total followed by `2 PCS x @ 105.00`, and a name followed by `2.0 x 35.00 70.00`. Quantity details now belong to the adjacent item instead of becoming new items. The parser checks exact centavo arithmetic, accepts an omitted multiplication mark only when both price columns agree, and retains ambiguous details for correction. Zero-rated and non-taxable sales summaries are excluded from items.

Eight additional JVM regressions cover both layouts, separately positioned OCR columns, missing multiplication marks, orphan/mismatched/fractional quantities, metadata boundaries, currency-prefixed ambiguity, and numeric-leading names such as `7UP`. Tests were observed failing before their corresponding fixes; all 32 JVM tests pass afterward.

Local ML Kit validation used the two supplied photos on the Android 16 emulator through `ReceiptCapture.read`, with temporary test assets removed afterward:

- Restaurant receipt: three items, quantities 1/1/2, line totals ₱555/₱895/₱210, service charge ₱166, inclusive VAT ₱177.86, printed and computed total ₱1,826.
- Café receipt: four items, quantities 1/2/1/1, line totals ₱640/₱70/₱190/₱110, printed and computed total ₱1,010. ML Kit omitted the last multiplication mark; the matching two-column fallback recovered it.

This validates item structure and amounts on two real photos, not general OCR accuracy. Character recognition still needs manual correction: the café heading and `22oz` suffix were misread, and a missing space remained in one item name. The restaurant photo's merchant heading is obscured by the payment placard, which the existing venue heuristic selects. No personal photos or customer/cashier details are included in the committed regression sources. Previously saved drafts are not automatically reprocessed; import or scan the receipt again to use the corrected parser.

Final validation: `./gradlew testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest` completed successfully with 32 JVM tests and nine device tests passing. Lint reported zero errors and 29 advisory warnings; Gradle still reports deprecations for Gradle 9. The updated debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Additional real-receipt validation — 10 October 2026

Baseline: commit `6e1afe1`, bundled ML Kit on the Android 16 emulator, through the actual `ReceiptCapture.read` pipeline. Both new image checks **failed**. These are additional accuracy failures, not passing corpus coverage. No parser changes were made during this validation.

| Sample | Expected printed values | Actual parser output |
| --- | --- | --- |
| HALINA | Six items; line totals 17.00, 15.00, 22.00, 24.00, 24.50, 11.50; subtotal 114.00; GST 5.70; total 119.70 | Five items, with shifted prices totaling 97.00; tax 0; total 0 |
| ALL FILIPINO | Eight item rows (nine units including two rice); line totals 250.00, 189.00, 250.00, 250.00, 0.00, 64.00, 130.00, 360.00; inclusive VAT 159.96; total 1,493.00 | Four food rows plus three false tax/change items; item sum 1,095.96; tax 0; total 0 |

HALINA has no visible currency symbol; the table uses printed amounts without assuming pesos. The circled 39.90 is a three-way split of 119.70, not another item or receipt total.

Observed causes:

- HALINA's slanted amount column sits about one row above the associated names. The current horizontal-center grouping pairs wings with 15.00 instead of 17.00 and shifts the following four prices; Ube Cake loses its price entirely. Widening the row tolerance alone would risk merging neighboring items.
- ML Kit reads HALINA's subtotal as `114.O0` and total as `119. 70`. The current parser does not recognize these amount strings. `GST 5%` is also unsupported; it must not become a food item once alignment is corrected. The venue heuristic chooses check/date metadata instead of the merchant footer.
- ALL FILIPINO amounts include OCR output `250,00`, `D.00`, `B4,00`, and `1.493.00`. These are not accepted by the current amount matcher. Two 250.00 dishes, the free item, and the rice are lost. Automatically changing `B4,00` to 64.00 would be a guess without corroborating evidence.
- Misread labels `UAT Exenpt Sales`, `12: UAT`, and `Charnge Due:` become false items. Wrapped `Bangus D` is left unassociated with the sisig item. Several product and merchant names also contain OCR spelling errors.

The temporary `LocalReceiptPhotoTest.halina` and `.filipino` tests each exercised the supplied image and checked item count, total, and reconciliation. Both failed at item count (expected 6/8; got 5/7); captured output also confirms both totals are zero. Command: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.receiptparser.tab.LocalReceiptPhotoTest`.

Temporary images and the local test harness were removed from the source tree after the run. The ignored local artifact `artifacts/additional-receipt-validation.json` preserves captured OCR text/bounds, actual drafts, and manually verified expected quantities/amounts for reproduction. It is not committed and does not count as an automated regression fixture. Future fixes need separate regression coverage for skewed columns, amount recognition, tax/footer classification, and wrapped descriptions. The earlier passing JVM tests do not establish correctness on these two photos.

## Additional receipt corrections — 10 October 2026

The two failures above now have fixes and permanent sanitized OCR fixtures (`halina-skewed` and `filipino-small`). Their expected amounts were checked against the supplied images; expected names retain OCR spelling rather than claiming perfect transcription.

- `ReceiptRows` validates tilt against item/amount headers and at least three unique quantity/price pairs, then refines the slope without changing pairings. Small quantity boxes no longer anchor the whole row; overlapping columns prevent adjacent rows from merging. Missing amounts remain missing rather than shifting subsequent prices.
- `OcrAmounts` handles observed decimal whitespace, comma decimal separators, dot thousands separators, and O in two-digit fractional parts. Token boundaries prevent partial parsing of malformed numbers/dates. Manual-entry `Money.parse` remains strict; B and D are not globally replaced with digits.
- Small source images (longest edge under 1,000 pixels) are enlarged 2x for recognition. Stored images remain unchanged and OCR boxes map back to source coordinates. In the second sample this recovers the rice amount as 64.00 without guessing B=6. Contrast-threshold experiments degraded other values and were not included in production.
- GST and the narrowly observed tax/change/total label variants are classified correctly; a split suggestion is not an item or receipt total. Numbered FREE items can recover the observed terminal D.00 as zero while retaining raw text. The misread e32.00 unit suffix is removed only when quantity times unit price matches the printed line amount.
- Wrapped descriptions require indentation relative to both neighboring numbered item rows. An unindented missing-price dish remains available in skipped rows.

Four temporary device tests ran all supplied images through `ReceiptCapture.read`, asserting each quantity, each line total, tax, fee, printed total, VAT mode, and reconciliation. All four passed: HALINA six items / 119.70 / additive tax 5.70; ALL FILIPINO eight items / 1,493.00 / inclusive VAT 159.96; ramen three items / 1,826.00; café four items / 1,010.00. Source photos and the temporary harness were removed from the source tree afterward. Local detailed results are in ignored `artifacts/receipt-fixed-validation.log`.

Remaining limits: arbitrary skew without recognized column headers is not inferred; OCR spelling and merchant detection still require review (HALINA selects check metadata, and the ramen merchant is obscured). GST uses the existing tax field; this change does not add multi-currency support or relabel the VAT UI. These four samples do not establish broad receipt accuracy. Existing saved drafts must be imported/scanned again.

During full-suite verification, `FlowTest.receiptActionsStayVisibleWhileTheFormScrolls` failed once on an `Assign items` visibility assertion. The isolated rerun passed without changing UI or test code. All 60 JVM tests passed after the final indentation guard.

Final complete command `./gradlew testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest` succeeded: 60 JVM tests, nine device tests, debug APK built, lint zero errors and 29 warnings. The full device rerun also passed the previously intermittent visibility test without test/UI changes. Independent review confirmed the final indentation safeguard. The four-photo test run plus final captured-line corpus tests verify the reported amounts; no personal photo assets are included in the source changes.
