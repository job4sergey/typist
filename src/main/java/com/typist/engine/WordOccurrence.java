package com.typist.engine;

public record WordOccurrence(
        int index,
        String word,
        int startOffset,
        int endOffset,
        long startMs,
        long endMs,
        long durationMs,
        int length,
        double msPerChar,
        Long transitionMs,
        int errors,
        int corrections
) {
}
