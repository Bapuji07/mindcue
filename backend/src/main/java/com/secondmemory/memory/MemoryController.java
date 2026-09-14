package com.secondmemory.memory;

import com.secondmemory.ai.dto.ExtractedMemoryResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memory")
public class MemoryController {
    private final MemoryRepository repository;
    private final MemoryExtractionService extractionService;
    private final MemoryAnswerService answerService;

    public MemoryController(MemoryRepository repository, MemoryExtractionService extractionService, MemoryAnswerService answerService) {
        this.repository = repository;
        this.extractionService = extractionService;
        this.answerService = answerService;
    }

    @GetMapping("/memories")
    public List<MemoryRecord> list(@RequestParam UUID userId,
                                   @RequestParam(required = false) MemoryType type,
                                   @RequestParam(defaultValue = "100") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return repository.list(userId, type, safeLimit);
    }

    @PostMapping("/ask")
    public AskMemoryResponse ask(@jakarta.validation.Valid @RequestBody AskMemoryRequest request) {
        return answerService.ask(request);
    }

    @PostMapping("/sessions/{sessionId}/extract")
    public ExtractedMemoryResponse extract(@PathVariable UUID sessionId) {
        return extractionService.extract(sessionId);
    }
}
