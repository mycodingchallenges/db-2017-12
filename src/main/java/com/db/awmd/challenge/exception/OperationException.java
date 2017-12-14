package com.db.awmd.challenge.exception;

/**
 * This is intentionally a checked exception.
 */
public final class OperationException extends Exception {

    public OperationException(final String messageFormat, final Object... args) {
        super(String.format(messageFormat, args));
    }
}
