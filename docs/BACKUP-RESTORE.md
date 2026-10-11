# Backup and restore

Open **Settings → Backup & restore**. Finish or discard any unfinished receipt first.

## Create a backup

Choose **Create backup**, then choose a destination in Android’s file picker. The ZIP includes saved receipts and their original photos, active and archived friends, recorded payments, exact saved shares and rounding, and saved settings (including GCash and reminder preferences). Unsaved settings and unfinished receipts are excluded.

The file is not encrypted. Store it privately, ideally somewhere you can access if you lose your phone. Choosing a cloud document provider uploads the file through that provider; Tungaon has no backup server or automatic upload. A failed export may leave an incomplete destination file; create another backup after correcting the error.

## Restore

Choose **Choose backup**, select a Tungaon ZIP, and review its creation date and counts. Nothing is replaced during preview. **Replace and restore** replaces the device’s entire saved ledger and settings. It does not merge records. Make a backup of the current ledger first if you want to preserve it.

Invalid or incomplete files are rejected before replacement. If the process stops after the ledger has been replaced, the next launch finishes the saved-settings restoration before allowing edits. If that fails, use the displayed retry action after resolving the storage problem.

## Format and limits

Format version 1 uses `manifest.json` with a `tungaon-backup` identifier and all six ledger tables, settings, creation time, and image metadata. Images use relative `images/<id>.jpg` paths with SHA-256 hashes and sizes; imported paths are remapped into app-private storage. Hashes detect damage; they do not authenticate the file’s author.

Maximum uncompressed size is 512 MiB, with a 16 MiB manifest, 20 MiB per photo, 10,000 photos and 100,000 total ledger records. Unexpected entries, duplicate identifiers, broken relationships and invalid monetary values are rejected. Historic allocations are preserved rather than recalculated.

Room schema 2 adds only an internal restore-recovery table; migration from schema 1 preserves existing ledger tables. Android automatic cloud backup remains disabled. Previous private receipt photos are retained after replacement; future backups include only those referenced by the restored ledger.
