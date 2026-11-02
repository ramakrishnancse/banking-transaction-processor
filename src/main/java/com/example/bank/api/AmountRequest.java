package com.example.bank.api;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

record AmountRequest(@NotNull BigDecimal amount) {
}
