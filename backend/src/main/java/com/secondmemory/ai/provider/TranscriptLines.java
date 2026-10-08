package com.secondmemory.ai.provider;

import com.secondmemory.ai.dto.TranscriptionSegment;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a speaker-labelled transcript ("Speaker 1: …" per turn) into segments. Consecutive turns by
 * the same speaker are merged and lines without a label continue the previous turn. Text without
 * any labels stays a single unlabelled segment, exactly as before speaker labels existed.
 */
final class TranscriptLines {
    // "Speaker 2:" or a capitalised name of up to three words ("Rahul:", "Dr. Mehta:"), optionally in
    // markdown bold. Requiring a capital letter keeps timestamps ("10:30") and ordinary words out.
    private static final Pattern TURN = Pattern.compile(
            "^\\s*\\**\\s*((?i:speaker)\\s*\\d{1,2}|\\p{Lu}[\\p{L}'.-]*(?:\\s+\\p{Lu}[\\p{L}'.-]*){0,2})\\s*\\**\\s*:\\s*\\**\\s*(.*)$");
    private static final Pattern NUMBERED_SPEAKER = Pattern.compile("(?i)speaker\\s*(\\d{1,2})");
    private static final int MAX_LABEL_LENGTH = 40;

    private TranscriptLines() {}

    static List<TranscriptionSegment> parse(String transcript) {
        List<String> labels = new ArrayList<>();
        List<StringBuilder> texts = new ArrayList<>();
        boolean labelled = false;
        for (String line : transcript.split("\\R")) {
            if (line.isBlank()) continue;
            Matcher turn = TURN.matcher(line);
            if (turn.matches() && turn.group(1).length() <= MAX_LABEL_LENGTH) {
                labelled = true;
                String label = normalize(turn.group(1));
                String text = turn.group(2).trim();
                if (!labels.isEmpty() && label.equals(labels.get(labels.size() - 1))) {
                    append(texts.get(texts.size() - 1), text);
                } else {
                    labels.add(label);
                    texts.add(new StringBuilder(text));
                }
            } else if (texts.isEmpty()) {
                labels.add(null);
                texts.add(new StringBuilder(line.trim()));
            } else {
                append(texts.get(texts.size() - 1), line.trim());
            }
        }
        if (!labelled) {
            return List.of(new TranscriptionSegment(null, null, null, transcript.trim(), null));
        }
        List<TranscriptionSegment> segments = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            String text = texts.get(i).toString().trim();
            if (!text.isEmpty()) segments.add(new TranscriptionSegment(null, null, labels.get(i), text, null));
        }
        return segments;
    }

    /** "speaker 2" / "SPEAKER2" -> "Speaker 2"; names are kept as written. */
    private static String normalize(String label) {
        Matcher numbered = NUMBERED_SPEAKER.matcher(label.trim());
        return numbered.matches() ? "Speaker " + Integer.parseInt(numbered.group(1)) : label.trim().replaceAll("\\s+", " ");
    }

    private static void append(StringBuilder text, String more) {
        if (more.isEmpty()) return;
        if (!text.isEmpty()) text.append(' ');
        text.append(more);
    }
}
