package com.typist.ui;

import com.typist.db.BigramFailure;
import com.typist.db.LetterFailure;
import com.typist.db.MonthlyAggregate;
import com.typist.db.SessionRecord;
import com.typist.db.StatsRepository;
import com.typist.engine.TypedBigram;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.sql.SQLException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.typist.engine.TypingEngine;

public final class StatsPanel extends JPanel {
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter MONTH =
            DateTimeFormatter.ofPattern("yyyy-MM");

    private final StatsRepository stats;
    private final DefaultTableModel sessionsModel;
    private final DefaultTableModel lettersModel;
    private final DefaultTableModel bigramsModel;
    private final DefaultTableModel monthlyLettersModel;
    private final DefaultTableModel monthlyBigramsModel;
    private final JLabel overallLabel;
    private final JLabel monthlySummary;
    private final JTable sessionsTable;
    private final JComboBox<YearMonth> monthPicker;
    private boolean updatingMonths;
    private boolean updatingSessions;
    private TypingEngine liveEngine;
    private String liveTextFile = "";

    public StatsPanel(StatsRepository stats) {
        this.stats = stats;
        setLayout(new BorderLayout(8, 8));
        setBackground(Theme.SURFACE);
        setOpaque(true);

        overallLabel = new JLabel("Overall worst letter: —");
        overallLabel.setForeground(Theme.ACCENT);
        overallLabel.setFont(overallLabel.getFont().deriveFont(Font.BOLD, 14f));
        overallLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 8));
        add(overallLabel, BorderLayout.NORTH);

        sessionsModel = unreadOnlyModel("ID", "When", "Text", "WPM", "Accuracy", "Errors", "Duration");
        sessionsTable = styledTable(sessionsModel);
        sessionsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sessionsTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !updatingSessions) {
                loadSelectedSessionFailures();
            }
        });

        lettersModel = unreadOnlyModel("Letter", "Failures");
        bigramsModel = unreadOnlyModel("Bigram", "Failures");
        monthlyLettersModel = unreadOnlyModel("Letter", "Failures");
        monthlyBigramsModel = unreadOnlyModel("Bigram", "Failures");

        JSplitPane sessionFailures = stackedTables(
                "Letter failures", styledTable(lettersModel),
                "Bigram failures", styledTable(bigramsModel)
        );
        JSplitPane sessionSplit = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                wrap("Sessions — current run updates live; select a saved row for history", sessionsTable),
                sessionFailures
        );
        sessionSplit.setResizeWeight(0.62);
        sessionSplit.setBorder(null);
        sessionSplit.setBackground(Theme.SURFACE);

        monthPicker = new JComboBox<>();
        monthPicker.setBackground(Theme.SURFACE);
        monthPicker.setForeground(Theme.TEXT_CORRECT);
        monthPicker.addActionListener(event -> {
            if (!updatingMonths) {
                loadMonthlyFailures();
            }
        });

        monthlySummary = new JLabel("Select a month to aggregate failures.");
        monthlySummary.setForeground(Theme.MUTED);

        JPanel monthBar = new JPanel(new BorderLayout(8, 0));
        monthBar.setOpaque(false);
        JLabel monthLabel = new JLabel("Month");
        monthLabel.setForeground(Theme.MUTED);
        JPanel monthControls = new JPanel(new BorderLayout(8, 0));
        monthControls.setOpaque(false);
        monthControls.add(monthLabel, BorderLayout.WEST);
        monthControls.add(monthPicker, BorderLayout.CENTER);
        monthBar.add(monthControls, BorderLayout.WEST);
        monthBar.add(monthlySummary, BorderLayout.CENTER);
        monthBar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JPanel monthly = new JPanel(new BorderLayout(8, 8));
        monthly.setBackground(Theme.SURFACE);
        monthly.add(monthBar, BorderLayout.NORTH);
        monthly.add(stackedTables(
                "Letter failures for the selected month", styledTable(monthlyLettersModel),
                "Bigram failures for the selected month", styledTable(monthlyBigramsModel)
        ), BorderLayout.CENTER);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(Theme.SURFACE);
        tabs.setForeground(Theme.ACCENT);
        tabs.setOpaque(true);
        tabs.addTab("Sessions", sessionSplit);
        tabs.addTab("Monthly", monthly);
        tabs.setTabComponentAt(0, yellowTabTitle("Sessions"));
        tabs.setTabComponentAt(1, yellowTabTitle("Monthly"));
        add(tabs, BorderLayout.CENTER);
    }

    private static JLabel yellowTabTitle(String title) {
        JLabel label = new JLabel(title);
        label.setForeground(Theme.ACCENT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        label.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        return label;
    }

    public void reload() {
        Long selectedId = selectedSessionId();
        YearMonth previouslySelected = (YearMonth) monthPicker.getSelectedItem();
        updatingSessions = true;
        sessionsModel.setRowCount(0);
        sessionsModel.addRow(liveRow());
        try {
            LetterFailure worst = stats.worstLetterOverall();
            BigramFailure worstBigram = stats.worstBigramOverall();
            if (worst == null && worstBigram == null) {
                overallLabel.setText("Overall worst letter / bigram: — (complete a run to store stats)");
            } else {
                overallLabel.setText("Overall worst letter: " + formatWorstLetter(worst)
                        + "   ·   worst bigram: " + formatWorstBigram(worstBigram));
            }
            for (SessionRecord session : stats.listSessions()) {
                sessionsModel.addRow(new Object[]{
                        session.id(),
                        TIME.format(session.finishedAt()),
                        session.textFile(),
                        String.format("%.1f", session.wpm()),
                        String.format("%.1f%%", session.accuracy()),
                        session.errorEvents(),
                        String.format("%.1fs", session.durationMs() / 1000.0)
                });
            }
            updatingMonths = true;
            monthPicker.removeAllItems();
            for (YearMonth month : stats.monthsWithSessions()) {
                monthPicker.addItem(month);
            }
            if (previouslySelected != null) {
                monthPicker.setSelectedItem(previouslySelected);
            }
            updatingMonths = false;
        } catch (SQLException ex) {
            overallLabel.setText("Could not load stats: " + ex.getMessage());
            updatingMonths = false;
        }
        int row = indexOfSession(selectedId == null ? 0L : selectedId);
        sessionsTable.setRowSelectionInterval(row, row);
        updatingSessions = false;
        loadSelectedSessionFailures();
        loadMonthlyFailures();
    }

    public void refreshLive(TypingEngine engine, String textFile) {
        refreshLive(engine, textFile, true);
    }

    public void refreshLive(TypingEngine engine, String textFile, boolean updateFailures) {
        this.liveEngine = engine;
        this.liveTextFile = textFile == null ? "" : textFile;
        if (sessionsModel.getRowCount() == 0) {
            updatingSessions = true;
            sessionsModel.addRow(liveRow());
            updatingSessions = false;
        }
        Object[] row = liveRow();
        for (int column = 0; column < row.length; column++) {
            sessionsModel.setValueAt(row[column], 0, column);
        }
        if (updateFailures && isLiveSelected()) {
            fillFromEngine(engine);
        }
    }

    public void showLiveSession() {
        if (sessionsModel.getRowCount() > 0 && sessionsTable.getSelectedRow() != 0) {
            sessionsTable.setRowSelectionInterval(0, 0);
        }
    }

    private void loadMonthlyFailures() {
        monthlyLettersModel.setRowCount(0);
        monthlyBigramsModel.setRowCount(0);
        YearMonth month = (YearMonth) monthPicker.getSelectedItem();
        if (month == null) {
            monthlySummary.setText("No completed sessions yet.");
            return;
        }
        try {
            MonthlyAggregate aggregate = stats.monthlyAggregate(month);
            monthlySummary.setText(String.format(
                    "%s  ·  %d session%s  ·  %d errors  ·  worst letter %s  ·  worst bigram %s",
                    MONTH.format(month.atDay(1)),
                    aggregate.sessionCount(),
                    aggregate.sessionCount() == 1 ? "" : "s",
                    aggregate.errorEvents(),
                    formatWorstLetter(aggregate.worstLetter()),
                    formatWorstBigram(aggregate.worstBigram())
            ));
            fillLetters(monthlyLettersModel, aggregate.letterFailures());
            fillBigrams(monthlyBigramsModel, aggregate.bigramFailures());
        } catch (SQLException ex) {
            monthlySummary.setText("Could not load monthly stats: " + ex.getMessage());
        }
    }

    private void loadSelectedSessionFailures() {
        if (isLiveSelected()) {
            if (liveEngine != null) {
                fillFromEngine(liveEngine);
            } else {
                lettersModel.setRowCount(0);
                bigramsModel.setRowCount(0);
                fillLetters(lettersModel, List.of());
                fillBigrams(bigramsModel, List.of());
            }
            return;
        }
        lettersModel.setRowCount(0);
        bigramsModel.setRowCount(0);
        int row = sessionsTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        long sessionId = ((Number) sessionsModel.getValueAt(row, 0)).longValue();
        try {
            fillLetters(lettersModel, stats.letterFailuresForSession(sessionId));
            fillBigrams(bigramsModel, stats.bigramFailuresForSession(sessionId));
        } catch (SQLException ex) {
            lettersModel.addRow(new Object[]{ex.getMessage(), ""});
        }
    }

    private Object[] liveRow() {
        if (liveEngine == null) {
            return new Object[]{0L, "current", liveTextFile.isBlank() ? "—" : liveTextFile, "0.0", "100.0%", 0, "0.0s"};
        }
        return new Object[]{
                0L,
                liveEngine.isFinished() ? "current (done)" : "current",
                liveTextFile.isBlank() ? "—" : liveTextFile,
                String.format("%.1f", liveEngine.wordsPerMinute()),
                String.format("%.1f%%", liveEngine.accuracyPercent()),
                liveEngine.getErrorEvents(),
                String.format("%.1fs", liveEngine.elapsedMillis() / 1000.0)
        };
    }

    private void fillFromEngine(TypingEngine engine) {
        List<LetterFailure> letters = new ArrayList<>();
        for (Map.Entry<Character, Integer> entry : engine.getLetterFailures().entrySet()) {
            letters.add(new LetterFailure(entry.getKey(), entry.getValue()));
        }
        letters.sort((a, b) -> {
            int cmp = Integer.compare(b.failCount(), a.failCount());
            return cmp != 0 ? cmp : Character.compare(a.letter(), b.letter());
        });
        List<BigramFailure> bigrams = new ArrayList<>();
        for (Map.Entry<TypedBigram, Integer> entry : engine.getBigramFailures().entrySet()) {
            bigrams.add(new BigramFailure(entry.getKey().previous(), entry.getKey().current(), entry.getValue()));
        }
        bigrams.sort((a, b) -> {
            int cmp = Integer.compare(b.failCount(), a.failCount());
            if (cmp != 0) {
                return cmp;
            }
            cmp = Character.compare(a.previous(), b.previous());
            return cmp != 0 ? cmp : Character.compare(a.current(), b.current());
        });
        lettersModel.setRowCount(0);
        bigramsModel.setRowCount(0);
        fillLetters(lettersModel, letters);
        fillBigrams(bigramsModel, bigrams);
    }

    private boolean isLiveSelected() {
        int row = sessionsTable.getSelectedRow();
        return row < 0 || row == 0 && ((Number) sessionsModel.getValueAt(0, 0)).longValue() == 0L;
    }

    private Long selectedSessionId() {
        int row = sessionsTable.getSelectedRow();
        if (row < 0 || row >= sessionsModel.getRowCount()) {
            return null;
        }
        return ((Number) sessionsModel.getValueAt(row, 0)).longValue();
    }

    private int indexOfSession(long sessionId) {
        for (int i = 0; i < sessionsModel.getRowCount(); i++) {
            if (((Number) sessionsModel.getValueAt(i, 0)).longValue() == sessionId) {
                return i;
            }
        }
        return 0;
    }

    private static void fillLetters(DefaultTableModel model, List<LetterFailure> failures) {
        if (failures.isEmpty()) {
            model.addRow(new Object[]{"(no failures)", 0});
            return;
        }
        for (LetterFailure failure : failures) {
            model.addRow(new Object[]{formatLetter(failure.letter()), failure.failCount()});
        }
    }

    private static void fillBigrams(DefaultTableModel model, List<BigramFailure> failures) {
        if (failures.isEmpty()) {
            model.addRow(new Object[]{"(no failures)", 0});
            return;
        }
        for (BigramFailure failure : failures) {
            model.addRow(new Object[]{formatBigram(failure.previous(), failure.current()), failure.failCount()});
        }
    }

    private static DefaultTableModel unreadOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JSplitPane stackedTables(String topTitle, JTable top, String bottomTitle, JTable bottom) {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, wrap(topTitle, top), wrap(bottomTitle, bottom));
        split.setResizeWeight(0.5);
        split.setBorder(null);
        split.setBackground(Theme.SURFACE);
        return split;
    }

    private static JTable styledTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setBackground(Theme.BACKGROUND);
        table.setForeground(Theme.TEXT_CORRECT);
        table.setGridColor(Theme.SURFACE);
        table.setSelectionBackground(Theme.CARET);
        table.setSelectionForeground(Color.BLACK);
        table.setRowHeight(24);
        table.getTableHeader().setBackground(Theme.SURFACE);
        table.getTableHeader().setForeground(Theme.MUTED);
        return table;
    }

    private static JScrollPane wrap(String title, JTable table) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(javax.swing.BorderFactory.createTitledBorder(
                javax.swing.BorderFactory.createLineBorder(Theme.TEXT_PENDING),
                title,
                0,
                0,
                null,
                Theme.MUTED
        ));
        scroll.getViewport().setBackground(Theme.BACKGROUND);
        return scroll;
    }

    static String formatLetter(char letter) {
        if (letter == ' ') {
            return "space";
        }
        if (letter == '\n') {
            return "newline";
        }
        return String.valueOf(letter);
    }

    static String formatBigram(char previous, char current) {
        return formatLetter(previous) + " → " + formatLetter(current);
    }

    static String formatBigram(TypedBigram bigram) {
        return bigram == null ? "—" : formatBigram(bigram.previous(), bigram.current());
    }

    private static String formatWorstLetter(LetterFailure worst) {
        if (worst == null) {
            return "—";
        }
        return formatLetter(worst.letter()) + " (" + worst.failCount() + ")";
    }

    private static String formatWorstBigram(BigramFailure worst) {
        if (worst == null) {
            return "—";
        }
        return formatBigram(worst.previous(), worst.current()) + " (" + worst.failCount() + ")";
    }
}
