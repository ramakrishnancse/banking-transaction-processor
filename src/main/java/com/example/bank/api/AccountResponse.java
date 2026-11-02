package com.example.bank.api;

import com.example.bank.domain.Account;
import java.math.BigDecimal;
import java.time.Instant;

record AccountResponse(Long accountNumber, String ownerName, BigDecimal balance, Instant createdAt) {

    static AccountResponse from(Account a) {
        return new AccountResponse(a.getAccountNumber(), a.getOwnerName(), a.getBalance(), a.getCreatedAt());
    }
}
