package com.secondmemory.audio;

import com.secondmemory.config.StorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class AudioStorageService {
    private static final String S3_PREFIX = "s3://";

    private final boolean useS3;
    private final Path audioDirectory;
    private final S3Client s3Client;
    private final String bucket;

    public AudioStorageService(StorageProperties properties) throws IOException {
        this.useS3 = "s3".equalsIgnoreCase(properties.provider());
        if (useS3) {
            if (properties.s3() == null || properties.s3().bucket() == null || properties.s3().bucket().isBlank()) {
                throw new IllegalStateException("memory.storage.s3.bucket (MEMORY_STORAGE_S3_BUCKET) must be set");
            }
            this.bucket = properties.s3().bucket();
            String region = properties.s3().region();
            this.s3Client = S3Client.builder()
                    .region(region == null || region.isBlank() ? Region.AP_SOUTH_1 : Region.of(region))
                    .build();
            this.audioDirectory = null;
        } else {
            this.bucket = null;
            this.s3Client = null;
            this.audioDirectory = Path.of(properties.audioDirectory()).toAbsolutePath().normalize();
            Files.createDirectories(this.audioDirectory);
        }
    }

    public String store(UUID sessionId, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Audio file must not be empty");
        }
        String extension = extensionOf(file.getOriginalFilename());
        String key = sessionId + extension;
        if (useS3) {
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return S3_PREFIX + bucket + "/" + key;
        }
        Path destination = audioDirectory.resolve(key).normalize();
        if (!destination.startsWith(audioDirectory)) {
            throw new IllegalArgumentException("Invalid audio path");
        }
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        return destination.toString();
    }

    /**
     * Returns a local, readable path for the stored audio. For S3 this downloads to a temp
     * file the caller must release with {@link #releaseRead}; for local storage it returns the
     * permanent file directly and releaseRead is a no-op.
     */
    public Path forRead(String audioUri) throws IOException {
        if (isS3Uri(audioUri)) {
            String key = keyOf(audioUri);
            // getObject(request, Path) creates the file itself (CREATE_NEW) and fails if it
            // already exists, so build a unique path in the temp dir without pre-creating it -
            // unlike Files.createTempFile, which would create it and cause exactly that failure.
            Path tmp = Path.of(System.getProperty("java.io.tmpdir"))
                    .resolve("audio-read-" + UUID.randomUUID() + extensionOf(key));
            s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(), tmp);
            return tmp;
        }
        Path target = validatedLocalPath(audioUri);
        if (!Files.isRegularFile(target)) {
            throw new IllegalArgumentException("Uploaded audio file cannot be found: " + target);
        }
        return target;
    }

    public void releaseRead(String audioUri, Path localPath) throws IOException {
        if (isS3Uri(audioUri)) {
            Files.deleteIfExists(localPath);
        }
    }

    public void delete(String audioUri) throws IOException {
        if (audioUri == null || audioUri.isBlank()) return;
        if (isS3Uri(audioUri)) {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(keyOf(audioUri)).build());
            return;
        }
        Files.deleteIfExists(validatedLocalPath(audioUri));
    }

    private Path validatedLocalPath(String audioUri) {
        Path target = Path.of(audioUri).toAbsolutePath().normalize();
        if (!target.startsWith(audioDirectory)) {
            throw new IllegalArgumentException("Audio path is outside the configured storage directory");
        }
        return target;
    }

    private boolean isS3Uri(String audioUri) {
        return audioUri != null && audioUri.startsWith(S3_PREFIX);
    }

    private String keyOf(String s3Uri) {
        String withoutPrefix = s3Uri.substring(S3_PREFIX.length());
        return withoutPrefix.substring(withoutPrefix.indexOf('/') + 1);
    }

    private String extensionOf(String name) {
        return name != null && name.contains(".") ? name.substring(name.lastIndexOf('.')) : ".bin";
    }
}
