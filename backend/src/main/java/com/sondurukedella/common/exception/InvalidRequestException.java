package com.sondurukedella.common.exception;

/** Request input that fails a check the bean validation annotations cannot express. Mapped to HTTP 400. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
