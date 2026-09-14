package com.secondmemory.ai.dto;

import java.util.List;

public record ExtractedMemoryResponse(String summary, List<ExtractedMemory> memories) {}
