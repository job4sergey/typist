package com.typist.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.typist.engine.TypedBigram;

public final class StatsRepository implements AutoCloseable {
    private final Connection connection;

    public StatsRepository(Path databaseFile) throws SQLException {
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        started_at TEXT NOT NULL,
                        finished_at TEXT NOT NULL,
                        text_file TEXT NOT NULL,
                        chars_typed INTEGER NOT NULL,
                        correct_chars INTEGER NOT NULL,
                        error_events INTEGER NOT NULL,
                        wpm REAL NOT NULL,
                        accuracy REAL NOT NULL,
                        duration_ms INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS letter_failures (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        session_id INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
                        letter TEXT NOT NULL,
                        fail_count INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS settings (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS bigram_failures (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        session_id INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
                        previous_char TEXT NOT NULL,
                        current_char TEXT NOT NULL,
                        fail_count INTEGER NOT NULL
                    )
                    """);
        }
    }

    public void setLastUsedTextFile(String fileName) throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement(
                """
                INSERT INTO settings(key, value) VALUES ('last_text', ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """
        )) {
            upsert.setString(1, fileName == null ? "" : fileName);
            upsert.executeUpdate();
        }
    }

    public String lastUsedTextFile() throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT value FROM settings WHERE key = 'last_text'"
        )) {
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                String value = rows.getString(1);
                return (value == null || value.isBlank()) ? null : value;
            }
        }
    }

    public long saveCompletedSession(
            Instant startedAt,
            Instant finishedAt,
            String textFile,
            int charsTyped,
            int correctChars,
            int errorEvents,
            double wpm,
            double accuracy,
            long durationMs,
            Map<Character, Integer> letterFailures
    ) throws SQLException {
        return saveCompletedSession(
                startedAt, finishedAt, textFile, charsTyped, correctChars, errorEvents,
                wpm, accuracy, durationMs, letterFailures, Map.of()
        );
    }

    public long saveCompletedSession(
            Instant startedAt,
            Instant finishedAt,
            String textFile,
            int charsTyped,
            int correctChars,
            int errorEvents,
            double wpm,
            double accuracy,
            long durationMs,
            Map<Character, Integer> letterFailures,
            Map<TypedBigram, Integer> bigramFailures
    ) throws SQLException {
        connection.setAutoCommit(false);
        try {
            long sessionId;
            try (PreparedStatement insert = connection.prepareStatement(
                    """
                    INSERT INTO sessions (
                        started_at, finished_at, text_file, chars_typed, correct_chars,
                        error_events, wpm, accuracy, duration_ms
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            )) {
                insert.setString(1, startedAt.toString());
                insert.setString(2, finishedAt.toString());
                insert.setString(3, textFile);
                insert.setInt(4, charsTyped);
                insert.setInt(5, correctChars);
                insert.setInt(6, errorEvents);
                insert.setDouble(7, wpm);
                insert.setDouble(8, accuracy);
                insert.setLong(9, durationMs);
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Missing session id");
                    }
                    sessionId = keys.getLong(1);
                }
            }
            try (PreparedStatement insertLetter = connection.prepareStatement(
                    "INSERT INTO letter_failures (session_id, letter, fail_count) VALUES (?, ?, ?)"
            )) {
                for (Map.Entry<Character, Integer> entry : letterFailures.entrySet()) {
                    insertLetter.setLong(1, sessionId);
                    insertLetter.setString(2, String.valueOf(entry.getKey()));
                    insertLetter.setInt(3, entry.getValue());
                    insertLetter.addBatch();
                }
                insertLetter.executeBatch();
            }
            try (PreparedStatement insertBigram = connection.prepareStatement(
                    """
                    INSERT INTO bigram_failures (session_id, previous_char, current_char, fail_count)
                    VALUES (?, ?, ?, ?)
                    """
            )) {
                for (Map.Entry<TypedBigram, Integer> entry : bigramFailures.entrySet()) {
                    insertBigram.setLong(1, sessionId);
                    insertBigram.setString(2, String.valueOf(entry.getKey().previous()));
                    insertBigram.setString(3, String.valueOf(entry.getKey().current()));
                    insertBigram.setInt(4, entry.getValue());
                    insertBigram.addBatch();
                }
                insertBigram.executeBatch();
            }
            connection.commit();
            return sessionId;
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public List<SessionRecord> listSessions() throws SQLException {
        List<SessionRecord> sessions = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT * FROM sessions ORDER BY id DESC"
        );
             ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                sessions.add(mapSession(rows));
            }
        }
        return sessions;
    }

    public SessionRecord findSession(long id) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT * FROM sessions WHERE id = ?"
        )) {
            query.setLong(1, id);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return mapSession(rows);
            }
        }
    }

    public List<LetterFailure> letterFailuresForSession(long sessionId) throws SQLException {
        List<LetterFailure> failures = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
                """
                SELECT letter, fail_count
                FROM letter_failures
                WHERE session_id = ?
                ORDER BY fail_count DESC, letter ASC
                """
        )) {
            query.setLong(1, sessionId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    failures.add(mapLetter(rows));
                }
            }
        }
        return failures;
    }

    public List<LetterFailure> overallLetterFailures() throws SQLException {
        List<LetterFailure> failures = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
                """
                SELECT letter, SUM(fail_count) AS fail_count
                FROM letter_failures
                GROUP BY letter
                ORDER BY fail_count DESC, letter ASC
                """
        );
             ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                failures.add(mapLetter(rows));
            }
        }
        return failures;
    }

    public LetterFailure worstLetterOverall() throws SQLException {
        List<LetterFailure> all = overallLetterFailures();
        return all.isEmpty() ? null : all.get(0);
    }

    public List<BigramFailure> bigramFailuresForSession(long sessionId) throws SQLException {
        List<BigramFailure> failures = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
                """
                SELECT previous_char, current_char, fail_count
                FROM bigram_failures
                WHERE session_id = ?
                ORDER BY fail_count DESC, previous_char ASC, current_char ASC
                """
        )) {
            query.setLong(1, sessionId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    failures.add(mapBigram(rows));
                }
            }
        }
        return failures;
    }

    public List<BigramFailure> overallBigramFailures() throws SQLException {
        List<BigramFailure> failures = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
                """
                SELECT previous_char, current_char, SUM(fail_count) AS fail_count
                FROM bigram_failures
                GROUP BY previous_char, current_char
                ORDER BY fail_count DESC, previous_char ASC, current_char ASC
                """
        );
             ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                failures.add(mapBigram(rows));
            }
        }
        return failures;
    }

    public BigramFailure worstBigramOverall() throws SQLException {
        List<BigramFailure> all = overallBigramFailures();
        return all.isEmpty() ? null : all.get(0);
    }

    public List<YearMonth> monthsWithSessions() throws SQLException {
        ZoneId zone = ZoneId.systemDefault();
        Map<YearMonth, Boolean> months = new LinkedHashMap<>();
        for (SessionRecord session : listSessions()) {
            YearMonth month = YearMonth.from(session.finishedAt().atZone(zone));
            months.putIfAbsent(month, Boolean.TRUE);
        }
        return new ArrayList<>(months.keySet());
    }

    public MonthlyAggregate monthlyAggregate(YearMonth month) throws SQLException {
        ZoneId zone = ZoneId.systemDefault();
        int sessionCount = 0;
        int errorEvents = 0;
        Map<Character, Integer> counts = new LinkedHashMap<>();
        Map<TypedBigram, Integer> bigrams = new LinkedHashMap<>();
        for (SessionRecord session : listSessions()) {
            if (!YearMonth.from(session.finishedAt().atZone(zone)).equals(month)) {
                continue;
            }
            sessionCount++;
            errorEvents += session.errorEvents();
            for (LetterFailure failure : letterFailuresForSession(session.id())) {
                counts.merge(failure.letter(), failure.failCount(), Integer::sum);
            }
            for (BigramFailure failure : bigramFailuresForSession(session.id())) {
                bigrams.merge(
                        new TypedBigram(failure.previous(), failure.current()),
                        failure.failCount(),
                        Integer::sum
                );
            }
        }
        List<LetterFailure> failures = counts.entrySet().stream()
                .map(entry -> new LetterFailure(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> {
                    int cmp = Integer.compare(b.failCount(), a.failCount());
                    return cmp != 0 ? cmp : Character.compare(a.letter(), b.letter());
                })
                .toList();
        List<BigramFailure> bigramList = bigrams.entrySet().stream()
                .map(entry -> new BigramFailure(entry.getKey().previous(), entry.getKey().current(), entry.getValue()))
                .sorted((a, b) -> {
                    int cmp = Integer.compare(b.failCount(), a.failCount());
                    if (cmp != 0) {
                        return cmp;
                    }
                    cmp = Character.compare(a.previous(), b.previous());
                    return cmp != 0 ? cmp : Character.compare(a.current(), b.current());
                })
                .toList();
        LetterFailure worst = failures.isEmpty() ? null : failures.get(0);
        BigramFailure worstBigram = bigramList.isEmpty() ? null : bigramList.get(0);
        return new MonthlyAggregate(month, sessionCount, errorEvents, worst, failures, worstBigram, bigramList);
    }

    private static SessionRecord mapSession(ResultSet rows) throws SQLException {
        return new SessionRecord(
                rows.getLong("id"),
                Instant.parse(rows.getString("started_at")),
                Instant.parse(rows.getString("finished_at")),
                rows.getString("text_file"),
                rows.getInt("chars_typed"),
                rows.getInt("correct_chars"),
                rows.getInt("error_events"),
                rows.getDouble("wpm"),
                rows.getDouble("accuracy"),
                rows.getLong("duration_ms")
        );
    }

    private static LetterFailure mapLetter(ResultSet rows) throws SQLException {
        String letter = rows.getString("letter");
        char ch = (letter == null || letter.isEmpty()) ? '?' : letter.charAt(0);
        return new LetterFailure(ch, rows.getInt("fail_count"));
    }

    private static BigramFailure mapBigram(ResultSet rows) throws SQLException {
        return new BigramFailure(
                firstChar(rows.getString("previous_char")),
                firstChar(rows.getString("current_char")),
                rows.getInt("fail_count")
        );
    }

    private static char firstChar(String value) {
        return (value == null || value.isEmpty()) ? '?' : value.charAt(0);
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
