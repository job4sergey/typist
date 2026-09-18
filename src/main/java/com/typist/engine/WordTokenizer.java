package com.typist.engine;

import java.util.ArrayList;
import java.util.List;

public final class WordTokenizer {
    private WordTokenizer() {
    }

    /**
     * Splits text into lexical words: letters, digits, and underscores.
     * Punctuation and spaces are separators (so {@code hello, world} and
     * {@code $result{$user}} yield independent word occurrences).
     */
    public static List<WordSpan> tokenize(String text) {
        List<WordSpan> spans = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return spans;
        }
        int i = 0;
        int index = 0;
        while (i < text.length()) {
            if (isWordChar(text.charAt(i))) {
                int start = i;
                i++;
                while (i < text.length() && isWordChar(text.charAt(i))) {
                    i++;
                }
                spans.add(new WordSpan(index++, text.substring(start, i), start, i));
            } else {
                i++;
            }
        }
        return spans;
    }

    public static boolean isWordChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }
}
