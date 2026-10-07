package com.secondmemory.memory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskMemoryRequest(
        @NotBlank @Size(max = 1000) String question,
        Integer topK
) {}
