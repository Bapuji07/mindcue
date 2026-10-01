package com.secondmemory.memory;

import java.util.List;

public record MemoryDetail(MemoryRecord memory, List<MemorySourceEvidence> sources) {}
