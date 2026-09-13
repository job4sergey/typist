package com.typist.db;

import java.time.YearMonth;
import java.util.List;

public record MonthlyAggregate(
        YearMonth month,
        int sessionCount,
        int errorEvents,
        LetterFailure worstLetter,
        List<LetterFailure> letterFailures,
        BigramFailure worstBigram,
        List<BigramFailure> bigramFailures
) {
}
