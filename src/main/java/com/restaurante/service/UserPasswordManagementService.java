package com.restaurante.service;

import com.restaurante.dto.request.AdminResetPasswordRequest;
import com.restaurante.dto.request.ChangePasswordRequest;
import com.restaurante.dto.request.PlatformTenantAccessResetPasswordRequest;
import com.restaurante.dto.response.AdminResetPasswordResponse;
import com.restaurante.dto.response.ChangePasswordResponse;
import com.restaurante.dto.response.PlatformTenantAccessResetPasswordResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.exception.TemporaryPasswordExpiredException;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.JwtPrincipal;
import com.restaurante.security.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPasswordManagementService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantGuard tenantGuard;

    @Value("${consuma.security.temporary-password.expiration-hours:168}")
    private int defaultTemporaryPasswordExpirationHours;

    @Transactional
    public ChangePasswordResponse changeOwnPassword(ChangePasswordRequest request) {
        User user = resolveAuthenticatedGlobalUser();
        LocalDateTime now = LocalDateTime.now();

        if (Boolean.TRUE.equals(user.getMustChangePassword())
                && user.getTemporaryPasswordExpiresAt() != null
                && user.getTemporaryPasswordExpiresAt().isBefore(now)) {
            log.warn("Tentativa de troca com senha temporaria expirada para userId={}", user.getId());
            throw new TemporaryPasswordExpiredException(
                    "A senha temporaria expirou. Solicite novo acesso a administracao CONSUMA."
            );
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException("Senha atual incorreta.");
        }
        validateNewPassword(request.getNewPassword(), request.getConfirmPassword());
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException("A nova senha deve ser diferente da senha atual.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        user.setPasswordResetRequired(false);
        user.setTemporaryPasswordExpiresAt(null);
        user.setLastPasswordChangedAt(now);
        userRepository.saveAndFlush(user);

        log.info("PASSWORD_CHANGED userId={} at={}", user.getId(), now);
        return ChangePasswordResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .mustChangePassword(Boolean.FALSE)
                .passwordResetRequired(Boolean.FALSE)
                .lastPasswordChangedAt(user.getLastPasswordChangedAt())
                .message("Senha alterada com sucesso.")
                .build();
    }

    @Transactional
    public AdminResetPasswordResponse resetPasswordByPlatformAdmin(Long userId, AdminResetPasswordRequest request) {
        tenantGuard.assertPlatformAdmin();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        String temporaryPassword = generateTemporaryPassword();
        LocalDateTime resetAt = LocalDateTime.now();
        applyTemporaryPassword(user, temporaryPassword, resolveExpirationHours(request), resolveForceChange(request), resetAt);
        userRepository.saveAndFlush(user);

        log.info("PASSWORD_RESET_BY_PLATFORM_ADMIN userId={} actor={} reasonPresent={}",
                user.getId(), resolveActorId(), request != null && request.getReason() != null && !request.getReason().isBlank());
        return toAdminResetResponse(user, temporaryPassword, resetAt);
    }

    @Transactional
    public PlatformTenantAccessResetPasswordResponse resetPasswordForTenant(Long tenantId,
                                                                            PlatformTenantAccessResetPasswordRequest request) {
        tenantGuard.assertPlatformAdmin();
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", tenantId));

        TenantUser tenantUser;
        if (request != null && request.getUserId() != null) {
            tenantUser = tenantUserRepository.findByTenantIdAndUserId(tenantId, request.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario do tenant", "userId", request.getUserId()));
        } else {
            tenantUser = tenantUserRepository.findByTenantId(tenantId).stream()
                    .filter(item -> item.getRole() == TenantUserRole.TENANT_OWNER)
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Owner do tenant", "tenantId", tenantId));
        }

        // GUARD: Platform Admin cannot be the target of tenant password reset
        // A user that holds ONLY ROLE_ADMIN is a platform-only identity, not a tenant operational user.
        com.restaurante.model.entity.User targetUser = tenantUser.getUser();
        if (targetUser.getRoles() != null && !targetUser.getRoles().isEmpty()
                && targetUser.getRoles().stream().allMatch(r -> r == com.restaurante.model.enums.Role.ROLE_ADMIN)) {
            throw new com.restaurante.exception.BusinessException(
                    "Operacao negada: o usuario alvo e um operador de plataforma, nao um administrador do negocio. " +
                    "Selecione um usuario owner/admin operacional real do tenant.");
        }

        User user = tenantUser.getUser();
        String temporaryPassword = generateTemporaryPassword();
        LocalDateTime resetAt = LocalDateTime.now();
        AdminResetPasswordRequest normalized = AdminResetPasswordRequest.builder()
                .reason(request != null ? request.getMotivo() : null)
                .temporaryPasswordExpiresInHours(request != null ? request.getTemporaryPasswordExpiresInHours() : null)
                .forceChangePassword(Boolean.TRUE)
                .build();
        applyTemporaryPassword(user, temporaryPassword, resolveExpirationHours(normalized), true, resetAt);
        userRepository.saveAndFlush(user);

        log.info("PASSWORD_RESET_BY_PLATFORM_ADMIN tenantId={} userId={} actor={} reasonPresent={}",
                tenantId, user.getId(), resolveActorId(), normalized.getReason() != null && !normalized.getReason().isBlank());
        return PlatformTenantAccessResetPasswordResponse.builder()
                .tenantId(tenantId)
                .userId(user.getId())
                .username(user.getUsername())
                .temporaryPassword(temporaryPassword)
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .passwordResetRequired(Boolean.TRUE.equals(user.getPasswordResetRequired()))
                .temporaryPasswordExpiresAt(user.getTemporaryPasswordExpiresAt())
                .resetAt(resetAt)
                .build();
    }

    public String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder("Tmp@");
        for (int i = 0; i < 10; i++) {
            sb.append(PASSWORD_ALPHABET[SECURE_RANDOM.nextInt(PASSWORD_ALPHABET.length)]);
        }
        sb.append("9");
        return sb.toString();
    }

    public void applyTemporaryPassword(User user, String temporaryPassword) {
        applyTemporaryPassword(user, temporaryPassword, defaultTemporaryPasswordExpirationHours, true, LocalDateTime.now());
    }

    private void applyTemporaryPassword(User user,
                                        String temporaryPassword,
                                        int expiresInHours,
                                        boolean forceChangePassword,
                                        LocalDateTime now) {
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(forceChangePassword);
        user.setPasswordResetRequired(forceChangePassword);
        user.setTemporaryPasswordExpiresAt(now.plusHours(expiresInHours));
        user.setLastPasswordChangedAt(now);
    }

    private User resolveAuthenticatedGlobalUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Usuario autenticado obrigatorio.");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof JwtPrincipal jwtPrincipal) {
            if (jwtPrincipal.isTenantScoped()) {
                throw new AccessDeniedException("Use token global para trocar a propria senha.");
            }
            if (jwtPrincipal.getUserId() != null) {
                return userRepository.findById(jwtPrincipal.getUserId())
                        .orElseThrow(() -> new ResourceNotFoundException("User", "id", jwtPrincipal.getUserId()));
            }
        }
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    private void validateNewPassword(String newPassword, String confirmPassword) {
        if (newPassword == null || confirmPassword == null || !newPassword.equals(confirmPassword)) {
            throw new BusinessException("A confirmacao da senha nao corresponde.");
        }
        if (newPassword.length() < 8) {
            throw new BusinessException("A senha deve ter pelo menos 8 caracteres.");
        }
        boolean hasLetter = newPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = newPassword.chars().anyMatch(Character::isDigit);
        boolean hasUpper = newPassword.chars().anyMatch(Character::isUpperCase);
        boolean hasSpecial = newPassword.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
        if (!hasLetter || !hasDigit) {
            throw new BusinessException("A senha deve conter letras e numeros.");
        }
        if (!hasUpper && !hasSpecial) {
            throw new BusinessException("A senha deve conter letra maiuscula ou caractere especial.");
        }
    }

    private int resolveExpirationHours(AdminResetPasswordRequest request) {
        Integer requested = request != null ? request.getTemporaryPasswordExpiresInHours() : null;
        if (requested == null) {
            return defaultTemporaryPasswordExpirationHours;
        }
        if (requested < 1 || requested > 720) {
            throw new BusinessException("temporaryPasswordExpiresInHours deve estar entre 1 e 720.");
        }
        return requested;
    }

    private boolean resolveForceChange(AdminResetPasswordRequest request) {
        return request == null || request.getForceChangePassword() == null || Boolean.TRUE.equals(request.getForceChangePassword());
    }

    private AdminResetPasswordResponse toAdminResetResponse(User user, String temporaryPassword, LocalDateTime resetAt) {
        return AdminResetPasswordResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .temporaryPassword(temporaryPassword)
                .temporaryPasswordExpiresAt(user.getTemporaryPasswordExpiresAt())
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .passwordResetRequired(Boolean.TRUE.equals(user.getPasswordResetRequired()))
                .resetAt(resetAt)
                .build();
    }

    private Object resolveActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal jwtPrincipal) {
            return jwtPrincipal.getUserId();
        }
        return authentication != null ? authentication.getName() : null;
    }
}
