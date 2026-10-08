package com.secondmemory.common;

import com.secondmemory.backup.BackupStatus;
import com.secondmemory.backup.DatabaseBackupService;
import com.secondmemory.config.AiProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private final JdbcTemplate jdbc;
    private final AiProperties ai;
    private final DatabaseBackupService backups;

    public HealthController(JdbcTemplate jdbc, AiProperties ai, DatabaseBackupService backups) {
        this.jdbc = jdbc;
        this.ai = ai;
        this.backups = backups;
    }

    @GetMapping
    public Map<String, Object> health() {
        Integer db = jdbc.queryForObject("SELECT 1", Integer.class);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("database", db != null && db == 1 ? "UP" : "DOWN");
        response.put("chatProvider", ai.chat().provider());
        response.put("chatModel", ai.chat().model());
        response.put("chatApiKeyConfigured", configured(ai.chat().apiKey()));
        response.put("transcriptionProvider", ai.transcription().provider());
        response.put("transcriptionModel", ai.transcription().model());
        response.put("transcriptionApiKeyConfigured", configured(ai.transcription().apiKey()));
        response.put("embeddingEnabled", ai.embedding().enabled());
        // Lets anyone confirm the nightly backup without server access; failures are detailed only in the log.
        BackupStatus backup = backups.status();
        Map<String, Object> backupInfo = new LinkedHashMap<>();
        backupInfo.put("status", backup.status());
        backupInfo.put("lastSuccessAt", backup.lastSuccessAt());
        backupInfo.put("lastAttemptAt", backup.lastAttemptAt());
        response.put("backup", backupInfo);
        return response;
    }

    private boolean configured(String value) {
        return value != null && !value.isBlank() && !"replace_me".equalsIgnoreCase(value);
    }
}
