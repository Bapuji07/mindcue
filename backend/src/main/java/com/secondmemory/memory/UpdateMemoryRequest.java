package com.secondmemory.memory;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record UpdateMemoryRequest(
        @Size(max = 500) String title,
        String content,
        ResolutionStatus resolutionStatus,
        Boolean active,
        Instant dueAt,
        Boolean clearDueAt,
        @DecimalMin("0.00") @DecimalMax("1.00") BigDecimal importance
) {}
