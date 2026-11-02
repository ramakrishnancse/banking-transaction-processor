package com.example.bank.domain;

public class InvalidAmountException extends InvalidRequestException {
    public InvalidAmountException(String message) {
        super(message);
    }
}
