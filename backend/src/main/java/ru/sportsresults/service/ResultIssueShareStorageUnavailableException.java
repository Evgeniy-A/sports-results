package ru.sportsresults.service;

public class ResultIssueShareStorageUnavailableException extends RuntimeException {
    public ResultIssueShareStorageUnavailableException(Throwable cause) {
        super("Shared attachment storage is temporarily unavailable", cause);
    }
}
