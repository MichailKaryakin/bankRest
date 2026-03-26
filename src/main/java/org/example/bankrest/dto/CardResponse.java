package org.example.bankrest.dto;

import org.example.bankrest.entity.CardStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CardResponse(
        Long id,
        String maskedNumber,
        String ownerUsername,
        LocalDate expiryDate,
        CardStatus status,
        BigDecimal balance,
        LocalDateTime createdAt
) {
}
