package com.typist.db;

import com.typist.engine.WordOccurrence;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

public final class SessionWordsJson {
    private static final DateTimeFormatter OFFSET =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private SessionWordsJson() {
    }

    public static String render(
            SessionRecord session,
            List<LetterFailure> letterFailures,
            List<BigramFailure> bigramFailures,
            List<WordOccurrence> words
    ) {
        StringBuilder json = new StringBuilder(1024);
        json.append("{\n");
        field(json, "session_id", session.id(), true);
        field(json, "text", session.textFile(), true);
        field(json, "started_at", formatTime(session.startedAt()), true);
        field(json, "finished_at", formatTime(session.finishedAt()), true);
        field(json, "duration_ms", session.durationMs(), true);
        field(json, "wpm", session.wpm(), true);
        field(json, "accuracy", session.accuracy(), true);
        field(json, "total_errors", session.errorEvents(), true);
        field(json, "chars_typed", session.charsTyped(), true);
        field(json, "correct_chars", session.correctChars(), true);
        json.append("\n");
        writeLetterFailures(json, letterFailures);
        writeBigramFailures(json, bigramFailures);
        writeWords(json, words);
        json.append("}\n");
        return json.toString();
    }

    private static void writeLetterFailures(StringBuilder json, List<LetterFailure> failures) {
        json.append("  \"letter_failures\": [\n");
        for (int i = 0; i < failures.size(); i++) {
            LetterFailure failure = failures.get(i);
            json.append("    {\n");
            field(json, "letter", formatLetter(failure.letter()), true, 6);
            field(json, "fail_count", failure.failCount(), false, 6);
            json.append("    }");
            if (i + 1 < failures.size()) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("  ],\n");
    }

    private static void writeBigramFailures(StringBuilder json, List<BigramFailure> failures) {
        json.append("  \"bigram_failures\": [\n");
        for (int i = 0; i < failures.size(); i++) {
            BigramFailure failure = failures.get(i);
            json.append("    {\n");
            field(json, "previous", formatLetter(failure.previous()), true, 6);
            field(json, "current", formatLetter(failure.current()), true, 6);
            field(json, "bigram", formatLetter(failure.previous()) + " → " + formatLetter(failure.current()), true, 6);
            field(json, "fail_count", failure.failCount(), false, 6);
            json.append("    }");
            if (i + 1 < failures.size()) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("  ],\n");
    }

    private static void writeWords(StringBuilder json, List<WordOccurrence> words) {
        json.append("  \"words\": [\n");
        for (int i = 0; i < words.size(); i++) {
            WordOccurrence word = words.get(i);
            json.append("    {\n");
            field(json, "index", word.index(), true, 6);
            field(json, "word", word.word(), true, 6);
            field(json, "start_offset", word.startOffset(), true, 6);
            field(json, "end_offset", word.endOffset(), true, 6);
            field(json, "start_ms", word.startMs(), true, 6);
            field(json, "end_ms", word.endMs(), true, 6);
            field(json, "duration_ms", word.durationMs(), true, 6);
            field(json, "length", word.length(), true, 6);
            field(json, "ms_per_char", word.msPerChar(), true, 6);
            if (word.transitionMs() == null) {
                json.append("      \"transition_ms\": null,\n");
            } else {
                field(json, "transition_ms", word.transitionMs(), true, 6);
            }
            field(json, "errors", word.errors(), true, 6);
            field(json, "corrections", word.corrections(), false, 6);
            json.append("    }");
            if (i + 1 < words.size()) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("  ]\n");
    }

    private static String formatTime(Instant instant) {
        return OFFSET.format(instant.atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS));
    }

    static String formatLetter(char letter) {
        if (letter == ' ') {
            return "space";
        }
        if (letter == '\n') {
            return "newline";
        }
        return String.valueOf(letter);
    }

    private static void field(StringBuilder json, String name, long value, boolean comma) {
        field(json, name, value, comma, 2);
    }

    private static void field(StringBuilder json, String name, long value, boolean comma, int indent) {
        indent(json, indent);
        json.append('"').append(name).append("\": ").append(value);
        json.append(comma ? ",\n" : "\n");
    }

    private static void field(StringBuilder json, String name, double value, boolean comma) {
        field(json, name, value, comma, 2);
    }

    private static void field(StringBuilder json, String name, double value, boolean comma, int indent) {
        indent(json, indent);
        json.append('"').append(name).append("\": ").append(String.format(Locale.US, "%.1f", value));
        json.append(comma ? ",\n" : "\n");
    }

    private static void field(StringBuilder json, String name, String value, boolean comma) {
        field(json, name, value, comma, 2);
    }

    private static void field(StringBuilder json, String name, String value, boolean comma, int indent) {
        indent(json, indent);
        json.append('"').append(name).append("\": \"").append(escape(value)).append('"');
        json.append(comma ? ",\n" : "\n");
    }

    private static void indent(StringBuilder json, int spaces) {
        json.append(" ".repeat(spaces));
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
