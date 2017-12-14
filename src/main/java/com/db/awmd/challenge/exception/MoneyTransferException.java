package com.db.awmd.challenge.exception;

/**
 * Exception to be thrown when an error occurs while transfering money from one account to another.
 * <p>This is intentionally a checked exception.</p>
 */
public final class MoneyTransferException extends Exception {

    public MoneyTransferException(final String messageFormat, final Object[] args, final Throwable cause) {
        super(String.format(messageFormat, args), cause);
    }
}
