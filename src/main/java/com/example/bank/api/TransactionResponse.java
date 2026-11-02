package com.example.bank.api;

import com.example.bank.domain.LedgerEntry;
import com.example.bank.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record TransactionResponse(UUID reference,
                           Long accountNumber,
                           TransactionType type,
                           BigDecimal amount,
                           BigDecimal balanceAfter,
                           Long counterpartyAccountNumber,
                           Instant timestamp) {

    static TransactionResponse from(LedgerEntry e) {
        return new TransactionResponse(e.getReference(), e.getAccountNumber(), e.getType(), e.getAmount(),
                e.getBalanceAfter(), e.getCounterpartyAccountNumber(), e.getCreatedAt());
    }
}
