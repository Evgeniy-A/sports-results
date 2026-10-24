package ru.sportsresults.service;

public class CertificateCapacityException extends RuntimeException {
    public CertificateCapacityException() {
        super("Certificate generation is busy; retry shortly");
    }
}
