package com.secondmemory.backup;

import java.time.Instant;

/** Outcome of the latest backup run, for the health endpoint. Error details stay in the server log. */
public record BackupStatus(String status, Instant lastSuccessAt, Instant lastAttemptAt) {
    public static BackupStatus notRun() {
        return new BackupStatus("NOT_RUN", null, null);
    }
}
