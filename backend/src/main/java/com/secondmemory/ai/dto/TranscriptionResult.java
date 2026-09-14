package com.secondmemory.ai.dto;

import java.util.List;

public record TranscriptionResult(
        String text,
        List<TranscriptionSegment> segments
) {}
