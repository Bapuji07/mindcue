package com.secondmemory.backup;

import com.secondmemory.config.BackupProperties;
import com.secondmemory.config.StorageProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Where backup archives go: the audio bucket (S3 storage) or a local folder (local storage). Keys
 * are fixed names that get overwritten, so retention needs no bucket listing or lifecycle rules.
 */
@Component
public class BackupStorage {
    private final S3Client s3Client;
    private final String bucket;
    private final Path localDirectory;

    public BackupStorage(StorageProperties storage, BackupProperties backup) {
        if ("s3".equalsIgnoreCase(storage.provider()) && storage.s3() != null
                && storage.s3().bucket() != null && !storage.s3().bucket().isBlank()) {
            String region = storage.s3().region();
            this.s3Client = S3Client.builder()
                    .region(region == null || region.isBlank() ? Region.AP_SOUTH_1 : Region.of(region))
                    .build();
            this.bucket = storage.s3().bucket();
            this.localDirectory = null;
        } else {
            this.s3Client = null;
            this.bucket = null;
            this.localDirectory = Path.of(backup.localDirectory()).toAbsolutePath().normalize();
        }
    }

    public void save(String key, Path archive) throws IOException {
        if (s3Client != null) {
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/zip").build(),
                    RequestBody.fromFile(archive));
            return;
        }
        Path target = localDirectory.resolve(key).normalize();
        if (!target.startsWith(localDirectory)) {
            throw new IllegalArgumentException("Backup key escapes the backup directory: " + key);
        }
        Files.createDirectories(target.getParent());
        Files.copy(archive, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
