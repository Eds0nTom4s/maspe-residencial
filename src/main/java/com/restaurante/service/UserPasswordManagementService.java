package com.restaurante.service;

import com.restaurante.dto.request.ChangePasswordRequest;
import com.restaurante.dto.response.ChangePasswordResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.User;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.JwtPrincipal;
import com.restaurante.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPasswordManagementService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public ChangePasswordResponse changeOwnPassword(ChangePasswordRequest request) {
        User user = resolveAuthenticatedGlobalUser();
        LocalDateTime now = LocalDateTime.now();
        if (Boolean.TRUE.equals(user.getMustChangePassword())
                && user.getTemporaryPasswordExpiresAt() != null
                && user.getTemporaryPasswordExpiresAt().isBefore(now)) {
            throw new BusinessException("A palavra-passe temporária expirou. Solicite novas credenciais à administração CONSUMA.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException("A palavra-passe atual está incorreta.");
        }
        validateNewPassword(request.newPassword(), request.confirmPassword());
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException("A nova palavra-passe deve ser diferente da atual.");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setPasswordResetRequired(false);
        user.setTemporaryPasswordExpiresAt(null);
        user.setLastPasswordChangedAt(now);
        users.saveAndFlush(user);
        String accessToken = jwtTokenProvider.generateUserToken(user);
        log.info("PASSWORD_CHANGED userId={} at={}", user.getId(), now);
        return new ChangePasswordResponse(user.getId(), user.getUsername(), false, false,
                now, accessToken, "Bearer", jwtTokenProvider.getExpirationMs());
    }

    private User resolveAuthenticatedGlobalUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Utilizador autenticado obrigatório.");
        }
        if (authentication.getPrincipal() instanceof JwtPrincipal principal) {
            if (!principal.isGlobalToken()) {
                throw new AccessDeniedException("Use o token global para alterar a palavra-passe.");
            }
            return users.findById(principal.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getUserId()));
        }
        return users.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", authentication.getName()));
    }

    private void validateNewPassword(String password, String confirmation) {
        if (!password.equals(confirmation)) throw new BusinessException("A confirmação da palavra-passe não corresponde.");
        if (password.length() < 8) throw new BusinessException("A nova palavra-passe deve ter pelo menos 8 caracteres.");
        if (!password.chars().anyMatch(Character::isLetter) || !password.chars().anyMatch(Character::isDigit)) {
            throw new BusinessException("A nova palavra-passe deve conter letras e números.");
        }
    }
}
