package org.example.bankrest.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.bankrest.dto.*;
import org.example.bankrest.entity.Role;
import org.example.bankrest.entity.User;
import org.example.bankrest.repository.UserRepository;
import org.example.bankrest.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.example.bankrest.security.RefreshTokenService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalStateException("Username already taken: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalStateException("Email already registered: " + request.email());
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .enabled(true)
                .build();

        userRepository.save(user);

        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken, user.getUsername(), user.getRole().name());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        refreshTokenService.revokeAll(user.getId());

        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken, user.getUsername(), user.getRole().name());
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        var oldToken = refreshTokenService.validateAndGet(request.refreshToken());
        User user = oldToken.getUser();

        String newRawRefresh = refreshTokenService.rotate(request.refreshToken());
        String accessToken = jwtService.generateToken(user);

        return new AuthResponse(accessToken, newRawRefresh, user.getUsername(), user.getRole().name());
    }

    @Transactional
    public LogoutResponse logout(String username) {
        userRepository.findByUsername(username)
                .ifPresent(user -> refreshTokenService.revokeAll(user.getId()));
        return new LogoutResponse("Logged out successfully");
    }
}
