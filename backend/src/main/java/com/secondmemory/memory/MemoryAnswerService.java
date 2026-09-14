package com.secondmemory.memory;

import com.secondmemory.ai.AiProviderRegistry;
import com.secondmemory.ai.ChatAiProvider;
import com.secondmemory.config.AiProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class MemoryAnswerService {
    private static final Set<String> STOP_WORDS = Set.of(
            "what", "when", "where", "which", "who", "whom", "whose", "why", "how",
            "did", "does", "do", "was", "were", "is", "are", "am", "be", "been",
            "the", "and", "for", "with", "about", "from", "that", "this", "these", "those",
            "have", "has", "had", "would", "could", "should", "will", "can", "into", "your",
            "you", "me", "my", "our", "we", "they", "them", "their", "a", "an", "to", "of", "in", "on"
    );

    private final AiProviderRegistry providers;
    private final AiProperties aiProperties;
    private final MemoryRepository memories;

    public MemoryAnswerService(AiProviderRegistry providers,
                               AiProperties aiProperties,
                               MemoryRepository memories) {
        this.providers = providers;
        this.aiProperties = aiProperties;
        this.memories = memories;
    }

    public AskMemoryResponse ask(AskMemoryRequest request) {
        int topK = request.topK() == null ? 8 : Math.max(1, Math.min(request.topK(), 20));

        // Local MVP currently runs without pgvector. Rank a bounded recent memory set by
        // keyword overlap. The provider-facing answer layer stays unchanged, so vector
        // retrieval can replace this later without changing the API contract.
        List<MemorySearchHit> hits = keywordRetrieve(request, topK);

        if (hits.isEmpty()) {
            return new AskMemoryResponse(
                    "I could not find a stored memory relevant to that question yet.",
                    List.of());
        }

        List<AskMemorySource> sources = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        int number = 1;
        for (MemorySearchHit hit : hits) {
            MemoryRecord memory = hit.memory();
            List<MemorySourceEvidence> evidence = memories.sourcesForMemory(memory.id());
            sources.add(new AskMemorySource(
                    memory.id(), memory.sessionId(), memory.type().name(), memory.title(),
                    memory.content(), hit.similarity(), evidence));

            context.append("MEMORY ").append(number++).append('\n')
                    .append("TYPE: ").append(memory.type()).append('\n')
                    .append("TITLE: ").append(memory.title()).append('\n')
                    .append("CONTENT: ").append(memory.content()).append('\n')
                    .append("OCCURRED_AT: ").append(memory.occurredAt()).append('\n')
                    .append("DUE_AT: ").append(memory.dueAt()).append('\n')
                    .append("RETRIEVAL_SCORE: ").append(hit.similarity()).append("\n\n");
        }

        ChatAiProvider chatProvider = providers.chat(aiProperties.chat().provider());
        String answer = chatProvider.generateText(
                """
                You answer questions using only the supplied personal memories.
                Never invent missing facts. If the memories are incomplete or conflicting, say so.
                Keep the answer concise and useful. Do not claim that something happened unless the memories support it.
                """,
                "Question:\n" + request.question() + "\n\nRetrieved memories:\n" + context
        );

        return new AskMemoryResponse(answer, sources);
    }

    private List<MemorySearchHit> keywordRetrieve(AskMemoryRequest request, int topK) {
        Set<String> terms = tokenize(request.question());
        List<MemoryRecord> candidates = memories.list(request.userId(), null, 500);

        List<MemorySearchHit> scored = candidates.stream()
                .map(memory -> new MemorySearchHit(memory, score(memory, terms)))
                .filter(hit -> terms.isEmpty() || hit.similarity() > 0)
                .sorted(Comparator
                        .comparingDouble(MemorySearchHit::similarity).reversed()
                        .thenComparing(hit -> hit.memory().createdAt(), Comparator.reverseOrder()))
                .limit(topK)
                .toList();

        if (!scored.isEmpty()) {
            return scored;
        }

        return candidates.stream()
                .limit(topK)
                .map(memory -> new MemorySearchHit(memory, 0.0))
                .toList();
    }

    private double score(MemoryRecord memory, Set<String> terms) {
        if (terms.isEmpty()) return 0.0;
        String haystack = ((memory.title() == null ? "" : memory.title()) + " " + memory.content())
                .toLowerCase(Locale.ROOT);
        long matches = terms.stream().filter(haystack::contains).count();
        return (double) matches / terms.size();
    }

    private Set<String> tokenize(String question) {
        Set<String> terms = new HashSet<>();
        for (String token : question.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() >= 3 && !STOP_WORDS.contains(token)) {
                terms.add(token);
            }
        }
        return terms;
    }
}
