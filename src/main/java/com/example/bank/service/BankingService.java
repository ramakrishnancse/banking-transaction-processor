package com.example.bank.service;

import com.example.bank.domain.Account;
import com.example.bank.domain.AccountNotFoundException;
import com.example.bank.domain.Amounts;
import com.example.bank.domain.IdempotencyKeyReuseException;
import com.example.bank.domain.InvalidRequestException;
import com.example.bank.domain.LedgerEntry;
import com.example.bank.domain.TransactionType;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.LedgerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Each public write method is ONE database transaction: either the balance change and
 * its ledger entries are all committed, or none are.
 *
 * Concurrency: rows are locked with SELECT ... FOR UPDATE. A transfer locks the lower
 * account number first, so opposing transfers can never deadlock.
 *
 * Idempotency: the optional key is checked AFTER the row lock is taken, so two concurrent
 * retries are serialised and the second one sees the first one's committed ledger entry.
 */
@Service
public class BankingService {
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    private final AccountRepository accounts;
    private final LedgerRepository ledger;
    private final Clock clock;

    public BankingService(AccountRepository accounts, LedgerRepository ledger, Clock clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.clock = clock;
    }

    @Transactional
    public Account openAccount(String ownerName, BigDecimal initialDeposit) {
        Instant now = clock.instant();
        Account account = accounts.save(new Account(ownerName, initialDeposit, now));
        if (account.getBalance().signum() > 0) {
            ledger.save(LedgerEntry.of(UUID.randomUUID(), account.getAccountNumber(), TransactionType.OPENING,
                    account.getBalance(), account.getBalance(), null, null, now));
        }
        return account;
    }

    @Transactional
    public LedgerEntry deposit(long accountNumber, BigDecimal amount, String idempotencyKey) {
        BigDecimal valid = Amounts.positive(amount);
        String key = normalise(idempotencyKey);

        Account account = lock(accountNumber);
        Optional<LedgerEntry> replay = findReplay(accountNumber, key, TransactionType.DEPOSIT, valid, null);
        if (replay.isPresent()) {
            return replay.get();
        }
        account.credit(valid);
        return ledger.save(LedgerEntry.of(UUID.randomUUID(), accountNumber, TransactionType.DEPOSIT,
                valid, account.getBalance(), null, key, clock.instant()));
    }

    @Transactional
    public LedgerEntry withdraw(long accountNumber, BigDecimal amount, String idempotencyKey) {
        BigDecimal valid = Amounts.positive(amount);
        String key = normalise(idempotencyKey);

        Account account = lock(accountNumber);
        Optional<LedgerEntry> replay = findReplay(accountNumber, key, TransactionType.WITHDRAWAL, valid, null);
        if (replay.isPresent()) {
            return replay.get();
        }
        account.debit(valid);
        return ledger.save(LedgerEntry.of(UUID.randomUUID(), accountNumber, TransactionType.WITHDRAWAL,
                valid, account.getBalance(), null, key, clock.instant()));
    }

    /** @return the sender's (debit) ledger entry. */
    @Transactional
    public LedgerEntry transfer(long fromNumber, long toNumber, BigDecimal amount, String idempotencyKey) {
        BigDecimal valid = Amounts.positive(amount);
        String key = normalise(idempotencyKey);
        if (fromNumber == toNumber) {
            throw new InvalidRequestException("Cannot transfer to the same account");
        }

        // Fixed lock order (lowest account number first) prevents deadlocks.
        Account lowest = lock(Math.min(fromNumber, toNumber));
        Account highest = lock(Math.max(fromNumber, toNumber));
        Account from = lowest.getAccountNumber() == fromNumber ? lowest : highest;
        Account to = from == lowest ? highest : lowest;

        Optional<LedgerEntry> replay = findReplay(fromNumber, key, TransactionType.TRANSFER_OUT, valid, toNumber);
        if (replay.isPresent()) {
            return replay.get();
        }

        from.debit(valid);
        to.credit(valid);

        UUID reference = UUID.randomUUID();
        Instant now = clock.instant();
        LedgerEntry debit = ledger.save(LedgerEntry.of(reference, fromNumber, TransactionType.TRANSFER_OUT,
                valid, from.getBalance(), toNumber, key, now));
        // The key is stored only on the initiating leg, so it never collides on the receiver.
        ledger.save(LedgerEntry.of(reference, toNumber, TransactionType.TRANSFER_IN,
                valid, to.getBalance(), fromNumber, null, now));
        return debit;
    }

    @Transactional(readOnly = true)
    public Account getAccount(long accountNumber) {
        return accounts.findById(accountNumber).orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntry> history(long accountNumber, Pageable pageable) {
        getAccount(accountNumber);
        return ledger.findByAccountNumber(accountNumber, pageable);
    }

    // ---- helpers ----

    private Account lock(long accountNumber) {
        return accounts.findWithLockByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    private Optional<LedgerEntry> findReplay(long accountNumber, String key, TransactionType type,
                                             BigDecimal amount, Long counterparty) {
        if (key == null) {
            return Optional.empty();
        }
        return ledger.findByAccountNumberAndIdempotencyKey(accountNumber, key).map(prior -> {
            if (!prior.isSameOperation(type, amount, counterparty)) {
                throw new IdempotencyKeyReuseException(key);
            }
            return prior;
        });
    }

    private static String normalise(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new InvalidRequestException(
                    "Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        return trimmed;
    }
}
