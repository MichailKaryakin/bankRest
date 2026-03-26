package org.example.bankrest.dto;

public record PagedResponse<T>(
        java.util.List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
}
