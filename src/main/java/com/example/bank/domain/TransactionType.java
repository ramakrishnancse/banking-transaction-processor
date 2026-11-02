package com.example.bank.domain;

public enum TransactionType {
    OPENING(true),
    DEPOSIT(true),
    TRANSFER_IN(true),
    WITHDRAWAL(false),
    TRANSFER_OUT(false);

    private final boolean credit;

    TransactionType(boolean credit) {
        this.credit = credit;
    }

    /** True when the entry adds money to the account. */
    public boolean isCredit() {
        return credit;
    }
}
