package com.typist.db;

public record BigramFailure(char previous, char current, int failCount) {
}
