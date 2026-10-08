package com.secondmemory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nightly database backup. Backups go to the audio bucket under [prefix] when storage is S3, or to
 * [localDirectory] otherwise. Unset values fall back to the defaults below.
 */
@ConfigurationProperties(prefix = "memory.backup")
public record BackupProperties(Boolean enabled, String prefix, String localDirectory) {
    public BackupProperties {
        enabled = enabled == null || enabled;
        prefix = prefix == null || prefix.isBlank() ? "backups/" : prefix.endsWith("/") ? prefix : prefix + "/";
        localDirectory = localDirectory == null || localDirectory.isBlank() ? "./data/backups" : localDirectory;
    }

    public static BackupProperties defaults() {
        return new BackupProperties(null, null, null);
    }
}
