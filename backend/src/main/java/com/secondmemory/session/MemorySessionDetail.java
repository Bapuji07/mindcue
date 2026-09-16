package com.secondmemory.session;

import com.secondmemory.memory.MemoryRecord;
import com.secondmemory.transcript.TranscriptChunk;

import java.util.List;

public record MemorySessionDetail(
        MemorySession session,
        List<MemoryRecord> memories,
        List<TranscriptChunk> transcriptChunks
) {}
