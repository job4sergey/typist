package com.typist.ui;

import com.typist.db.StatsRepository;
import com.typist.engine.TypedBigram;
import com.typist.engine.TypingEngine;
import com.typist.io.TextLibrary;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

public final class MainFrame extends JFrame {
    private final TextLibrary texts;
    private final StatsRepository stats;
    private final TypingEngine engine = new TypingEngine();
    private final DefaultListModel<String> textNames = new DefaultListModel<>();
    private final JList<String> textList = new JList<>(textNames);
    private final JLabel liveStats = new JLabel(" ", SwingConstants.CENTER);
    private final TypingPane typingPane;
    private final StatsPanel statsPanel;
    private final Path exportsDir;
    private List<Path> textFiles = List.of();
    private String currentFileName = "";
    private boolean pendingRecord;
    private boolean suppressTextLoad;

    public MainFrame(TextLibrary texts, StatsRepository stats) {
        this(texts, stats, Path.of("exports"));
    }

    public MainFrame(TextLibrary texts, StatsRepository stats, Path exportsDir) {
        super("Typist");
        this.texts = texts;
        this.stats = stats;
        this.exportsDir = exportsDir;
        this.typingPane = new TypingPane(engine, this::onEngineChanged);
        this.statsPanel = new StatsPanel(stats, exportsDir);

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setBackground(Theme.BACKGROUND);
        setMinimumSize(new Dimension(980, 640));

        textList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        textList.setBackground(Theme.SURFACE);
        textList.setForeground(Theme.TEXT_CORRECT);
        textList.setSelectionBackground(Theme.CARET);
        textList.setSelectionForeground(java.awt.Color.BLACK);
        textList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        textList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !suppressTextLoad) {
                loadSelectedText();
            }
        });

        JScrollPane listScroll = new JScrollPane(textList);
        listScroll.setBorder(null);
        listScroll.getViewport().setBackground(Theme.SURFACE);

        JButton refreshTexts = new JButton("Refresh");
        refreshTexts.setBackground(Theme.SURFACE);
        refreshTexts.setForeground(Theme.ACCENT);
        refreshTexts.addActionListener(event -> reloadTexts());

        JPanel textsHeader = new JPanel(new BorderLayout(4, 0));
        textsHeader.setOpaque(false);
        JLabel textsLabel = new JLabel("Texts");
        textsLabel.setForeground(Theme.MUTED);
        textsHeader.add(textsLabel, BorderLayout.WEST);
        textsHeader.add(refreshTexts, BorderLayout.EAST);

        JPanel textsPanel = new JPanel(new BorderLayout(4, 4));
        textsPanel.setBackground(Theme.SURFACE);
        textsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.TEXT_PENDING),
                null,
                0,
                0,
                null,
                Theme.MUTED
        ));
        textsPanel.setPreferredSize(new Dimension(220, 0));
        textsPanel.add(textsHeader, BorderLayout.NORTH);
        textsPanel.add(listScroll, BorderLayout.CENTER);

        liveStats.setForeground(Theme.ACCENT);
        liveStats.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        liveStats.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel hint = new JLabel("Type in the pane. Esc restarts and discards an unfinished run. Backspace corrects. Ctrl+Backspace jumps to the current word start. Green = correct, red = error, yellow underline = caret.", SwingConstants.CENTER);
        hint.setForeground(Theme.MUTED);
        hint.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));

        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(Theme.BACKGROUND);
        JScrollPane typingScroll = new JScrollPane(typingPane);
        typingScroll.setBorder(null);
        typingScroll.getViewport().setBackground(Theme.BACKGROUND);
        center.add(liveStats, BorderLayout.NORTH);
        center.add(typingScroll, BorderLayout.CENTER);
        center.add(hint, BorderLayout.SOUTH);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, textsPanel, center);
        mainSplit.setDividerLocation(220);
        mainSplit.setBorder(null);

        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainSplit, statsPanel);
        vertical.setResizeWeight(0.62);
        vertical.setBorder(null);
        add(vertical);

        Timer ticker = new Timer(200, event -> updateLiveStats());
        ticker.start();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                ticker.stop();
                persistPendingRecord();
                try {
                    stats.close();
                } catch (SQLException ignored) {
                    // closing best-effort
                }
            }
        });

        reloadTexts();
        statsPanel.reload();
        statsPanel.refreshLive(engine, currentFileName);
        updateLiveStats();
    }

    private void reloadTexts() {
        String keep = currentFileName;
        suppressTextLoad = true;
        textNames.clear();
        try {
            textFiles = texts.listTextFiles();
            for (Path file : textFiles) {
                textNames.addElement(file.getFileName().toString());
            }
            if (textFiles.isEmpty()) {
                suppressTextLoad = false;
                engine.load("Add .txt files to " + texts.getTextsDir().toAbsolutePath());
                currentFileName = "";
                typingPane.refresh();
                return;
            }
            int index = indexOfFileName(keep);
            if (index < 0) {
                index = indexOfLastUsedText();
            }
            textList.setSelectedIndex(index);
            suppressTextLoad = false;
            String selected = textFiles.get(index).getFileName().toString();
            if (!selected.equals(keep) || engine.getText().isEmpty()) {
                loadSelectedText();
            }
        } catch (IOException ex) {
            suppressTextLoad = false;
            JOptionPane.showMessageDialog(this, "Could not read texts folder: " + ex.getMessage());
        }
    }

    private int indexOfFileName(String name) {
        if (name == null || name.isBlank()) {
            return -1;
        }
        for (int i = 0; i < textFiles.size(); i++) {
            if (name.equals(textFiles.get(i).getFileName().toString())) {
                return i;
            }
        }
        return -1;
    }

    private int indexOfLastUsedText() {
        try {
            String last = stats.lastUsedTextFile();
            if (last != null) {
                for (int i = 0; i < textFiles.size(); i++) {
                    if (last.equals(textFiles.get(i).getFileName().toString())) {
                        return i;
                    }
                }
            }
        } catch (SQLException ignored) {
            // fall back to first text
        }
        return 0;
    }

    private void loadSelectedText() {
        int index = textList.getSelectedIndex();
        if (index < 0 || index >= textFiles.size()) {
            return;
        }
        Path file = textFiles.get(index);
        persistPendingRecord();
        try {
            engine.load(texts.read(file));
            currentFileName = file.getFileName().toString();
            pendingRecord = false;
            try {
                stats.setLastUsedTextFile(currentFileName);
            } catch (SQLException ignored) {
                // last-used text is best-effort
            }
            typingPane.refresh();
            updateLiveStats();
            typingPane.requestFocusInWindow();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not open " + file.getFileName() + ": " + ex.getMessage());
        }
    }

    private void onEngineChanged() {
        if (engine.isFinished() && engine.isStarted()) {
            pendingRecord = true;
            persistPendingRecord();
        } else if (!engine.isStarted()) {
            pendingRecord = false;
        }
        updateLiveStats();
        if (engine.isFinished()) {
            return;
        }
        if (engine.isStarted()) {
            statsPanel.showLiveSession();
        }
        statsPanel.refreshLive(engine, currentFileName, true);
    }

    private void persistPendingRecord() {
        if (!pendingRecord || !engine.isFinished() || !engine.isStarted()) {
            return;
        }
        try {
            Instant finished = Instant.now();
            Instant started = finished.minusMillis(engine.elapsedMillis());
            long sessionId = stats.saveCompletedSession(
                    started,
                    finished,
                    currentFileName,
                    engine.getTypedCount(),
                    engine.getCorrectCount(),
                    engine.getErrorEvents(),
                    engine.wordsPerMinute(),
                    engine.accuracyPercent(),
                    engine.elapsedMillis(),
                    engine.getLetterFailures(),
                    engine.getBigramFailures(),
                    engine.wordOccurrences()
            );
            pendingRecord = false;
            try {
                stats.exportSessionWordsJson(sessionId, exportsDir);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Session saved, but word JSON export failed: " + ex.getMessage());
            }
            statsPanel.reload();
            statsPanel.selectSession(sessionId);
            statsPanel.refreshLive(engine, currentFileName);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Could not save session: " + ex.getMessage());
        }
    }

    private void updateLiveStats() {
        Character worst = engine.worstLetter();
        TypedBigram worstBigram = engine.worstBigram();
        String worstText = worst == null ? "—" : StatsPanel.formatLetter(worst);
        String worstBigramText = StatsPanel.formatBigram(worstBigram);
        liveStats.setText(String.format(
                "WPM %.1f   accuracy %.1f%%   errors %d   session worst letter %s   worst bigram %s   %s",
                engine.wordsPerMinute(),
                engine.accuracyPercent(),
                engine.getErrorEvents(),
                worstText,
                worstBigramText,
                engine.isFinished() ? "done — Esc restarts a new run" : "press Esc to restart"
        ));
        statsPanel.refreshLive(engine, currentFileName, false);
    }
}
