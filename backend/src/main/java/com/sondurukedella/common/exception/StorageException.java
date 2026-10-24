package com.sondurukedella.common.exception;

/** The file store failed. Mapped to HTTP 500 without exposing paths or the underlying error. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
