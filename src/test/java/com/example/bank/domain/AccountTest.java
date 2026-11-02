package com.example.bank.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccountTest {
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final Account account = new Account("Alice", new BigDecimal("100"), NOW);

    @Test
    void opening_balance_is_normalised_to_two_decimals() {
        assertThat(account.getBalance()).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void credit_increases_the_balance() {
        account.credit(new BigDecimal("25.5"));

        assertThat(account.getBalance()).isEqualTo(new BigDecimal("125.50"));
    }

    @Test
    void debiting_exactly_the_balance_is_allowed() {
        account.debit(new BigDecimal("100.00"));

        assertThat(account.getBalance()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void debiting_one_cent_more_than_the_balance_is_rejected_and_changes_nothing() {
        assertThatThrownBy(() -> account.debit(new BigDecimal("100.01")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(account.getBalance()).isEqualTo(new BigDecimal("100.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-5", "1.001"})
    void invalid_amounts_are_rejected(String amount) {
        assertThatThrownBy(() -> account.credit(new BigDecimal(amount)))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> account.debit(new BigDecimal(amount)))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void null_amount_is_rejected() {
        assertThatThrownBy(() -> account.credit(null)).isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void trailing_zeros_beyond_two_decimals_are_accepted() {
        account.credit(new BigDecimal("1.5000"));

        assertThat(account.getBalance()).isEqualTo(new BigDecimal("101.50"));
    }

    @Test
    void negative_opening_balance_is_rejected() {
        assertThatThrownBy(() -> new Account("Bob", new BigDecimal("-1"), NOW))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void blank_owner_name_is_rejected() {
        assertThatThrownBy(() -> new Account("  ", BigDecimal.ZERO, NOW))
                .isInstanceOf(InvalidRequestException.class);
    }
}
