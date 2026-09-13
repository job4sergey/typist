package com.typist.db;

import java.time.Instant;

public record SessionRecord(
        long id,
        Instant startedAt,
        Instant finishedAt,
        String textFile,
        int charsTyped,
        int correctChars,
        int errorEvents,
        double wpm,
        double accuracy,
        long durationMs
) {
}
