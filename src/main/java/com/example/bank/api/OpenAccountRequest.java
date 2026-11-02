package com.example.bank.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

record OpenAccountRequest(@NotBlank @Size(max = 100) String ownerName,
                          @NotNull BigDecimal initialDeposit) {
}
