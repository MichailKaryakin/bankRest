package org.example.bankrest.dto;

import org.example.bankrest.entity.Role;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String email,
        Role role,
        boolean enabled,
        LocalDateTime createdAt
) {
}
