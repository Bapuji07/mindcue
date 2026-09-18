package com.secondmemory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "memory.auth")
public record AuthProperties(Jwt jwt, Bootstrap bootstrap) {
    public record Jwt(String secret, long expiryMinutes) {}
    public record Bootstrap(String username, String password) {}
}
