package org.example.bankrest.service;

import org.example.bankrest.dto.*;
import org.example.bankrest.entity.RefreshToken;
import org.example.bankrest.entity.Role;
import org.example.bankrest.entity.User;
import org.example.bankrest.repository.UserRepository;
import org.example.bankrest.security.JwtService;
import org.example.bankrest.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    private User alice;

    @BeforeEach
    void setUp() {
        alice = User.builder()
                .id(1L).username("alice").email("alice@mail.com")
                .password("hashed").role(Role.USER).enabled(true).build();
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("should register and return access + refresh tokens")
        void success() {
            RegisterRequest req = new RegisterRequest("alice", "alice@mail.com", "pass123");
            when(userRepository.existsByUsername("alice")).thenReturn(false);
            when(userRepository.existsByEmail("alice@mail.com")).thenReturn(false);
            when(passwordEncoder.encode("pass123")).thenReturn("hashed");
            when(userRepository.save(any())).thenReturn(alice);
            when(jwtService.generateToken(any())).thenReturn("access-token");
            when(refreshTokenService.createRefreshToken(any())).thenReturn("refresh-token");

            AuthResponse result = authService.register(req);

            assertThat(result.accessToken()).isEqualTo("access-token");
            assertThat(result.refreshToken()).isEqualTo("refresh-token");
            assertThat(result.username()).isEqualTo("alice");
            assertThat(result.role()).isEqualTo("USER");
        }

        @Test
        @DisplayName("should throw when username is taken")
        void duplicateUsername() {
            when(userRepository.existsByUsername("alice")).thenReturn(true);
            assertThatThrownBy(() ->
                    authService.register(new RegisterRequest("alice", "alice@mail.com", "pass")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Username already taken");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when email is taken")
        void duplicateEmail() {
            when(userRepository.existsByUsername("alice")).thenReturn(false);
            when(userRepository.existsByEmail("alice@mail.com")).thenReturn(true);
            assertThatThrownBy(() ->
                    authService.register(new RegisterRequest("alice", "alice@mail.com", "pass")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Email already registered");
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("should authenticate and return token pair")
        void success() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(jwtService.generateToken(alice)).thenReturn("access-token");
            when(refreshTokenService.createRefreshToken(alice)).thenReturn("refresh-token");

            AuthResponse result = authService.login(new LoginRequest("alice", "pass123"));

            assertThat(result.accessToken()).isEqualTo("access-token");
            assertThat(result.refreshToken()).isEqualTo("refresh-token");
            verify(refreshTokenService).revokeAll(alice.getId());
        }

        @Test
        @DisplayName("should throw on bad credentials")
        void wrongPassword() {
            doThrow(new BadCredentialsException("Bad credentials"))
                    .when(authenticationManager).authenticate(any());
            assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("should revoke old tokens before issuing new ones")
        void revokesOldTokens() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
            when(jwtService.generateToken(any())).thenReturn("t");
            when(refreshTokenService.createRefreshToken(any())).thenReturn("r");

            authService.login(new LoginRequest("alice", "pass"));

            verify(refreshTokenService).revokeAll(1L);
        }
    }

    @Nested
    @DisplayName("refresh")
    class Refresh {

        @Test
        @DisplayName("should return new token pair on valid refresh token")
        void success() {
            RefreshToken entity = RefreshToken.builder()
                    .id(1L).tokenHash("hash").user(alice)
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .revoked(false).createdAt(Instant.now()).build();

            when(refreshTokenService.validateAndGet("old-refresh")).thenReturn(entity);
            when(refreshTokenService.rotate("old-refresh")).thenReturn("new-refresh");
            when(jwtService.generateToken(alice)).thenReturn("new-access");

            AuthResponse result = authService.refresh(new RefreshRequest("old-refresh"));

            assertThat(result.accessToken()).isEqualTo("new-access");
            assertThat(result.refreshToken()).isEqualTo("new-refresh");
        }

        @Test
        @DisplayName("should propagate exception when refresh token is invalid")
        void invalid() {
            when(refreshTokenService.validateAndGet(any()))
                    .thenThrow(new IllegalStateException("Refresh token has expired"));
            assertThatThrownBy(() -> authService.refresh(new RefreshRequest("expired")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("expired");
        }
    }

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("should revoke all tokens and return success message")
        void success() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

            LogoutResponse result = authService.logout("alice");

            assertThat(result.message()).contains("successfully");
            verify(refreshTokenService).revokeAll(1L);
        }

        @Test
        @DisplayName("should not throw when user not found on logout")
        void unknownUser() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
            assertThatNoException().isThrownBy(() -> authService.logout("ghost"));
        }
    }
}
