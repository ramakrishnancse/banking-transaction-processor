package com.example.bank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One immutable line in an account's ledger. {@code balanceAfter} makes the history
 * self-auditing, and both legs of a transfer share one {@code reference}.
 * {@code idempotencyKey} is set only on the leg the client initiated, and is unique per account.
 */
@Entity
@Table(name = "ledger_entries",
        indexes = @Index(name = "idx_ledger_account_id", columnList = "account_number, id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_ledger_account_idempotency",
                columnNames = {"account_number", "idempotency_key"}))
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private UUID reference;

    @Column(name = "account_number", nullable = false, updatable = false)
    private Long accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "counterparty_account_number", updatable = false)
    private Long counterpartyAccountNumber;

    @Column(name = "idempotency_key", updatable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
        // for JPA
    }

    public static LedgerEntry of(UUID reference, Long accountNumber, TransactionType type,
                                 BigDecimal amount, BigDecimal balanceAfter,
                                 Long counterpartyAccountNumber, String idempotencyKey, Instant createdAt) {
        LedgerEntry e = new LedgerEntry();
        e.reference = reference;
        e.accountNumber = accountNumber;
        e.type = type;
        e.amount = amount;
        e.balanceAfter = balanceAfter;
        e.counterpartyAccountNumber = counterpartyAccountNumber;
        e.idempotencyKey = idempotencyKey;
        e.createdAt = createdAt;
        return e;
    }

    /** Positive for money in, negative for money out. */
    public BigDecimal signedAmount() {
        return type.isCredit() ? amount : amount.negate();
    }

    /** Is this entry the result of the same operation a client is now repeating? */
    public boolean isSameOperation(TransactionType otherType, BigDecimal otherAmount, Long otherCounterparty) {
        return type == otherType
                && amount.compareTo(otherAmount) == 0
                && Objects.equals(counterpartyAccountNumber, otherCounterparty);
    }

    public Long getId() {
        return id;
    }

    public UUID getReference() {
        return reference;
    }

    public Long getAccountNumber() {
        return accountNumber;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public Long getCounterpartyAccountNumber() {
        return counterpartyAccountNumber;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
