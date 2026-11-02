package com.example.bank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * An account guards its own invariant: the balance never goes negative.
 * Callers are expected to hold a database row lock (see AccountRepository)
 * while changing the balance; {@code version} is a second line of defence.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "account_number_seq")
    @SequenceGenerator(name = "account_number_seq", sequenceName = "account_number_seq",
            initialValue = 1000000001, allocationSize = 1)
    @Column(name = "account_number")
    private Long accountNumber;

    @Column(name = "owner_name", nullable = false, length = 100)
    private String ownerName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
        // for JPA
    }

    public Account(String ownerName, BigDecimal openingBalance, Instant createdAt) {
        if (ownerName == null || ownerName.isBlank()) {
            throw new InvalidRequestException("Owner name is required");
        }
        if (ownerName.trim().length() > 100) {
            throw new InvalidRequestException("Owner name must be at most 100 characters");
        }
        this.ownerName = ownerName.trim();
        this.balance = Amounts.nonNegative(openingBalance);
        this.createdAt = createdAt;
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(Amounts.positive(amount));
    }

    public void debit(BigDecimal amount) {
        BigDecimal valid = Amounts.positive(amount);
        if (balance.compareTo(valid) < 0) {
            throw new InsufficientFundsException(accountNumber);
        }
        balance = balance.subtract(valid);
    }

    public Long getAccountNumber() {
        return accountNumber;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
