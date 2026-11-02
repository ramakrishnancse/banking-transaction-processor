package com.example.bank.domain;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(long accountNumber) {
        super("Account not found: " + accountNumber);
    }
}
