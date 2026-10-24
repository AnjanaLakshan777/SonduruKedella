package com.sondurukedella.common.exception;

/** An upload bigger than the configured limit. Mapped to HTTP 413. */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(String message) {
        super(message);
    }
}
