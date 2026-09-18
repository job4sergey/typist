package com.typist.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Records per-occurrence word timings from live key events.
 */
public final class WordTimingCollector {
    private List<WordSpan> spans = List.of();
    private long[] correctMs = new long[0];
    private int[] errors = new int[0];
    private int[] corrections = new int[0];

    public void reset(String text) {
        spans = WordTokenizer.tokenize(text);
        int length = text == null ? 0 : text.length();
        correctMs = new long[length];
        Arrays.fill(correctMs, -1L);
        errors = new int[spans.size()];
        corrections = new int[spans.size()];
    }

    public void onCorrect(int position, long relativeMs) {
        if (position < 0 || position >= correctMs.length) {
            return;
        }
        correctMs[position] = Math.max(0L, relativeMs);
    }

    public void onIncorrect(int position) {
        int word = wordIndexAt(position);
        if (word < 0) {
            word = nearestWord(position);
        }
        if (word >= 0) {
            errors[word]++;
        }
    }

    public void revert(int position) {
        if (position >= 0 && position < correctMs.length) {
            correctMs[position] = -1L;
        }
    }

    public void addCorrection(int position) {
        int word = wordIndexAt(position);
        if (word < 0) {
            word = nearestWord(position);
        }
        if (word >= 0) {
            corrections[word]++;
        }
    }

    public void addCorrectionForWord(int wordIndex) {
        if (wordIndex >= 0 && wordIndex < corrections.length) {
            corrections[wordIndex]++;
        }
    }

    public int wordIndexAt(int position) {
        for (WordSpan span : spans) {
            if (span.contains(position)) {
                return span.index();
            }
        }
        return -1;
    }

    public List<WordOccurrence> snapshot() {
        List<WordOccurrence> occurrences = new ArrayList<>(spans.size());
        List<long[]> bounds = new ArrayList<>(spans.size());
        for (WordSpan span : spans) {
            long start = Long.MAX_VALUE;
            long end = -1L;
            for (int i = span.start(); i < span.endExclusive(); i++) {
                if (correctMs[i] >= 0) {
                    start = Math.min(start, correctMs[i]);
                    end = Math.max(end, correctMs[i]);
                }
            }
            if (end < 0) {
                start = 0;
                end = 0;
            }
            bounds.add(new long[]{start, end});
        }
        for (int i = 0; i < spans.size(); i++) {
            WordSpan span = spans.get(i);
            long start = bounds.get(i)[0];
            long end = bounds.get(i)[1];
            long duration = Math.max(0L, end - start);
            int length = span.length();
            double msPerChar = duration / (double) Math.max(length - 1, 1);
            Long transition = null;
            if (i + 1 < spans.size()) {
                long nextStart = bounds.get(i + 1)[0];
                transition = Math.max(0L, nextStart - end);
            }
            occurrences.add(new WordOccurrence(
                    span.index(),
                    span.word(),
                    span.start(),
                    span.endExclusive(),
                    start,
                    end,
                    duration,
                    length,
                    msPerChar,
                    transition,
                    errors[i],
                    corrections[i]
            ));
        }
        return occurrences;
    }

    private int nearestWord(int position) {
        int previous = -1;
        for (WordSpan span : spans) {
            if (span.endExclusive() <= position) {
                previous = span.index();
            } else if (span.start() > position) {
                return previous >= 0 ? previous : span.index();
            }
        }
        return previous;
    }
}
