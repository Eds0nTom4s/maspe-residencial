package com.restaurante.security.tenant;

import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.repository.TenantUserAccessVersionRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.security.JwtTokenProvider;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.exception.TenantTokenStaleException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolve TenantContext a partir de:
 * - Authentication (JWT já validado pelo JwtAuthenticationFilter)
 * - Headers (X-Tenant-Id / X-Tenant-Code) quando aplicável
 *
 * Filosofia (Prompt 4):
 * - Não quebra fluxos legados: se não conseguir resolver e não houver seleção explícita, retorna vazio.
 * - Só lança erro em seleção explícita inválida (ex.: header inválido) ou em inconsistência crítica.
 */
@Component
@RequiredArgsConstructor
public class TenantResolver {

    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_TENANT_CODE = "X-Tenant-Code";

    private final ObjectProvider<TenantRepository> tenantRepositoryProvider;
    private final ObjectProvider<TenantUserRepository> tenantUserRepositoryProvider;
    private final ObjectProvider<JwtTokenProvider> jwtTokenProviderProvider;
    private final ObjectProvider<TenantUserAccessVersionRepository> tenantUserAccessVersionRepositoryProvider;

    @Value("${consuma.security.tenant-token.require-access-version:true}")
    private boolean requireAccessVersion;

    public Optional<TenantContext> resolve(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        // Device-authenticated requests (Authorization: Device <token>)
        // DeviceAuthenticationFilter popula SecurityContext com DevicePrincipal; aqui convertemos para TenantContext.
        Object principal = authentication.getPrincipal();
        if (principal instanceof DevicePrincipal d && d.tenantId() != null) {
            return Optional.of(new TenantContext(
                    d.tenantId(),
                    d.tenantCode(),
                    null,
                    extractRoleNames(authentication),
                    TenantResolutionSource.DEVICE_TOKEN,
                    false,
                    false
            ));
        }

        boolean platformAdmin = hasAuthority(authentication, Role.ROLE_ADMIN.name());

        // Preferir resolução via JWT tenant-scoped (tokenType=TENANT)
        Optional<TenantContext> fromTenantToken = resolveFromTenantScopedToken(request, authentication, platformAdmin);
        if (fromTenantToken.isPresent()) {
            return fromTenantToken;
        }

        Optional<Long> selectedTenantId = parseTenantIdHeader(request)
                .or(() -> resolveTenantIdByCodeHeader(request));

        Long userId = extractUserId(authentication).orElse(null);

        // PLATFORM_ADMIN: só resolve tenant quando houver seleção explícita
        if (platformAdmin) {
            if (selectedTenantId.isEmpty()) {
                // Para endpoints platform-scoped (ex.: provisionamento), ainda precisamos de um contexto
                // com platformAdmin=true, mesmo sem tenant selecionado.
                return Optional.of(new TenantContext(
                        null,
                        null,
                        userId,
                        extractRoleNames(authentication),
                        TenantResolutionSource.PLATFORM_ADMIN,
                        true,
                        false
                ));
            }
            Tenant tenant = requireActiveTenant(selectedTenantId.get());
            return Optional.of(new TenantContext(
                    tenant.getId(),
                    tenant.getTenantCode(),
                    userId,
                    extractRoleNames(authentication),
                    TenantResolutionSource.PLATFORM_ADMIN_SELECTION,
                    true,
                    true
            ));
        }

        // Não-admin: se houver seleção explícita, validar membership
        if (selectedTenantId.isPresent()) {
            if (userId == null) {
                throw new BusinessException("Usuário não suporta seleção explícita de tenant.");
            }

            Tenant tenant = requireActiveTenant(selectedTenantId.get());
            TenantUserRepository tenantUserRepository = requireTenantUserRepository();
            boolean belongs = tenantUserRepository.existsByTenantIdAndUserIdAndEstado(
                    tenant.getId(), userId, TenantUserEstado.ATIVO
            );
            if (!belongs) {
                throw new BusinessException("Usuário não pertence ao tenant selecionado.");
            }

            Set<String> roles = extractRoleNames(authentication);
            roles.addAll(extractTenantRoleNames(tenantUserRepository, tenant.getId(), userId));

            return Optional.of(new TenantContext(
                    tenant.getId(),
                    tenant.getTenantCode(),
                    userId,
                    roles,
                    TenantResolutionSource.JWT,
                    false,
                    false
            ));
        }

        // Não-admin: auto-resolve se o user tiver exatamente 1 membership ATIVO
        if (userId != null) {
            TenantUserRepository tenantUserRepository = tenantUserRepositoryProvider.getIfAvailable();
            if (tenantUserRepository == null) {
                return Optional.empty();
            }
            List<TenantUser> memberships = tenantUserRepository.findByUserIdAndEstado(userId, TenantUserEstado.ATIVO);
            if (memberships.size() == 1) {
                Tenant tenant = requireActiveTenant(memberships.get(0).getTenant().getId());
                Set<String> roles = extractRoleNames(authentication);
                roles.addAll(extractTenantRoleNames(tenantUserRepository, tenant.getId(), userId));
                return Optional.of(new TenantContext(
                        tenant.getId(),
                        tenant.getTenantCode(),
                        userId,
                        roles,
                        TenantResolutionSource.JWT,
                        false,
                        false
                ));
            }

            // Se tiver > 1, não resolve automaticamente: exige header (mas não quebra fluxo legado)
            return Optional.empty();
        }

        // Principal não mapeia para User persistido: manter legado
        return Optional.empty();
    }

