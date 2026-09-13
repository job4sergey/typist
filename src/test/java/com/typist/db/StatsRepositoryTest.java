package com.typist.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;

import com.typist.engine.TypedBigram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StatsRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void storesSessionAndOverallWorstLetter() throws Exception {
        Path db = tempDir.resolve("typist.db");
        try (StatsRepository repo = new StatsRepository(db)) {
            Instant start = Instant.parse("2026-09-13T09:00:00Z");
            Instant end = Instant.parse("2026-09-13T09:01:00Z");
            long id = repo.saveCompletedSession(
                    start, end, "pangram.txt", 40, 38, 3, 48.0, 95.0, 60_000,
                    Map.of('e', 2, 's', 1),
                    Map.of(new TypedBigram('h', 'e'), 2)
            );
            SessionRecord session = repo.findSession(id);
            assertNotNull(session);
            assertEquals("pangram.txt", session.textFile());
            assertEquals(2, repo.letterFailuresForSession(id).get(0).failCount());
            assertEquals('e', repo.worstLetterOverall().letter());
            assertEquals(1, repo.bigramFailuresForSession(id).size());
        }
    }

    @Test
    void remembersLastUsedTextAndAggregatesFailuresByMonth() throws Exception {
        Path db = tempDir.resolve("typist.db");
        try (StatsRepository repo = new StatsRepository(db)) {
            repo.setLastUsedTextFile("programming.txt");
            assertEquals("programming.txt", repo.lastUsedTextFile());

            repo.saveCompletedSession(
                    Instant.parse("2026-08-02T10:00:00Z"),
                    Instant.parse("2026-08-02T10:01:00Z"),
                    "pangram.txt", 20, 18, 4, 40.0, 90.0, 60_000,
                    Map.of('a', 3, 'e', 1),
                    Map.of(new TypedBigram('t', 'h'), 2)
            );
            repo.saveCompletedSession(
                    Instant.parse("2026-09-13T09:00:00Z"),
                    Instant.parse("2026-09-13T09:01:00Z"),
                    "common-english.txt", 30, 28, 3, 45.0, 93.0, 60_000,
                    Map.of('e', 2, 's', 1),
                    Map.of(new TypedBigram('t', 'h'), 1, new TypedBigram('i', 'n'), 2)
            );
            repo.saveCompletedSession(
                    Instant.parse("2026-09-20T09:00:00Z"),
                    Instant.parse("2026-09-20T09:01:00Z"),
                    "pangram.txt", 25, 24, 1, 50.0, 96.0, 60_000,
                    Map.of('e', 1),
                    Map.of(new TypedBigram('t', 'h'), 1)
            );

            var months = repo.monthsWithSessions();
            MonthlyAggregate september = repo.monthlyAggregate(YearMonth.from(
                    Instant.parse("2026-09-13T09:00:00Z").atZone(ZoneId.systemDefault())
            ));
            assertEquals(2, september.sessionCount());
            assertEquals(4, september.errorEvents());
            assertEquals('e', september.worstLetter().letter());
            assertEquals(3, september.worstLetter().failCount());
            assertEquals('i', september.worstBigram().previous());
            assertEquals('n', september.worstBigram().current());
            assertEquals(2, september.worstBigram().failCount());
            assertFalse(months.isEmpty());
        }
    }
}
