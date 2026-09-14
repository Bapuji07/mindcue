package com.secondmemory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "memory.storage")
public record StorageProperties(String audioDirectory) {}
