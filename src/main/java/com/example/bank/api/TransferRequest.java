package com.example.bank.api;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

record TransferRequest(@NotNull Long fromAccount,
                       @NotNull Long toAccount,
                       @NotNull BigDecimal amount) {
}
