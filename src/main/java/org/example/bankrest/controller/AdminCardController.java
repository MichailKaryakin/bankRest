package org.example.bankrest.controller;

import org.example.bankrest.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.bankrest.entity.CardStatus;
import org.example.bankrest.service.CardService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/cards")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Cards", description = "Card management (admin only)")
@SecurityRequirement(name = "bearerAuth")
public class AdminCardController {

    private final CardService cardService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new card for a user")
    public CardResponse createCard(@Valid @RequestBody CreateCardRequest request) {
        return cardService.createCard(request);
    }

    @GetMapping
    @Operation(summary = "Get all cards with optional status filter (paginated)")
    public PagedResponse<CardResponse> getAllCards(
            @RequestParam(required = false) CardStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy
    ) {
        return cardService.getAllCards(status, PageRequest.of(page, size, Sort.by(sortBy).descending()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get card by ID")
    public CardResponse getCard(@PathVariable Long id) {
        return cardService.getCardById(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Set card status (ACTIVE / BLOCKED / EXPIRED)")
    public CardResponse setStatus(
            @PathVariable Long id,
            @RequestParam CardStatus status
    ) {
        return cardService.setCardStatus(id, status);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a card")
    public ResponseEntity<Void> deleteCard(@PathVariable Long id) {
        cardService.deleteCard(id);
        return ResponseEntity.noContent().build();
    }
}
