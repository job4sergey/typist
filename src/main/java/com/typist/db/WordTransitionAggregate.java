package com.typist.db;

public record WordTransitionAggregate(
        String fromWord,
        String toWord,
        int occurrences,
        double avgTransitionMs,
        int fromErrors,
        int toErrors,
        int totalErrors,
        double avgFromDurationMs,
        double avgToDurationMs
) {
}
