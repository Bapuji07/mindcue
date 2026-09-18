package com.secondmemory.auth;

import java.util.UUID;

public record LoginResponse(String token, UUID userId, String username) {}
