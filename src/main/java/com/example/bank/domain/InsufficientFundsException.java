package com.example.bank.domain;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(Long accountNumber) {
        super("Insufficient funds in account " + accountNumber);
    }
}
