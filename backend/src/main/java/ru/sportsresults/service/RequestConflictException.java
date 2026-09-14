package ru.sportsresults.service;

public class RequestConflictException extends RuntimeException {

    private final String code;

    public RequestConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
