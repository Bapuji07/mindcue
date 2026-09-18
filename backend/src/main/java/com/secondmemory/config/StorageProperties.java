package com.secondmemory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "memory.storage")
public record StorageProperties(String provider, String audioDirectory, S3 s3) {
    public record S3(String bucket, String region) {}
}
