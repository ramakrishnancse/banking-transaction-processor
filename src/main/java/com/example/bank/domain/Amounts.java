package com.example.bank.domain;

import java.math.BigDecimal;

/**
 * Money validation. Amounts have at most 2 decimal places; extra precision is
 * rejected, never silently rounded. Valid amounts are normalised to scale 2.
 */
public final class Amounts {
    private static final int SCALE = 2;

    private Amounts() {
    }

    public static BigDecimal positive(BigDecimal amount) {
        BigDecimal normalised = withValidScale(amount);
        if (normalised.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
        return normalised;
    }

    public static BigDecimal nonNegative(BigDecimal amount) {
        BigDecimal normalised = withValidScale(amount);
        if (normalised.signum() < 0) {
            throw new InvalidAmountException("Amount must not be negative");
        }
        return normalised;
    }

    private static BigDecimal withValidScale(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidAmountException("Amount is required");
        }
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new InvalidAmountException("At most " + SCALE + " decimal places are allowed");
        }
        return amount.setScale(SCALE);
    }
}
