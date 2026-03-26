package org.example.bankrest.service;

import org.example.bankrest.dto.*;
import org.example.bankrest.entity.Card;
import org.example.bankrest.entity.CardStatus;
import org.example.bankrest.entity.User;
import org.example.bankrest.exception.CardOperationException;
import org.example.bankrest.exception.InsufficientFundsException;
import org.example.bankrest.repository.CardRepository;
import org.example.bankrest.repository.CardSpecification;
import org.example.bankrest.repository.UserRepository;
import org.example.bankrest.util.CardEncryptionUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final CardEncryptionUtil encryptionUtil;

    @Transactional
    public CardResponse createCard(CreateCardRequest request) {
        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + request.ownerId()));

        String encrypted = encryptionUtil.encrypt(request.cardNumber());
        String masked = encryptionUtil.mask(request.cardNumber());

        Card card = Card.builder()
                .encryptedNumber(encrypted)
                .maskedNumber(masked)
                .owner(owner)
                .expiryDate(request.expiryDate())
                .status(CardStatus.ACTIVE)
                .balance(request.initialBalance() != null ? request.initialBalance() : BigDecimal.ZERO)
                .build();

        return toResponse(cardRepository.save(card));
    }

    @Transactional
    public CardResponse setCardStatus(Long cardId, CardStatus status) {
        Card card = findCardOrThrow(cardId);
        card.setStatus(status);
        return toResponse(cardRepository.save(card));
    }

    @Transactional
    public void deleteCard(Long cardId) {
        findCardOrThrow(cardId);
        cardRepository.deleteById(cardId);
    }

    @Transactional(readOnly = true)
    public PagedResponse<CardResponse> getAllCards(CardStatus status, Pageable pageable) {
        Page<CardResponse> page = cardRepository
                .findAll(CardSpecification.withFilters(null, status), pageable)
                .map(this::toResponse);
        return toPagedResponse(page);
    }

    @Transactional(readOnly = true)
    public CardResponse getCardById(Long cardId) {
        return toResponse(findCardOrThrow(cardId));
    }

    @Transactional(readOnly = true)
    public PagedResponse<CardResponse> getUserCards(String username, CardStatus status, Pageable pageable) {
        User owner = findUserOrThrow(username);
        Page<CardResponse> page = cardRepository
                .findAll(CardSpecification.withFilters(owner.getId(), status), pageable)
                .map(this::toResponse);
        return toPagedResponse(page);
    }

    @Transactional(readOnly = true)
    public CardResponse getUserCard(Long cardId, String username) {
        User owner = findUserOrThrow(username);
        Card card = cardRepository.findByIdAndOwnerId(cardId, owner.getId())
                .orElseThrow(() -> new EntityNotFoundException("Card not found: " + cardId));
        return toResponse(card);
    }

    @Transactional
    public void requestBlockCard(Long cardId, String username, BlockRequestCard request) {
        User owner = findUserOrThrow(username);
        Card card = cardRepository.findByIdAndOwnerId(cardId, owner.getId())
                .orElseThrow(() -> new EntityNotFoundException("Card not found: " + cardId));

        if (card.getStatus() == CardStatus.BLOCKED) {
            throw new CardOperationException("Card is already blocked");
        }
        if (card.getStatus() == CardStatus.EXPIRED) {
            throw new CardOperationException("Cannot block an expired card");
        }

        card.setStatus(CardStatus.BLOCKED);
        cardRepository.save(card);
        log.info("Card {} blocked by user {} — reason: {}", cardId, username, request.reason());
    }

    @Transactional
    public TransferResponse transfer(String username, TransferRequest request) {
        if (request.fromCardId().equals(request.toCardId())) {
            throw new IllegalArgumentException("Source and destination cards must be different");
        }

        User owner = findUserOrThrow(username);

        Card from = cardRepository.findByIdAndOwnerId(request.fromCardId(), owner.getId())
                .orElseThrow(() -> new EntityNotFoundException("Source card not found: " + request.fromCardId()));
        Card to = cardRepository.findByIdAndOwnerId(request.toCardId(), owner.getId())
                .orElseThrow(() -> new EntityNotFoundException("Destination card not found: " + request.toCardId()));

        validateCardForTransfer(from, "Source");
        validateCardForTransfer(to, "Destination");

        if (from.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds. Available: " + from.getBalance() + ", requested: " + request.amount()
            );
        }

        from.setBalance(from.getBalance().subtract(request.amount()));
        to.setBalance(to.getBalance().add(request.amount()));

        cardRepository.save(from);
        cardRepository.save(to);

        log.info("Transfer: {} -> {}, amount: {}, user: {}",
                from.getMaskedNumber(), to.getMaskedNumber(), request.amount(), username);

        return new TransferResponse(
                "Transfer successful",
                from.getMaskedNumber(),
                to.getMaskedNumber(),
                request.amount(),
                from.getBalance(),
                to.getBalance()
        );
    }

    private void validateCardForTransfer(Card card, String role) {
        if (card.getStatus() == CardStatus.BLOCKED) {
            throw new CardOperationException(role + " card is blocked");
        }
        if (card.getStatus() == CardStatus.EXPIRED) {
            throw new CardOperationException(role + " card is expired");
        }
        if (card.getExpiryDate().isBefore(LocalDate.now())) {
            card.setStatus(CardStatus.EXPIRED);
            cardRepository.save(card);
            throw new CardOperationException(role + " card has expired");
        }
    }

    private Card findCardOrThrow(Long id) {
        return cardRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Card not found: " + id));
    }

    private User findUserOrThrow(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
    }

    private CardResponse toResponse(Card card) {
        return new CardResponse(
                card.getId(),
                card.getMaskedNumber(),
                card.getOwner().getUsername(),
                card.getExpiryDate(),
                card.getStatus(),
                card.getBalance(),
                card.getCreatedAt()
        );
    }

    private <T> PagedResponse<T> toPagedResponse(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
