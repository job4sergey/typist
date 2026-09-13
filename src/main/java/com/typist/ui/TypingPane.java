package com.typist.ui;

import com.typist.engine.CharState;
import com.typist.engine.TypingEngine;

import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public final class TypingPane extends JTextPane {
    private final TypingEngine engine;
    private final Runnable onChange;

    public TypingPane(TypingEngine engine, Runnable onChange) {
        this.engine = engine;
        this.onChange = onChange;
        setEditable(false);
        setFocusable(true);
        setOpaque(true);
        setBackground(Theme.BACKGROUND);
        setCaretColor(Theme.CARET);
        setBorder(null);
        setMargin(new Insets(16, 16, 16, 16));
        setFont(new Font(Font.MONOSPACED, Font.PLAIN, 26));
        setHighlighter(null);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ESCAPE
                        || event.getKeyCode() == KeyEvent.VK_BACK_SPACE
                        || event.getKeyCode() == KeyEvent.VK_TAB) {
                    boolean changed = engine.handleKey(
                            event.getKeyChar(), event.getKeyCode(), event.getModifiersEx(), false);
                    event.consume();
                    if (changed) {
                        refresh();
                        onChange.run();
                    }
                }
            }

            @Override
            public void keyTyped(KeyEvent event) {
                char ch = event.getKeyChar();
                if (ch == KeyEvent.VK_ESCAPE || ch == KeyEvent.VK_BACK_SPACE || ch == '\n') {
                    event.consume();
                    return;
                }
                boolean changed = engine.handleKey(ch, event.getKeyCode(), true);
                if (changed) {
                    event.consume();
                    refresh();
                    onChange.run();
                }
            }
        });
    }

    public void refresh() {
        String text = engine.getText();
        StyledDocument document = getStyledDocument();
        try {
            document.remove(0, document.getLength());
            if (text.isEmpty()) {
                return;
            }
            for (int i = 0; i < text.length(); i++) {
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setFontFamily(attrs, Font.MONOSPACED);
                StyleConstants.setFontSize(attrs, 26);
                CharState state = engine.stateAt(i);
                if (i == engine.getCaret() && !engine.isFinished()) {
                    StyleConstants.setUnderline(attrs, true);
                    StyleConstants.setForeground(attrs, Theme.CARET);
                } else if (state == CharState.CORRECT) {
                    StyleConstants.setForeground(attrs, Theme.TEXT_CORRECT);
                } else if (state == CharState.ERROR) {
                    StyleConstants.setForeground(attrs, Theme.TEXT_ERROR);
                    if (text.charAt(i) == ' ') {
                        StyleConstants.setBackground(attrs, Theme.ERROR_SPACE_BACKGROUND);
                    }
                } else {
                    StyleConstants.setForeground(attrs, Theme.TEXT_PENDING);
                }
                document.insertString(document.getLength(), String.valueOf(text.charAt(i)), attrs);
            }
        } catch (BadLocationException ex) {
            throw new IllegalStateException(ex);
        }
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }
}
