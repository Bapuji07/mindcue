package com.secondmemory.memory;

import jakarta.validation.constraints.Size;

public record UpdateMemoryRequest(
        @Size(max = 500) String title,
        String content,
        ResolutionStatus resolutionStatus,
        Boolean active
) {}
