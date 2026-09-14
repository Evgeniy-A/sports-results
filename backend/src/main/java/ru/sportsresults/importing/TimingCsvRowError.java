package ru.sportsresults.importing;

public record TimingCsvRowError(int sourceRowNumber, String column, String message) {
}
