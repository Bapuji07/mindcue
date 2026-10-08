package com.secondmemory.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondmemory.config.BackupProperties;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Copies every table to a zip of CSV files (plus a manifest) and stores it, 5 minutes after each
 * start and then daily. Daily archives are named by weekday and Sunday ones also by week, so the
 * same few files are overwritten: anything deleted is gone from all backups within about 35 days.
 * See docs/restore-database.md for restoring one.
 */
@Service
public class DatabaseBackupService {
    private static final Logger log = LoggerFactory.getLogger(DatabaseBackupService.class);

    /** Read access to the tables being backed up; the real one is a Postgres connection. */
    interface TableSource {
        List<String> tables() throws SQLException;

        /** Writes the table as CSV with a header row; returns the number of rows. */
        long copyCsv(String table, OutputStream out) throws SQLException, IOException;

        String schemaVersion() throws SQLException;
    }

    private final DataSource dataSource;
    private final BackupStorage storage;
    private final BackupProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private volatile BackupStatus status = BackupStatus.notRun();

    @Autowired
    public DatabaseBackupService(DataSource dataSource, BackupStorage storage, BackupProperties properties,
                                 ObjectMapper objectMapper) {
        this(dataSource, storage, properties, objectMapper, Clock.systemUTC());
    }

    DatabaseBackupService(DataSource dataSource, BackupStorage storage, BackupProperties properties,
                          ObjectMapper objectMapper, Clock clock) {
        this.dataSource = dataSource;
        this.storage = storage;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public BackupStatus status() {
        return status;
    }

    @Scheduled(initialDelay = 5, fixedDelay = 1440, timeUnit = TimeUnit.MINUTES)
    public void scheduledBackup() {
        if (properties.enabled()) runBackup();
    }

    void runBackup() {
        Instant startedAt = clock.instant();
        Path archive = null;
        try {
            archive = Files.createTempFile("mindcue-backup-", ".zip");
            Map<String, Object> manifest = backupDatabase(archive, startedAt);
            for (String key : keysFor(LocalDate.ofInstant(startedAt, ZoneOffset.UTC), properties.prefix())) {
                storage.save(key, archive);
            }
            status = new BackupStatus("OK", clock.instant(), startedAt);
            log.info("Database backup stored: {} tables, {} bytes", ((Map<?, ?>) manifest.get("rows")).size(), Files.size(archive));
        } catch (Exception ex) {
            status = new BackupStatus("FAILED", status.lastSuccessAt(), startedAt);
            log.error("Database backup failed", ex);
        } finally {
            if (archive != null) {
                try {
                    Files.deleteIfExists(archive);
                } catch (IOException ignored) {
                    // Temp file; the OS cleans it up eventually.
                }
            }
        }
    }

    /** Reads every table inside one read-only snapshot so the archive is consistent across tables. */
    private Map<String, Object> backupDatabase(Path archive, Instant createdAt) throws SQLException, IOException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setReadOnly(true);
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(archive))) {
                return writeArchive(new PostgresTableSource(connection), out, createdAt, objectMapper);
            } finally {
                connection.rollback();
            }
        }
    }

    /** One CSV per table plus manifest.json (time, schema version, row counts). */
    static Map<String, Object> writeArchive(TableSource source, OutputStream target, Instant createdAt,
                                            ObjectMapper objectMapper) throws SQLException, IOException {
        Map<String, Long> rows = new LinkedHashMap<>();
        Map<String, Object> manifest = new LinkedHashMap<>();
        try (ZipOutputStream zip = new ZipOutputStream(target)) {
            OutputStream entryStream = new FilterOutputStream(zip) {
                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    zip.write(b, off, len);
                }

                @Override
                public void close() throws IOException {
                    flush(); // the zip stays open for the next entry
                }
            };
            for (String table : source.tables()) {
                zip.putNextEntry(new ZipEntry(table + ".csv"));
                rows.put(table, source.copyCsv(table, entryStream));
                zip.closeEntry();
            }
            manifest.put("createdAt", createdAt.toString());
            manifest.put("schemaVersion", source.schemaVersion());
            manifest.put("format", "CSV with header, one file per table (PostgreSQL COPY)");
            manifest.put("rows", rows);
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
            zip.closeEntry();
        }
        return manifest;
    }

    /** Daily archive by weekday; on Sundays also a weekly archive (rolling 4 weeks). */
    static List<String> keysFor(LocalDate date, String prefix) {
        List<String> keys = new ArrayList<>();
        keys.add(prefix + "daily/" + date.getDayOfWeek().name().toLowerCase(Locale.ROOT) + ".zip");
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            keys.add(prefix + "weekly/w" + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) % 4 + ".zip");
        }
        return keys;
    }

    private static final class PostgresTableSource implements TableSource {
        private final Connection connection;

        PostgresTableSource(Connection connection) {
            this.connection = connection;
        }

        @Override
        public List<String> tables() throws SQLException {
            List<String> tables = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT table_name FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                    ORDER BY table_name
                    """);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) tables.add(rs.getString(1));
            }
            return tables;
        }

        @Override
        public long copyCsv(String table, OutputStream out) throws SQLException, IOException {
            String quoted = "\"" + table.replace("\"", "\"\"") + "\"";
            return connection.unwrap(PGConnection.class).getCopyAPI()
                    .copyOut("COPY public." + quoted + " TO STDOUT WITH (FORMAT csv, HEADER)", out);
        }

        @Override
        public String schemaVersion() throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1
                    """);
                 ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
