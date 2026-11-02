package com.example.bank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.bank.domain.AccountNotFoundException;
import com.example.bank.domain.IdempotencyKeyReuseException;
import com.example.bank.domain.InsufficientFundsException;
import com.example.bank.domain.InvalidAmountException;
import com.example.bank.domain.InvalidRequestException;
import com.example.bank.domain.LedgerEntry;
import com.example.bank.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Runs against a real (in-memory) H2 database, so locking and rollback are genuinely exercised. */
@SpringBootTest
@Import(BankingServiceIntegrationTest.FixedClockConfig.class)
class BankingServiceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private BankingService bank;

    // ---------- opening ----------

    @Test
    void opening_with_money_records_an_opening_ledger_entry() {
        long a = open("Alice", "100.00");

        LedgerEntry entry = entries(a).get(0);
        assertThat(entry.getType()).isEqualTo(TransactionType.OPENING);
        assertThat(entry.getBalanceAfter()).isEqualByComparingTo("100.00");
        assertThat(entry.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void opening_with_zero_records_no_ledger_entry() {
        long a = open("Alice", "0.00");

        assertThat(entries(a)).isEmpty();
    }

    @Test
    void account_numbers_are_unique_and_assigned_by_the_bank() {
        assertThat(open("Alice", "1.00")).isNotEqualTo(open("Bob", "1.00"));
    }

    @Test
    void negative_opening_deposit_is_rejected() {
        assertThatThrownBy(() -> bank.openAccount("Alice", new BigDecimal("-1")))
                .isInstanceOf(InvalidAmountException.class);
    }

    // ---------- deposit / withdraw ----------

    @Test
    void deposit_increases_balance_and_records_balance_after() {
        long a = open("Alice", "100.00");

        LedgerEntry receipt = bank.deposit(a, new BigDecimal("25.50"), null);

        assertThat(balance(a)).isEqualByComparingTo("125.50");
        assertThat(receipt.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(receipt.getBalanceAfter()).isEqualByComparingTo("125.50");
    }

    @Test
    void withdrawing_exactly_the_balance_is_allowed() {
        long a = open("Alice", "100.00");

        bank.withdraw(a, new BigDecimal("100.00"), null);

        assertThat(balance(a)).isEqualByComparingTo("0.00");
    }

    @Test
    void overdraft_is_rejected_and_leaves_no_trace() {
        long a = open("Alice", "100.00");

        assertThatThrownBy(() -> bank.withdraw(a, new BigDecimal("100.01"), null))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(balance(a)).isEqualByComparingTo("100.00");
        assertThat(entries(a)).hasSize(1); // only the opening entry
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "1.001"})
    void invalid_amounts_are_rejected_and_write_nothing(String amount) {
        long a = open("Alice", "100.00");

        assertThatThrownBy(() -> bank.deposit(a, new BigDecimal(amount), null))
                .isInstanceOf(InvalidAmountException.class);

        assertThat(entries(a)).hasSize(1);
    }

    @Test
    void unknown_account_is_reported() {
        assertThatThrownBy(() -> bank.getAccount(42))
                .isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> bank.deposit(42, BigDecimal.ONE, null))
                .isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> bank.history(42, PageRequest.of(0, 10)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ---------- transfer ----------

    @Test
    void transfer_moves_money_and_both_legs_share_a_reference_and_timestamp() {
        long a = open("Alice", "100.00");
        long b = open("Bob", "50.00");

        bank.transfer(a, b, new BigDecimal("40"), null);

        assertThat(balance(a)).isEqualByComparingTo("60.00");
        assertThat(balance(b)).isEqualByComparingTo("90.00");
        LedgerEntry out = last(a);
        LedgerEntry in = last(b);
        assertThat(out.getType()).isEqualTo(TransactionType.TRANSFER_OUT);
        assertThat(out.getCounterpartyAccountNumber()).isEqualTo(b);
        assertThat(in.getType()).isEqualTo(TransactionType.TRANSFER_IN);
        assertThat(in.getCounterpartyAccountNumber()).isEqualTo(a);
        assertThat(in.getReference()).isEqualTo(out.getReference());
        assertThat(in.getCreatedAt()).isEqualTo(out.getCreatedAt());
    }

    @Test
    void transfer_with_insufficient_funds_changes_neither_account() {
        long a = open("Alice", "100.00");
        long b = open("Bob", "50.00");

        assertThatThrownBy(() -> bank.transfer(a, b, new BigDecimal("500"), null))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(balance(a)).isEqualByComparingTo("100.00");
        assertThat(balance(b)).isEqualByComparingTo("50.00");
        assertThat(entries(a)).hasSize(1);
        assertThat(entries(b)).hasSize(1);
    }

    @Test
    void transfer_to_an_unknown_account_does_not_debit_the_sender() {
        long a = open("Alice", "100.00");

        assertThatThrownBy(() -> bank.transfer(a, 42, BigDecimal.TEN, null))
                .isInstanceOf(AccountNotFoundException.class);

        assertThat(balance(a)).isEqualByComparingTo("100.00");
        assertThat(entries(a)).hasSize(1);
    }

    @Test
    void transfer_to_the_same_account_is_rejected() {
        long a = open("Alice", "100.00");

        assertThatThrownBy(() -> bank.transfer(a, a, BigDecimal.TEN, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    // ---------- ledger ----------

    @Test
    void ledger_always_explains_the_balance() {
        long a = open("Alice", "100.00");
        long b = open("Bob", "20.00");

        bank.deposit(a, new BigDecimal("5.25"), null);
        bank.withdraw(a, new BigDecimal("10.10"), null);
        bank.transfer(a, b, new BigDecimal("30"), null);
        bank.transfer(b, a, new BigDecimal("1.01"), null);

        assertThat(sum(entries(a))).isEqualByComparingTo(balance(a));
        assertThat(sum(entries(b))).isEqualByComparingTo(balance(b));
    }

    @Test
    void history_is_paged_newest_first() {
        long a = open("Alice", "100.00");
        bank.deposit(a, BigDecimal.ONE, null);
        bank.deposit(a, BigDecimal.TEN, null);

        var page = bank.history(a, PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "id")));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).getAmount()).isEqualByComparingTo("10.00");
    }

    // ---------- idempotency ----------

    @Test
    void repeating_a_deposit_with_the_same_key_applies_it_once() {
        long a = open("Alice", "100.00");

        LedgerEntry first = bank.deposit(a, BigDecimal.TEN, "key-1");
        LedgerEntry retry = bank.deposit(a, BigDecimal.TEN, "key-1");

        assertThat(retry.getId()).isEqualTo(first.getId());
        assertThat(balance(a)).isEqualByComparingTo("110.00");
        assertThat(entries(a)).hasSize(2);
    }

    @Test
    void repeating_a_transfer_with_the_same_key_moves_money_once() {
        long a = open("Alice", "100.00");
        long b = open("Bob", "0.00");

        bank.transfer(a, b, BigDecimal.TEN, "key-t");
        bank.transfer(a, b, BigDecimal.TEN, "key-t");

        assertThat(balance(a)).isEqualByComparingTo("90.00");
        assertThat(balance(b)).isEqualByComparingTo("10.00");
    }

    @Test
    void reusing_a_key_for_a_different_amount_is_rejected() {
        long a = open("Alice", "100.00");
        bank.deposit(a, BigDecimal.TEN, "key-2");

        assertThatThrownBy(() -> bank.deposit(a, BigDecimal.ONE, "key-2"))
                .isInstanceOf(IdempotencyKeyReuseException.class);

        assertThat(balance(a)).isEqualByComparingTo("110.00");
    }

    // ---------- concurrency ----------

    @Test
    void concurrent_deposits_to_one_account_are_not_lost() throws Exception {
        long a = open("Alice", "100.00");

        runConcurrently(300, () -> bank.deposit(a, BigDecimal.ONE, null));

        assertThat(balance(a)).isEqualByComparingTo("400.00");
        assertThat(entries(a)).hasSize(301);
    }

    @Test
    void concurrent_retries_with_the_same_key_apply_once() throws Exception {
        long a = open("Alice", "100.00");

        runConcurrently(20, () -> bank.deposit(a, BigDecimal.TEN, "retry-key"));

        assertThat(balance(a)).isEqualByComparingTo("110.00");
        assertThat(entries(a)).filteredOn(e -> e.getType() == TransactionType.DEPOSIT).hasSize(1);
    }

    @Test
    void opposing_concurrent_transfers_neither_deadlock_nor_create_or_destroy_money() throws Exception {
        long a = open("Alice", "100.00");
        long b = open("Bob", "100.00");
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            tasks.add(() -> tryTransfer(a, b));
            tasks.add(() -> tryTransfer(b, a));
        }

        runAll(tasks);

        assertThat(balance(a).add(balance(b))).isEqualByComparingTo("200.00");
        assertThat(sum(entries(a))).isEqualByComparingTo(balance(a));
        assertThat(sum(entries(b))).isEqualByComparingTo(balance(b));
    }

    // ---------- helpers ----------

    private long open(String owner, String initial) {
        return bank.openAccount(owner, new BigDecimal(initial)).getAccountNumber();
    }

    private BigDecimal balance(long accountNumber) {
        return bank.getAccount(accountNumber).getBalance();
    }

    private List<LedgerEntry> entries(long accountNumber) {
        return bank.history(accountNumber, PageRequest.of(0, 10_000, Sort.by("id"))).getContent();
    }

    private LedgerEntry last(long accountNumber) {
        List<LedgerEntry> all = entries(accountNumber);
        return all.get(all.size() - 1);
    }

    private static BigDecimal sum(List<LedgerEntry> entries) {
        return entries.stream().map(LedgerEntry::signedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** An overdraft while racing is legitimate; only the invariants matter. */
    private void tryTransfer(long from, long to) {
        try {
            bank.transfer(from, to, BigDecimal.ONE, null);
        } catch (InsufficientFundsException expectedUnderContention) {
            // ignored on purpose
        }
    }

    private void runConcurrently(int times, Runnable task) throws Exception {
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            tasks.add(task);
        }
        runAll(tasks);
    }

    private void runAll(List<Runnable> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            tasks.forEach(t -> futures.add(pool.submit(t)));
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS); // a timeout here would mean a deadlock
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
