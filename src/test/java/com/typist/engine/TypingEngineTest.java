package com.typist.engine;

import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypingEngineTest {
    @Test
    void colorsProgressAndCountsLetterFailures() {
        TypingEngine engine = new TypingEngine();
        engine.load("ab");

        engine.handleKey('a', KeyEvent.VK_A, true);
        assertEquals(CharState.CORRECT, engine.stateAt(0));
        assertEquals(1, engine.getCaret());

        engine.handleKey('x', KeyEvent.VK_X, true);
        assertEquals(CharState.ERROR, engine.stateAt(1));
        assertEquals(1, engine.getLetterFailures().get('b'));
        assertEquals(1, engine.getBigramFailures().get(new TypedBigram('a', 'b')));
        assertTrue(engine.isFinished());
        assertEquals(50.0, engine.accuracyPercent(), 0.01);
    }

    @Test
    void accuracyUsesCorrectOverCorrectPlusIncorrectKeystrokes() {
        TypingEngine engine = new TypingEngine();
        engine.load("ab");
        engine.handleKey('x', KeyEvent.VK_X, true);
        engine.handleKey('\b', KeyEvent.VK_BACK_SPACE, false);
        engine.handleKey('a', KeyEvent.VK_A, true);
        engine.handleKey('b', KeyEvent.VK_B, true);
        assertEquals(CharState.CORRECT, engine.stateAt(0));
        assertEquals(CharState.CORRECT, engine.stateAt(1));
        assertEquals(200.0 / 3.0, engine.accuracyPercent(), 0.01);
    }

    @Test
    void firstCharacterErrorDoesNotRecordABigram() {
        TypingEngine engine = new TypingEngine();
        engine.load("ab");
        engine.handleKey('x', KeyEvent.VK_X, true);
        assertEquals(1, engine.getLetterFailures().get('a'));
        assertTrue(engine.getBigramFailures().isEmpty());
    }

    @Test
    void escapeRestartsWithoutKeepingProgress() {
        TypingEngine engine = new TypingEngine();
        engine.load("hi");
        engine.handleKey('h', KeyEvent.VK_H, true);
        engine.handleKey((char) 27, KeyEvent.VK_ESCAPE, false);

        assertEquals(0, engine.getCaret());
        assertEquals(CharState.PENDING, engine.stateAt(0));
        assertFalse(engine.isStarted());
        assertTrue(engine.getLetterFailures().isEmpty());
    }

    @Test
    void backspaceRewindsACharacter() {
        TypingEngine engine = new TypingEngine();
        engine.load("z");
        engine.handleKey('z', KeyEvent.VK_Z, true);
        engine.handleKey('\b', KeyEvent.VK_BACK_SPACE, false);
        assertEquals(0, engine.getCaret());
        assertEquals(CharState.PENDING, engine.stateAt(0));
        assertFalse(engine.isFinished());
    }

    @Test
    void ctrlBackspaceReturnsToCurrentWordStart() {
        TypingEngine engine = new TypingEngine();
        engine.load("one two");
        for (char ch : "one tw".toCharArray()) {
            engine.handleKey(ch, KeyEvent.VK_UNDEFINED, true);
        }
        assertEquals(6, engine.getCaret());

        engine.handleKey('\b', KeyEvent.VK_BACK_SPACE, KeyEvent.CTRL_DOWN_MASK, false);
        assertEquals(4, engine.getCaret());
        assertEquals(CharState.PENDING, engine.stateAt(4));
        assertEquals(CharState.PENDING, engine.stateAt(5));
        assertEquals(CharState.CORRECT, engine.stateAt(3));
    }

    @Test
    void ctrlBackspaceFromAfterSpaceClearsTheJustFinishedWord() {
        TypingEngine engine = new TypingEngine();
        engine.load("one two");
        for (char ch : "one ".toCharArray()) {
            engine.handleKey(ch, KeyEvent.VK_UNDEFINED, true);
        }
        engine.handleKey('\b', KeyEvent.VK_BACK_SPACE, KeyEvent.CTRL_DOWN_MASK, false);
        assertEquals(0, engine.getCaret());
        assertEquals(CharState.PENDING, engine.stateAt(0));
    }

    @Test
    void recordsPerOccurrenceWordTimingsFromKeyTimestamps() {
        TypingEngine engine = new TypingEngine();
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(1_000_000_000L);
        engine.setClock(clock::get);
        engine.load("hi to");

        typeAfter(engine, clock, 'h', 0);
        typeAfter(engine, clock, 'i', 200);
        typeAfter(engine, clock, ' ', 80);
        typeAfter(engine, clock, 'x', 50);
        engine.handleKey('\b', KeyEvent.VK_BACK_SPACE, false);
        typeAfter(engine, clock, 't', 40);
        typeAfter(engine, clock, 'o', 100);

        java.util.List<WordOccurrence> words = engine.wordOccurrences();
        assertEquals(2, words.size());
        WordOccurrence hi = words.get(0);
        assertEquals("hi", hi.word());
        assertEquals(0, hi.startMs());
        assertEquals(200, hi.endMs());
        assertEquals(200, hi.durationMs());
        assertEquals(200.0, hi.msPerChar(), 0.01);
        assertEquals(170L, hi.transitionMs());
        assertEquals(0, hi.errors());

        WordOccurrence to = words.get(1);
        assertEquals("to", to.word());
        assertEquals(1, to.errors());
        assertEquals(1, to.corrections());
        assertEquals(null, to.transitionMs());
        assertEquals(370, to.startMs());
        assertEquals(470, to.endMs());
    }

    private static void typeAfter(TypingEngine engine, java.util.concurrent.atomic.AtomicLong clock, char ch, long delayMs) {
        if (delayMs > 0) {
            clock.addAndGet(delayMs * 1_000_000L);
        }
        engine.handleKey(ch, KeyEvent.VK_UNDEFINED, true);
    }
}
