package com.secondmemory.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.secondmemory.ai.AiCapability;
import com.secondmemory.ai.TranscriptionProvider;
import com.secondmemory.ai.dto.TranscriptionResult;
import com.secondmemory.ai.dto.TranscriptionSegment;
import com.secondmemory.config.AiProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class OpenAiCompatibleTranscriptionProvider implements TranscriptionProvider {
    private final AiProperties properties;

    public OpenAiCompatibleTranscriptionProvider(AiProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "openai-compatible";
    }

    @Override
    public Set<AiCapability> capabilities() {
        return Set.of(AiCapability.TRANSCRIPTION);
    }

    @Override
    public TranscriptionResult transcribe(Path audioFile) {
        AiProperties.Transcription config = properties.transcription();
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new IllegalStateException("MEMORY_TRANSCRIPTION_API_KEY is not configured");
        }
        if (!Files.isRegularFile(audioFile)) {
            throw new IllegalArgumentException("Audio file does not exist: " + audioFile);
        }

        RestClient client = RestClient.builder()
                .baseUrl(config.baseUrl())
                .defaultHeader("Authorization", "Bearer " + config.apiKey())
                .build();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(audioFile));
        body.add("model", config.model());

        String model = config.model() == null ? "" : config.model().toLowerCase();
        if (model.contains("diarize")) {
            body.add("response_format", "diarized_json");
            body.add("chunking_strategy", "auto");
        } else if (model.equals("whisper-1")) {
            body.add("response_format", "verbose_json");
        } else {
            body.add("response_format", "json");
        }

        if (config.language() != null && !config.language().isBlank()) {
            body.add("language", config.language());
        }

        JsonNode response = client.post()
                .uri("/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("Transcription provider returned an empty response");
        }

        String text = response.path("text").asText("").trim();
        List<TranscriptionSegment> segments = parseSegments(response.path("segments"));
        if (text.isBlank() && segments.isEmpty()) {
            throw new IllegalStateException("Transcription provider returned no transcript text");
        }
        if (text.isBlank()) {
            text = segments.stream().map(TranscriptionSegment::text).reduce("", (a, b) -> a.isBlank() ? b : a + " " + b);
        }
        return new TranscriptionResult(text, segments);
    }

    private List<TranscriptionSegment> parseSegments(JsonNode segmentNode) {
        if (!segmentNode.isArray()) {
            return List.of();
        }
        List<TranscriptionSegment> segments = new ArrayList<>();
        for (JsonNode segment : segmentNode) {
            String text = segment.path("text").asText("").trim();
            if (text.isBlank()) {
                continue;
            }
            Long startMs = secondsToMs(segment.get("start"));
            Long endMs = secondsToMs(segment.get("end"));
            String speaker = segment.hasNonNull("speaker") ? segment.get("speaker").asText() : null;
            BigDecimal confidence = null;
            segments.add(new TranscriptionSegment(startMs, endMs, speaker, text, confidence));
        }
        return segments;
    }

    private Long secondsToMs(JsonNode value) {
        if (value == null || !value.isNumber()) {
            return null;
        }
        return Math.round(value.asDouble() * 1000.0);
    }
}
