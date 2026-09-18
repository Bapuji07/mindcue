package com.secondmemory.memory;

import com.secondmemory.ai.dto.ExtractedMemoryResponse;
import com.secondmemory.auth.CurrentUser;
import com.secondmemory.common.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
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
    public List<MemoryRecord> list(@RequestParam(required = false) MemoryType type,
                                   @RequestParam(defaultValue = "100") int limit,
                                   Authentication auth) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return repository.list(CurrentUser.id(auth), type, safeLimit);
    }

    @PostMapping("/ask")
    public AskMemoryResponse ask(@Valid @RequestBody AskMemoryRequest request, Authentication auth) {
        return answerService.ask(request, CurrentUser.id(auth));
    }

    @PatchMapping("/memories/{id}")
    public MemoryRecord update(@PathVariable UUID id,
                               @Valid @RequestBody UpdateMemoryRequest request,
                               Authentication auth) {
        UUID userId = CurrentUser.id(auth);
        repository.findByIdAndUser(id, userId)
                .orElseThrow(() -> new NotFoundException("Memory not found: " + id));
        repository.update(id, userId, request);
        return repository.findByIdAndUser(id, userId).orElseThrow();
    }

    @PostMapping("/sessions/{sessionId}/extract")
    public ExtractedMemoryResponse extract(@PathVariable UUID sessionId, Authentication auth) {
        return extractionService.extract(sessionId, CurrentUser.id(auth));
    }
}
