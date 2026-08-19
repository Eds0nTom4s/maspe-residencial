package com.restaurante.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurante.dto.request.ChangePasswordRequest;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.User;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.JwtPrincipal;
import com.restaurante.security.JwtTokenProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserPasswordManagementServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-19T10:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void changesTemporaryPasswordAndIssuesCleanGlobalToken() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JwtTokenProvider tokens = mock(JwtTokenProvider.class);
        User user = user(11L);
        when(users.findById(11L)).thenReturn(Optional.of(user));
        when(encoder.matches("Temporary123", user.getPassword())).thenReturn(true);
        when(encoder.matches("Permanent12345", user.getPassword())).thenReturn(false);
        when(encoder.encode("Permanent12345")).thenReturn("new-hash");
        when(tokens.generateUserToken(user)).thenReturn("new-global-token");
        when(tokens.getExpirationMs()).thenReturn(3_600_000L);
        authenticateGlobal(11L);

        var result = new UserPasswordManagementService(users, encoder, tokens, CLOCK)
                .changeOwnPassword(new ChangePasswordRequest(
                        "Temporary123", "Permanent12345", "Permanent12345"));

        assertThat(result.accessToken()).isEqualTo("new-global-token");
        assertThat(result.mustChangePassword()).isFalse();
        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(user.getMustChangePassword()).isFalse();
        assertThat(user.getPasswordResetRequired()).isFalse();
        assertThat(user.getTemporaryPasswordExpiresAt()).isNull();
        assertThat(user.getLastPasswordChangedAt()).isEqualTo(LocalDateTime.of(2026, 8, 19, 10, 0));
        verify(users).saveAndFlush(user);
    }

    @Test
    void expiredTemporaryPasswordCannotBeUsed() {
        UserRepository users = mock(UserRepository.class);
        User user = user(12L);
        user.setTemporaryPasswordExpiresAt(LocalDateTime.of(2026, 8, 19, 9, 59));
        when(users.findById(12L)).thenReturn(Optional.of(user));
        authenticateGlobal(12L);

        var service = new UserPasswordManagementService(
                users, mock(PasswordEncoder.class), mock(JwtTokenProvider.class), CLOCK);
        assertThatThrownBy(() -> service.changeOwnPassword(
                new ChangePasswordRequest("Temporary123", "Permanent12345", "Permanent12345")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("temporária expirou");
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("owner");
        user.setPassword("old-hash");
        user.setMustChangePassword(true);
        user.setPasswordResetRequired(true);
        user.setTemporaryPasswordExpiresAt(LocalDateTime.of(2026, 8, 20, 10, 0));
        return user;
    }

    private static void authenticateGlobal(Long userId) {
        JwtPrincipal principal = JwtPrincipal.builder()
                .userId(userId)
                .username("owner")
                .tokenType("GLOBAL")
                .authorities(List.of())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
