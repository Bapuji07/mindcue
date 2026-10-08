package com.secondmemory.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondmemory.config.BackupProperties;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseBackupServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void weekdaysOverwriteTheirOwnDailyFile() {
        assertThat(DatabaseBackupService.keysFor(LocalDate.of(2026, 10, 8), "backups/"))
                .containsExactly("backups/daily/thursday.zip");
    }

    @Test
    void sundaysAlsoWriteARollingWeeklyFile() {
        // 2026-10-11 is a Sunday in ISO week 41 -> slot 41 % 4 = 1.
        assertThat(DatabaseBackupService.keysFor(LocalDate.of(2026, 10, 11), "backups/"))
                .containsExactly("backups/daily/sunday.zip", "backups/weekly/w1.zip");
    }

    @Test
    void archiveHasOneCsvPerTableAndAManifestWithRowCounts() throws Exception {
        Map<String, String> tables = new LinkedHashMap<>();
        tables.put("app_user", "id,username\n1,alice\n2,bob\n");
        tables.put("memory", "id,title\n");
        DatabaseBackupService.TableSource source = new DatabaseBackupService.TableSource() {
            @Override
            public List<String> tables() {
                return List.copyOf(tables.keySet());
            }

            @Override
            public long copyCsv(String table, OutputStream out) throws IOException {
                String csv = tables.get(table);
                out.write(csv.getBytes(StandardCharsets.UTF_8));
                out.close(); // must not close the zip
                return csv.split("\n").length - 1;
            }

            @Override
            public String schemaVersion() {
                return "11";
            }
        };

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Map<String, Object> manifest = DatabaseBackupService.writeArchive(
                source, bytes, Instant.parse("2026-10-08T21:30:00Z"), mapper);

        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertThat(entries).containsKeys("app_user.csv", "memory.csv", "manifest.json");
        assertThat(entries.get("app_user.csv")).isEqualTo(tables.get("app_user"));
        assertThat(manifest.get("rows")).isEqualTo(Map.of("app_user", 2L, "memory", 0L));
        assertThat(mapper.readTree(entries.get("manifest.json")).path("schemaVersion").asText()).isEqualTo("11");
    }

    @Test
    void failureIsReportedAndNothingIsStored() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("database down"));
        BackupStorage storage = mock(BackupStorage.class);
        DatabaseBackupService service = new DatabaseBackupService(dataSource, storage, BackupProperties.defaults(),
                mapper, Clock.fixed(Instant.parse("2026-10-08T21:30:00Z"), ZoneOffset.UTC));

        service.runBackup();

        assertThat(service.status().status()).isEqualTo("FAILED");
        assertThat(service.status().lastSuccessAt()).isNull();
        verify(storage, never()).save(any(), any());
    }

    @Test
    void disabledBackupDoesNothing() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        DatabaseBackupService service = new DatabaseBackupService(dataSource, mock(BackupStorage.class),
                new BackupProperties(false, null, null), mapper);
        service.scheduledBackup();
        assertThat(service.status().status()).isEqualTo("NOT_RUN");
        verify(dataSource, never()).getConnection();
    }
}
