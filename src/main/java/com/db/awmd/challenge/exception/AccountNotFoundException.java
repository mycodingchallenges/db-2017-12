package com.db.awmd.challenge.exception;

public class AccountNotFoundException extends Exception {

    public AccountNotFoundException(final String accountId) {
        super(String.format("Account '%s' was not found in the repository.", accountId));
    }
}
