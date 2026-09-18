package com.typist.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WordTokenizerTest {
    @Test
    void splitsPunctuationAwayFromLexicalWords() {
        List<WordSpan> spans = WordTokenizer.tokenize("hello, world");
        assertEquals(2, spans.size());
        assertEquals("hello", spans.get(0).word());
        assertEquals("world", spans.get(1).word());
        assertEquals(0, spans.get(0).start());
        assertEquals(5, spans.get(0).endExclusive());
        assertEquals(7, spans.get(1).start());
    }

    @Test
    void splitsProgrammingIdentifiersOutOfPunctuation() {
        List<WordSpan> spans = WordTokenizer.tokenize("$result{$user}");
        assertEquals(2, spans.size());
        assertEquals("result", spans.get(0).word());
        assertEquals("user", spans.get(1).word());
    }

    @Test
    void keepsRepeatedOccurrencesSeparate() {
        List<WordSpan> spans = WordTokenizer.tokenize("result then result");
        assertEquals(3, spans.size());
        assertEquals("result", spans.get(0).word());
        assertEquals("then", spans.get(1).word());
        assertEquals("result", spans.get(2).word());
        assertEquals(0, spans.get(0).index());
        assertEquals(2, spans.get(2).index());
    }
}
