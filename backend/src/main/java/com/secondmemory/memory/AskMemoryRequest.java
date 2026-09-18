package com.secondmemory.memory;

import jakarta.validation.constraints.NotBlank;

public record AskMemoryRequest(
        @NotBlank String question,
        Integer topK
) {}
