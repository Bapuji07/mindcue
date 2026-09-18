package com.secondmemory.auth;

import com.secondmemory.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration expiry;

    public JwtService(AuthProperties properties) {
        if (properties.jwt() == null || properties.jwt().secret() == null || properties.jwt().secret().isBlank()) {
            throw new IllegalStateException(
                    "memory.auth.jwt.secret (AUTH_JWT_SECRET) must be set to a long random value");
        }
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.expiry = Duration.ofMinutes(properties.jwt().expiryMinutes());
    }

    public String issue(UUID userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiry)))
                .signWith(key)
                .compact();
    }

    public Optional<UUID> verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
