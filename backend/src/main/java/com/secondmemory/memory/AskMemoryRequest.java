package com.secondmemory.memory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AskMemoryRequest(
        @NotNull UUID userId,
        @NotBlank String question,
        Integer topK
) {}
