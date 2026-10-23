package com.sondurukedella.common.exception;

/** A valid request that a business rule does not allow. Mapped to HTTP 422. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
