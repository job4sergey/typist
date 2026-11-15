package com.typist.db;

public record WordErrorAggregate(
        String word,
        int occurrences,
        int errorOccurrences,
        int totalErrors,
        int totalCorrections,
        double avgDurationMs,
        double avgMsPerChar,
        double avgLength
) {
}
