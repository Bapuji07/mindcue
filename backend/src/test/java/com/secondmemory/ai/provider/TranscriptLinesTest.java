package com.secondmemory.ai.provider;

import com.secondmemory.ai.dto.TranscriptionSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptLinesTest {
    private static List<String> speakers(List<TranscriptionSegment> segments) {
        return segments.stream().map(TranscriptionSegment::speakerLabel).toList();
    }

    private static List<String> texts(List<TranscriptionSegment> segments) {
        return segments.stream().map(TranscriptionSegment::text).toList();
    }

    @Test
    void splitsTurnsAndMergesConsecutiveTurnsOfTheSameSpeaker() {
        var segments = TranscriptLines.parse("""
                Speaker 1: Can you send the invoice?
                Speaker 2: Sure.
                Speaker 2: I'll do it by Friday.
                Speaker 1: Thanks.
                """);
        assertThat(speakers(segments)).containsExactly("Speaker 1", "Speaker 2", "Speaker 1");
        assertThat(texts(segments)).containsExactly("Can you send the invoice?", "Sure. I'll do it by Friday.", "Thanks.");
    }

    @Test
    void unlabelledLinesContinueThePreviousTurn() {
        var segments = TranscriptLines.parse("Speaker 1: First part\nand the rest of it\nSpeaker 2: Reply");
        assertThat(texts(segments)).containsExactly("First part and the rest of it", "Reply");
    }

    @Test
    void acceptsNamesBoldMarkdownAndOddCapitalisation() {
        var segments = TranscriptLines.parse("**Rahul:** Hello\n**speaker 2**: Hi Rahul\nDr. Mehta: Good morning");
        assertThat(speakers(segments)).containsExactly("Rahul", "Speaker 2", "Dr. Mehta");
        assertThat(texts(segments)).containsExactly("Hello", "Hi Rahul", "Good morning");
    }

    @Test
    void textWithoutLabelsStaysOneUnlabelledSegment() {
        String transcript = "I promised Ravi\nto check the discount tomorrow.";
        var segments = TranscriptLines.parse(transcript);
        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).speakerLabel()).isNull();
        assertThat(segments.get(0).text()).isEqualTo(transcript);
    }

    @Test
    void timesAndLowercaseWordsAreNotSpeakers() {
        var segments = TranscriptLines.parse("Speaker 1: The meeting is at\n10:30 tomorrow\nnote: bring the files");
        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).isEqualTo("The meeting is at 10:30 tomorrow note: bring the files");
    }

    @Test
    void textBeforeTheFirstLabelIsKept() {
        var segments = TranscriptLines.parse("[inaudible]\nSpeaker 1: Hello");
        assertThat(speakers(segments)).containsExactly(null, "Speaker 1");
        assertThat(texts(segments)).containsExactly("[inaudible]", "Hello");
    }
}
