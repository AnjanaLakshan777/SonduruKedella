package com.sondurukedella.common.exception;

/** The request conflicts with the current state, e.g. a duplicate or a stale version. Mapped to HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    /** Optimistic locking for updates: the client must send the version it last read. */
    public static void requireCurrentVersion(Long expected, Long current) {
        if (!java.util.Objects.equals(expected, current)) {
            throw new ConflictException("The record was changed by someone else. Reload it and try again.");
        }
    }
}