    private Optional<Long> parseTenantIdHeader(HttpServletRequest request) {
        String raw = request.getHeader(HEADER_TENANT_ID);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(raw.trim()));
        } catch (NumberFormatException e) {
            throw new BusinessException("Header X-Tenant-Id inválido.");
        }
    }

    private Optional<Long> resolveTenantIdByCodeHeader(HttpServletRequest request) {
        String raw = request.getHeader(HEADER_TENANT_CODE);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        TenantRepository tenantRepository = tenantRepositoryProvider.getIfAvailable();
        if (tenantRepository == null) {
            throw new BusinessException("Resolução por tenantCode indisponível.");
        }
        return tenantRepository.findByTenantCode(raw.trim()).map(Tenant::getId);
    }

    private Tenant requireActiveTenant(Long tenantId) {
        TenantRepository tenantRepository = tenantRepositoryProvider.getIfAvailable();
        if (tenantRepository == null) {
            throw new BusinessException("TenantRepository indisponível para resolução de tenant.");
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + tenantId));
        if (tenant.getEstado() != TenantEstado.ATIVO) {
            throw new BusinessException("Tenant não está ativo para operação tenant-aware.");
        }
        return tenant;
    }

    private TenantUserRepository requireTenantUserRepository() {
        TenantUserRepository repo = tenantUserRepositoryProvider.getIfAvailable();
        if (repo == null) {
            throw new BusinessException("TenantUserRepository indisponível para validação de membership.");
        }
        return repo;
    }

    private Optional<Long> extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof User u) {
            return Optional.ofNullable(u.getId());
        }
        return Optional.empty();
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        for (GrantedAuthority a : authentication.getAuthorities()) {
            if (authority.equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private Set<String> extractRoleNames(Authentication authentication) {
        Set<String> roles = new HashSet<>();
        for (GrantedAuthority a : authentication.getAuthorities()) {
            roles.add(a.getAuthority());
        }
        return roles;
    }

    private Set<String> extractTenantRoleNames(TenantUserRepository tenantUserRepository, Long tenantId, Long userId) {
        Set<String> roles = new HashSet<>();
        try {
            tenantUserRepository.findAllByTenantIdAndUserIdAndEstado(tenantId, userId, TenantUserEstado.ATIVO)
                    .forEach(tu -> {
                        if (tu.getRole() != null) {
                            roles.add(tu.getRole().name());
                        }
                    });
        } catch (Exception ignored) {
            // não bloquear resolução se repo não suportar; guard fará enforcement.
        }
        return roles;
    }

    private Optional<TenantContext> resolveFromTenantScopedToken(HttpServletRequest request, Authentication authentication, boolean platformAdmin) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = authHeader.substring(7);
        JwtTokenProvider jwtTokenProvider = jwtTokenProviderProvider.getIfAvailable();
        if (jwtTokenProvider == null) {
            return Optional.empty();
        }
        try {
            var claims = jwtTokenProvider.getClaims(token);
            String tokenType = claims.get("tokenType", String.class);
            if (!"TENANT".equals(tokenType)) {
                return Optional.empty();
            }
            Long tenantId = claims.get("tenantId", Long.class);
            String tenantCode = claims.get("tenantCode", String.class);
            Object tenantRolesObj = claims.get("tenantRoles");
            Integer tokenAccessVersion = claims.get("tenantAccessVersion", Integer.class);

            Long userId = extractUserId(authentication).orElse(claims.get("userId", Long.class));
            if (tenantId == null || userId == null) {
                return Optional.empty();
            }

            // Segurança: valida tenant ATIVO e membership (aceita qualquer estado para poder rejeitar com 403)
            Tenant tenant = requireActiveTenant(tenantId);
            TenantUserRepository tenantUserRepository = requireTenantUserRepository();
            boolean belongs = tenantUserRepository.existsByTenantIdAndUserIdAndEstado(tenant.getId(), userId, TenantUserEstado.ATIVO);
            if (!belongs) {
                // Verificar se existe membership com estado diferente de ATIVO (SUSPENSO, INATIVO)
                boolean existsAny = tenantUserRepository.existsByTenantIdAndUserId(tenant.getId(), userId);
                if (existsAny) {
                    // Membership existe mas está suspensa/inactiva → 403
                    throw new com.restaurante.exception.TenantAccessDeniedException(
                            "Acesso negado: membership não está activa para este tenant."
                    );
                }
                return Optional.empty();
            }

            if (requireAccessVersion) {
                if (tokenAccessVersion == null) {
                    throw new TenantTokenStaleException("Sessão do tenant desatualizada. Selecione novamente o tenant.");
                }
                TenantUserAccessVersionRepository accessRepo = tenantUserAccessVersionRepositoryProvider.getIfAvailable();
                if (accessRepo == null) {
                    throw new BusinessException("Validação de versão indisponível.");
                }
                Integer currentVersion = accessRepo.findAccessVersion(tenant.getId(), userId).orElse(1);
                if (!tokenAccessVersion.equals(currentVersion)) {
                    throw new TenantTokenStaleException("Sessão do tenant desatualizada. Selecione novamente o tenant.");
                }
            }

            Set<String> roles = extractRoleNames(authentication);
            if (tenantRolesObj instanceof java.util.List<?> list) {
                list.forEach(r -> {
                    if (r != null) roles.add(String.valueOf(r));
                });
            }

            return Optional.of(new TenantContext(
                    tenant.getId(),
                    tenant.getTenantCode(),
                    userId,
                    roles,
                    TenantResolutionSource.JWT,
                    platformAdmin,
                    false
            ));
        } catch (com.restaurante.exception.TenantTokenStaleException e) {
            throw e;
        } catch (com.restaurante.exception.TenantAccessDeniedException e) {
            throw e;
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
