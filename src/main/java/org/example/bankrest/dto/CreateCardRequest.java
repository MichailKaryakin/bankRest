package org.example.bankrest.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateCardRequest(
        @NotNull(message = "Owner ID is required")
        Long ownerId,

        @NotBlank(message = "Card number is required")
        @Pattern(regexp = "\\d{16}", message = "Card number must be exactly 16 digits")
        String cardNumber,

        @NotNull(message = "Expiry date is required")
        @Future(message = "Expiry date must be in the future")
        LocalDate expiryDate,

        @DecimalMin(value = "0.0", message = "Initial balance cannot be negative")
        BigDecimal initialBalance
) {
}
