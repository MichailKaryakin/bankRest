package org.example.bankrest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TransferRequest(
        @NotNull(message = "Source card ID is required")
        Long fromCardId,

        @NotNull(message = "Destination card ID is required")
        Long toCardId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Transfer amount must be greater than 0")
        @Digits(integer = 15, fraction = 2, message = "Invalid amount format")
        BigDecimal amount
) {
}
