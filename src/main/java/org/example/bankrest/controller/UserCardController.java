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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Tag(name = "Cards", description = "Card operations for authenticated users")
@SecurityRequirement(name = "bearerAuth")
public class UserCardController {

    private final CardService cardService;

    @GetMapping
    @Operation(summary = "Get user cards with optional status filter (paginated)")
    public PagedResponse<CardResponse> getUserCards(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(required = false) CardStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy
    ) {
        return cardService.getUserCards(
                user.getUsername(), status,
                PageRequest.of(page, size, Sort.by(sortBy).descending())
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of user cards by ID")
    public CardResponse getUserCard(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id
    ) {
        return cardService.getUserCard(id, user.getUsername());
    }

    @PostMapping("/{id}/block-request")
    @Operation(summary = "Request blocking of user card")
    public ResponseEntity<Void> requestBlock(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody BlockRequestCard request
    ) {
        cardService.requestBlockCard(id, user.getUsername(), request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/transfer")
    @Operation(summary = "Transfer funds between my own cards")
    public TransferResponse transfer(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody TransferRequest request
    ) {
        return cardService.transfer(user.getUsername(), request);
    }
}
