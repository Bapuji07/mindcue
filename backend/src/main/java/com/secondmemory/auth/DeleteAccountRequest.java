package com.secondmemory.auth;

import jakarta.validation.constraints.NotBlank;

/** The account password, re-entered to confirm a permanent account deletion. */
public record DeleteAccountRequest(@NotBlank String password) {}
