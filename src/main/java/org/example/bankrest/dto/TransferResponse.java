package org.example.bankrest.dto;

import java.math.BigDecimal;

public record TransferResponse(
        String message,
        String fromCard,
        String toCard,
        BigDecimal amount,
        BigDecimal fromCardBalance,
        BigDecimal toCardBalance
) {
}
