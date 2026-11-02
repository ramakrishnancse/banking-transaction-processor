package com.example.bank.domain;

/** The same Idempotency-Key was used for a different operation. */
public class IdempotencyKeyReuseException extends RuntimeException {
    public IdempotencyKeyReuseException(String key) {
        super("Idempotency-Key '" + key + "' was already used for a different request");
    }
}
