package com.secondmemory.memory;

import java.util.List;
import java.util.UUID;

public record AskMemorySource(
        UUID memoryId,
        UUID sessionId,
        String type,
        String title,
        String content,
        double similarity,
        List<MemorySourceEvidence> evidence
) {}
