package com.typist.engine;

public record WordSpan(int index, String word, int start, int endExclusive) {
    public int length() {
        return endExclusive - start;
    }

    public boolean contains(int position) {
        return position >= start && position < endExclusive;
    }
}
