package ru.sportsresults.service;

public class DocumentStorageUnavailableException extends RuntimeException {
    public DocumentStorageUnavailableException(Throwable cause) {
        super("Document storage is temporarily unavailable", cause);
    }
}
