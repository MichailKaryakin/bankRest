package org.example.bankrest.security;

import org.example.bankrest.entity.RefreshToken;
import org.example.bankrest.entity.Role;
import org.example.bankrest.entity.User;
import org.example.bankrest.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks private RefreshTokenService refreshTokenService;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpiration", 604800000L);

        user = User.builder()
                .id(1L).username("alice").email("alice@mail.com")
                .password("hashed").role(Role.USER).enabled(true)
                .build();
    }

    @Nested
    @DisplayName("createRefreshToken")
    class Create {

        @Test
        @DisplayName("should persist a hashed token and return raw token")
        void success() {
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            String raw = refreshTokenService.createRefreshToken(user);

            assertThat(raw).isNotBlank();
            assertThat(raw).hasSize(64);

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(captor.capture());

            RefreshToken saved = captor.getValue();
            assertThat(saved.getTokenHash()).isNotEqualTo(raw);
            assertThat(saved.getTokenHash()).hasSize(64);
            assertThat(saved.isRevoked()).isFalse();
            assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        }

        @Test
        @DisplayName("two calls should produce different tokens")
        void unique() {
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            String t1 = refreshTokenService.createRefreshToken(user);
            String t2 = refreshTokenService.createRefreshToken(user);

            assertThat(t1).isNotEqualTo(t2);
        }
    }

    @Nested
    @DisplayName("validateAndGet")
    class Validate {

        @Test
        @DisplayName("should return token entity for a valid raw token")
        void success() {
            String raw = "a".repeat(64);
            RefreshToken entity = buildToken(false, Instant.now().plusSeconds(3600));

            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(entity));

            RefreshToken result = refreshTokenService.validateAndGet(raw);

            assertThat(result).isSameAs(entity);
        }

        @Test
        @DisplayName("should throw when token not found")
        void notFound() {
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService.validateAndGet("x".repeat(64)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("should throw when token is revoked")
        void revoked() {
            RefreshToken entity = buildToken(true, Instant.now().plusSeconds(3600));
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> refreshTokenService.validateAndGet("x".repeat(64)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("revoked");
        }

        @Test
        @DisplayName("should throw when token is expired")
        void expired() {
            RefreshToken entity = buildToken(false, Instant.now().minusSeconds(1));
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> refreshTokenService.validateAndGet("x".repeat(64)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("expired");
        }
    }

    @Nested
    @DisplayName("rotate")
    class Rotate {

        @Test
        @DisplayName("should revoke old token and issue a new one")
        void success() {
            RefreshToken old = buildToken(false, Instant.now().plusSeconds(3600));
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(old));
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            String newRaw = refreshTokenService.rotate("x".repeat(64));

            assertThat(old.isRevoked()).isTrue();
            assertThat(newRaw).isNotBlank();
            assertThat(newRaw).isNotEqualTo("x".repeat(64));
            verify(refreshTokenRepository, times(2)).save(any());
        }
    }

    @Nested
    @DisplayName("revokeAll")
    class RevokeAll {

        @Test
        @DisplayName("should call revokeAllByUserId")
        void success() {
            refreshTokenService.revokeAll(1L);
            verify(refreshTokenRepository).revokeAllByUserId(1L);
        }
    }

    private RefreshToken buildToken(boolean revoked, Instant expiresAt) {
        return RefreshToken.builder()
                .id(1L)
                .tokenHash("a".repeat(64))
                .user(user)
                .expiresAt(expiresAt)
                .revoked(revoked)
                .createdAt(Instant.now())
                .build();
    }
}
