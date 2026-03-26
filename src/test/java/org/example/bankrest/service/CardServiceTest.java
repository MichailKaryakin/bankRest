package org.example.bankrest.service;

import org.example.bankrest.dto.*;
import org.example.bankrest.entity.Card;
import org.example.bankrest.entity.CardStatus;
import org.example.bankrest.entity.Role;
import org.example.bankrest.entity.User;
import org.example.bankrest.exception.CardOperationException;
import org.example.bankrest.exception.InsufficientFundsException;
import org.example.bankrest.repository.CardRepository;
import org.example.bankrest.repository.UserRepository;
import org.example.bankrest.util.CardEncryptionUtil;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CardEncryptionUtil encryptionUtil;

    @InjectMocks
    private CardService cardService;

    private User owner;
    private Card activeCard;
    private Card secondCard;

    @BeforeEach
    void setUp() {
        owner = User.builder()
                .id(1L)
                .username("john")
                .email("john@example.com")
                .password("hashed")
                .role(Role.USER)
                .enabled(true)
                .build();

        activeCard = Card.builder()
                .id(10L)
                .encryptedNumber("enc123")
                .maskedNumber("**** **** **** 1234")
                .owner(owner)
                .expiryDate(LocalDate.now().plusYears(2))
                .status(CardStatus.ACTIVE)
                .balance(new BigDecimal("500.00"))
                .createdAt(LocalDateTime.now())
                .build();

        secondCard = Card.builder()
                .id(11L)
                .encryptedNumber("enc456")
                .maskedNumber("**** **** **** 5678")
                .owner(owner)
                .expiryDate(LocalDate.now().plusYears(2))
                .status(CardStatus.ACTIVE)
                .balance(new BigDecimal("100.00"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("createCard")
    class CreateCard {

        @Test
        @DisplayName("should create card with encrypted number and mask")
        void success() {
            CreateCardRequest req = new CreateCardRequest(
                    1L, "1234567890123456",
                    LocalDate.now().plusYears(3), new BigDecimal("200.00")
            );
            when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
            when(encryptionUtil.encrypt("1234567890123456")).thenReturn("ENC");
            when(encryptionUtil.mask("1234567890123456")).thenReturn("**** **** **** 3456");
            when(cardRepository.save(any())).thenReturn(activeCard);

            CardResponse result = cardService.createCard(req);

            assertThat(result).isNotNull();
            assertThat(result.maskedNumber()).isEqualTo("**** **** **** 1234");
            verify(encryptionUtil).encrypt("1234567890123456");
            verify(encryptionUtil).mask("1234567890123456");
        }

        @Test
        @DisplayName("should throw when owner not found")
        void ownerNotFound() {
            CreateCardRequest req = new CreateCardRequest(
                    99L, "1234567890123456",
                    LocalDate.now().plusYears(1), null
            );
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cardService.createCard(req))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("should default balance to zero when not provided")
        void defaultBalance() {
            CreateCardRequest req = new CreateCardRequest(
                    1L, "1234567890123456",
                    LocalDate.now().plusYears(2), null
            );
            when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
            when(encryptionUtil.encrypt(any())).thenReturn("ENC");
            when(encryptionUtil.mask(any())).thenReturn("**** **** **** 3456");

            Card zeroBalanceCard = Card.builder()
                    .id(12L).encryptedNumber("ENC").maskedNumber("**** **** **** 3456")
                    .owner(owner).expiryDate(req.expiryDate())
                    .status(CardStatus.ACTIVE).balance(BigDecimal.ZERO)
                    .createdAt(LocalDateTime.now()).build();
            when(cardRepository.save(any())).thenReturn(zeroBalanceCard);

            CardResponse result = cardService.createCard(req);
            assertThat(result.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("transfer")
    class Transfer {

        @Test
        @DisplayName("should transfer successfully and update balances")
        void success() {
            TransferRequest req = new TransferRequest(10L, 11L, new BigDecimal("200.00"));
            when(userRepository.findByUsername("john")).thenReturn(Optional.of(owner));
            when(cardRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(activeCard));
            when(cardRepository.findByIdAndOwnerId(11L, 1L)).thenReturn(Optional.of(secondCard));
            when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TransferResponse result = cardService.transfer("john", req);

            assertThat(result.amount()).isEqualByComparingTo("200.00");
            assertThat(activeCard.getBalance()).isEqualByComparingTo("300.00");
            assertThat(secondCard.getBalance()).isEqualByComparingTo("300.00");
        }

        @Test
        @DisplayName("should throw InsufficientFundsException when balance is too low")
        void insufficientFunds() {
            TransferRequest req = new TransferRequest(10L, 11L, new BigDecimal("999.00"));
            when(userRepository.findByUsername("john")).thenReturn(Optional.of(owner));
            when(cardRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(activeCard));
            when(cardRepository.findByIdAndOwnerId(11L, 1L)).thenReturn(Optional.of(secondCard));

            assertThatThrownBy(() -> cardService.transfer("john", req))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("Insufficient");
        }

        @Test
        @DisplayName("should throw when source and destination are the same card")
        void sameCard() {
            TransferRequest req = new TransferRequest(10L, 10L, new BigDecimal("50.00"));

            assertThatThrownBy(() -> cardService.transfer("john", req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("different");
        }

        @Test
        @DisplayName("should throw CardOperationException when source card is blocked")
        void sourceBlocked() {
            activeCard.setStatus(CardStatus.BLOCKED);
            TransferRequest req = new TransferRequest(10L, 11L, new BigDecimal("50.00"));
            when(userRepository.findByUsername("john")).thenReturn(Optional.of(owner));
            when(cardRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(activeCard));
            when(cardRepository.findByIdAndOwnerId(11L, 1L)).thenReturn(Optional.of(secondCard));

            assertThatThrownBy(() -> cardService.transfer("john", req))
                    .isInstanceOf(CardOperationException.class)
                    .hasMessageContaining("blocked");
        }

        @Test
        @DisplayName("should throw CardOperationException when destination card is expired")
        void destinationExpired() {
            secondCard.setStatus(CardStatus.EXPIRED);
            TransferRequest req = new TransferRequest(10L, 11L, new BigDecimal("50.00"));
            when(userRepository.findByUsername("john")).thenReturn(Optional.of(owner));
            when(cardRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(activeCard));
            when(cardRepository.findByIdAndOwnerId(11L, 1L)).thenReturn(Optional.of(secondCard));

            assertThatThrownBy(() -> cardService.transfer("john", req))
                    .isInstanceOf(CardOperationException.class)
                    .hasMessageContaining("expired");
        }
    }

    @Nested
    @DisplayName("requestBlockCard")
    class RequestBlock {

        @Test
        @DisplayName("should block an active card")
        void success() {
            when(userRepository.findByUsername("john")).thenReturn(Optional.of(owner));
            when(cardRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(activeCard));
            when(cardRepository.save(any())).thenReturn(activeCard);

            cardService.requestBlockCard(10L, "john", new BlockRequestCard("Lost card"));

            assertThat(activeCard.getStatus()).isEqualTo(CardStatus.BLOCKED);
            verify(cardRepository).save(activeCard);
        }

        @Test
        @DisplayName("should throw when card is already blocked")
        void alreadyBlocked() {
            activeCard.setStatus(CardStatus.BLOCKED);
            when(userRepository.findByUsername("john")).thenReturn(Optional.of(owner));
            when(cardRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(activeCard));

            assertThatThrownBy(() ->
                    cardService.requestBlockCard(10L, "john", new BlockRequestCard("reason")))
                    .isInstanceOf(CardOperationException.class)
                    .hasMessageContaining("already blocked");
        }

        @Test
        @DisplayName("should throw when card does not belong to user")
        void cardNotFound() {
            when(userRepository.findByUsername("john")).thenReturn(Optional.of(owner));
            when(cardRepository.findByIdAndOwnerId(99L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    cardService.requestBlockCard(99L, "john", new BlockRequestCard("reason")))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getUserCards")
    class GetUserCards {

        @Test
        @DisplayName("should return paginated cards for the user")
        void success() {
            var pageable = PageRequest.of(0, 10);
            when(userRepository.findByUsername("john")).thenReturn(Optional.of(owner));
            when(cardRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(activeCard, secondCard)));

            PagedResponse<CardResponse> result = cardService.getUserCards("john", null, pageable);

            assertThat(result.content()).hasSize(2);
            assertThat(result.totalElements()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("setCardStatus")
    class SetCardStatus {

        @Test
        @DisplayName("should change card status")
        void success() {
            when(cardRepository.findById(10L)).thenReturn(Optional.of(activeCard));
            when(cardRepository.save(any())).thenReturn(activeCard);

            cardService.setCardStatus(10L, CardStatus.BLOCKED);

            assertThat(activeCard.getStatus()).isEqualTo(CardStatus.BLOCKED);
        }

        @Test
        @DisplayName("should throw when card not found")
        void notFound() {
            when(cardRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cardService.setCardStatus(99L, CardStatus.BLOCKED))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }
}
