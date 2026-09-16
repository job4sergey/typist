package com.typist.engine;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Character-by-character typing state. Wrong keys mark the expected letter
 * as an error and still advance (Monkeytype-style). Backspace rewinds.
 */
public final class TypingEngine {
    private String text = "";
    private CharState[] states = new CharState[0];
    private int caret;
    private long startedAtNanos;
    private long finishedAtNanos;
    private int errorEvents;
    private int correctKeystrokes;
    private final Map<Character, Integer> letterFailures = new HashMap<>();
    private final Map<TypedBigram, Integer> bigramFailures = new HashMap<>();

    public void load(String source) {
        text = source == null ? "" : source.replace("\r\n", "\n").replace('\r', '\n');
        reset();
    }

    public void reset() {
        states = new CharState[text.length()];
        Arrays.fill(states, CharState.PENDING);
        caret = 0;
        startedAtNanos = 0;
        finishedAtNanos = 0;
        errorEvents = 0;
        correctKeystrokes = 0;
        letterFailures.clear();
        bigramFailures.clear();
    }

    public String getText() {
        return text;
    }

    public int getCaret() {
        return caret;
    }

    public CharState stateAt(int index) {
        return states[index];
    }

    public boolean isStarted() {
        return startedAtNanos > 0;
    }

    public boolean isFinished() {
        return finishedAtNanos > 0;
    }

    public int getErrorEvents() {
        return errorEvents;
    }

    public Map<Character, Integer> getLetterFailures() {
        return Collections.unmodifiableMap(letterFailures);
    }

    public Map<TypedBigram, Integer> getBigramFailures() {
        return Collections.unmodifiableMap(bigramFailures);
    }

    public int getCorrectCount() {
        int n = 0;
        for (CharState state : states) {
            if (state == CharState.CORRECT) {
                n++;
            }
        }
        return n;
    }

    public int getTypedCount() {
        int n = 0;
        for (CharState state : states) {
            if (state != CharState.PENDING) {
                n++;
            }
        }
        return n;
    }

    public double accuracyPercent() {
        int total = correctKeystrokes + errorEvents;
        if (total == 0) {
            return 100.0;
        }
        return 100.0 * correctKeystrokes / total;
    }

    public long elapsedMillis() {
        if (startedAtNanos == 0) {
            return 0;
        }
        long end = finishedAtNanos > 0 ? finishedAtNanos : System.nanoTime();
        return Math.max(0, (end - startedAtNanos) / 1_000_000L);
    }

    public double wordsPerMinute() {
        long ms = elapsedMillis();
        if (ms <= 0) {
            return 0;
        }
        return (getCorrectCount() / 5.0) / (ms / 60_000.0);
    }

    public Character worstLetter() {
        Character worst = null;
        int max = 0;
        for (Map.Entry<Character, Integer> entry : letterFailures.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                worst = entry.getKey();
            }
        }
        return worst;
    }

    public TypedBigram worstBigram() {
        TypedBigram worst = null;
        int max = 0;
        for (Map.Entry<TypedBigram, Integer> entry : bigramFailures.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                worst = entry.getKey();
            }
        }
        return worst;
    }

    /**
     * @return true if the display should refresh
     */
    public boolean handleKey(char ch, int keyCode, boolean isTypedChar) {
        return handleKey(ch, keyCode, 0, isTypedChar);
    }

    public boolean handleKey(char ch, int keyCode, int modifiersEx, boolean isTypedChar) {
        if (text.isEmpty()) {
            return false;
        }
        if (keyCode == java.awt.event.KeyEvent.VK_ESCAPE) {
            reset();
            return true;
        }
        if (keyCode == java.awt.event.KeyEvent.VK_BACK_SPACE) {
            boolean ctrl = (modifiersEx & java.awt.event.InputEvent.CTRL_DOWN_MASK) != 0;
            return ctrl ? backspaceWord() : backspace();
        }
        if (isFinished() || !isTypedChar || Character.isISOControl(ch)) {
            return false;
        }
        if (caret >= text.length()) {
            return false;
        }
        markStart();
        char expected = text.charAt(caret);
        if (ch == expected) {
            states[caret] = CharState.CORRECT;
            correctKeystrokes++;
        } else {
            states[caret] = CharState.ERROR;
            errorEvents++;
            letterFailures.merge(Character.toLowerCase(expected), 1, Integer::sum);
            if (caret > 0) {
                char previous = Character.toLowerCase(text.charAt(caret - 1));
                char current = Character.toLowerCase(expected);
                bigramFailures.merge(new TypedBigram(previous, current), 1, Integer::sum);
            }
        }
        caret++;
        if (caret >= text.length()) {
            finishedAtNanos = System.nanoTime();
        }
        return true;
    }

    private boolean backspace() {
        if (caret <= 0) {
            return false;
        }
        caret--;
        states[caret] = CharState.PENDING;
        finishedAtNanos = 0;
        return true;
    }

    /**
     * Rewind to the start of the word currently being typed. If the caret sits
     * on the space after a finished word, that previous word is treated as current.
     */
    private boolean backspaceWord() {
        int target = wordStart(caret);
        if (target >= caret) {
            return false;
        }
        while (caret > target) {
            caret--;
            states[caret] = CharState.PENDING;
        }
        finishedAtNanos = 0;
        return true;
    }

    int wordStart(int from) {
        if (from <= 0 || text.isEmpty()) {
            return 0;
        }
        int i = Math.min(from, text.length());
        while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) {
            i--;
        }
        while (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    private void markStart() {
        if (startedAtNanos == 0) {
            startedAtNanos = System.nanoTime();
        }
    }
}
