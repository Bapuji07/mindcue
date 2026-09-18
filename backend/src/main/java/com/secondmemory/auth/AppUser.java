package com.secondmemory.auth;

import java.time.Instant;
import java.util.UUID;

public record AppUser(
        UUID id,
        String username,
        String passwordHash,
        Instant createdAt,
        Instant updatedAt
) {}
