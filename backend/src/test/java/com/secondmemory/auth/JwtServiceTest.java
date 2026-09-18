package com.secondmemory.auth;

import com.secondmemory.config.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {
    JwtService service;

    @BeforeEach void setup() {
        service = new JwtService(new AuthProperties(
                new AuthProperties.Jwt("test-secret-value-that-is-long-enough-for-hmac-sha256", 30),
                new AuthProperties.Bootstrap(null, null)));
    }

    @Test void issuedTokenVerifiesBackToTheSameUserId() {
        UUID userId = UUID.randomUUID();
        String token = service.issue(userId, "alice");
        assertThat(service.verify(token)).contains(userId);
    }

    @Test void tamperedTokenIsRejected() {
        String token = service.issue(UUID.randomUUID(), "alice");
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");
        assertThat(service.verify(tampered)).isEmpty();
    }

    @Test void expiredTokenIsRejected() {
        JwtService alreadyExpired = new JwtService(new AuthProperties(
                new AuthProperties.Jwt("test-secret-value-that-is-long-enough-for-hmac-sha256", -5),
                new AuthProperties.Bootstrap(null, null)));
        String token = alreadyExpired.issue(UUID.randomUUID(), "alice");
        assertThat(alreadyExpired.verify(token)).isEmpty();
    }

    @Test void garbageTokenIsRejected() {
        assertThat(service.verify("not-a-jwt")).isEmpty();
    }
}
