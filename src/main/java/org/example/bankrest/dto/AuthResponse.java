package org.example.bankrest.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String username,
        String role
) {
}
