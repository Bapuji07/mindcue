package com.secondmemory.transcription;

import com.secondmemory.ai.dto.ExtractedMemoryResponse;
import com.secondmemory.memory.MemoryExtractionService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memory/sessions/{sessionId}")
public class SessionProcessingController {
    private final TranscriptionService transcriptionService;
    private final MemoryExtractionService extractionService;

    public SessionProcessingController(TranscriptionService transcriptionService,
                                       MemoryExtractionService extractionService) {
        this.transcriptionService = transcriptionService;
        this.extractionService = extractionService;
    }

    @PostMapping("/transcribe")
    public TranscriptionResponse transcribe(@PathVariable UUID sessionId) {
        return transcriptionService.transcribe(sessionId);
    }

    @PostMapping("/process")
    public SessionProcessResponse process(@PathVariable UUID sessionId) {
        TranscriptionResponse transcription = transcriptionService.transcribe(sessionId);
        ExtractedMemoryResponse extraction = extractionService.extract(sessionId);
        return new SessionProcessResponse(transcription, extraction);
    }
}
