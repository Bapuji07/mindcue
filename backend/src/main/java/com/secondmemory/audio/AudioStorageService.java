package com.secondmemory.audio;

import com.secondmemory.config.StorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class AudioStorageService {
    private final Path audioDirectory;

    public AudioStorageService(StorageProperties properties) throws IOException {
        this.audioDirectory = Path.of(properties.audioDirectory()).toAbsolutePath().normalize();
        Files.createDirectories(this.audioDirectory);
    }

    public String store(UUID sessionId, MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Audio file must not be empty");
        }
        String original = file.getOriginalFilename();
        String extension = original != null && original.contains(".")
                ? original.substring(original.lastIndexOf('.'))
                : ".bin";
        Path destination = audioDirectory.resolve(sessionId + extension).normalize();
        if (!destination.startsWith(audioDirectory)) {
            throw new IllegalArgumentException("Invalid audio path");
        }
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
        return destination.toString();
    }

    public void delete(String audioUri) throws IOException {
        if (audioUri == null || audioUri.isBlank()) return;
        Path target = Path.of(audioUri).toAbsolutePath().normalize();
        if (!target.startsWith(audioDirectory)) {
            throw new IllegalArgumentException("Audio path is outside the configured storage directory");
        }
        Files.deleteIfExists(target);
    }
}
