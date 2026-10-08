# Restoring the MindCue database from a backup

The backend backs up the whole database 5 minutes after each start and then every 24 hours
(`backup/DatabaseBackupService.java`). Check the latest result at `GET /api/v1/health` → `backup`.

## Where the backups are

In the audio S3 bucket (`mindcue-audio-records`), under `backups/`:

| Key | Written | Kept |
|---|---|---|
| `backups/daily/<weekday>.zip` | every run | overwritten a week later |
| `backups/weekly/w0..w3.zip` | runs on Sundays (UTC) | overwritten 4 weeks later |

Deleted data therefore disappears from every backup within about 35 days.

Each zip holds one `<table>.csv` per table (PostgreSQL `COPY … CSV HEADER`) and `manifest.json` with
the time, the Flyway schema version and the row count of every table.

## Restore steps

You need shell access to a machine with PostgreSQL and the backend jar.

1. Download the zip from the S3 console and unzip it into a folder, e.g. `restore/`.
2. Create an empty database:
   ```sh
   sudo -u postgres createdb mindcue_restore
   ```
3. Start the backend once against the empty database (or run Flyway) so the schema is created at
   the version in `manifest.json`. Stop it again before loading data.
4. Load every table with foreign-key checks off for this session, then check the counts against
   `manifest.json`:
   ```sh
   cd restore
   {
     echo "SET session_replication_role = replica;"
     echo "TRUNCATE flyway_schema_history;"
     for f in *.csv; do
       t="${f%.csv}"
       echo "\\copy public.\"$t\" FROM '$PWD/$f' WITH (FORMAT csv, HEADER)"
     done
   } | sudo -u postgres psql -v ON_ERROR_STOP=1 -d mindcue_restore
   ```
5. Point the backend at the restored database (`DB_URL` in `/etc/mindcue.env`), or rename the
   databases, and restart the service.

Audio files are not in the backup: they are deleted after transcription, and unprocessed
recordings stay in the bucket under their own keys.
