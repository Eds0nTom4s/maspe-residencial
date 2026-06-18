package com.restaurante.service;

import com.restaurante.dto.request.PlatformTenantAccessResetPasswordRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.TenantProvisioningAccessRequest;
import com.restaurante.dto.response.PlatformTenantAccessResetPasswordResponse;
import com.restaurante.dto.response.PlatformTenantAccessSummaryResponse;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.dto.response.TenantProvisioningAccessResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.PlatformAdminCannotOwnTenantException;
import com.restaurante.exception.TenantOwnerRequiredException;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodBootstrapService;
import com.restaurante.model.entity.BusinessAccount;
import com.restaurante.model.entity.BusinessAccountMember;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.BusinessAccountMemberEstado;
import com.restaurante.model.enums.BusinessAccountRole;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.BusinessAccountMemberRepository;
import com.restaurante.repository.BusinessAccountRepository;
import com.restaurante.repository.PlanoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.JwtPrincipal;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.provisioning.ProvisioningPlanCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformTenantProvisioningAccessService {

    private final TenantGuard tenantGuard;
    private final TenantProvisioningService tenantProvisioningService;
    private final TenantRepository tenantRepository;
    private final PlanoRepository planoRepository;
    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final BusinessAccountRepository businessAccountRepository;
    private final BusinessAccountMemberRepository businessAccountMemberRepository;
    private final TenantPaymentMethodBootstrapService tenantPaymentMethodBootstrapService;
    private final TenantLimitService tenantLimitService;
    private final UserPasswordManagementService passwordManagementService;

    @Value("${app.client-url:http://localhost:5173}")
    private String clientAppUrl;

    @Transactional
    public TenantProvisioningAccessResponse provisionWithAccess(TenantProvisioningAccessRequest request) {
        tenantGuard.assertPlatformAdmin();

        // GUARD: owner data must be present
        assertOwnerDataPresent(request);

        // GUARD: Platform Admin cannot own a tenant
        Long platformAdminUserId = resolvePlatformAdminUserId();
        assertOwnerIsNotPlatformAdmin(request, platformAdminUserId);

        List<String> warnings = new ArrayList<>();
        OwnerResolution ownerResolution = resolveOwner(request, warnings);

        // GUARD: if owner was resolved to an existing user, that user must not be Platform Admin
        if (ownerResolution.existingUser() != null) {
            assertUserIsNotPlatformAdmin(ownerResolution.existingUser(), platformAdminUserId);
        }

        String temporaryPassword = resolveTemporaryPassword(request);
        Plano plano = resolvePlano(request.getPlanoId());
        BusinessAccount businessAccount = resolveOrCreateBusinessAccount(request);

        if (businessAccount != null
                && businessAccount.getMaxTenants() != null
                && tenantRepository.countByBusinessAccountId(businessAccount.getId()) >= businessAccount.getMaxTenants()) {
            throw new BusinessException("BusinessAccount atingiu o limite maximo de tenants vinculados.");
        }

        ProvisionarTenantRequest provisioningRequest = buildProvisioningRequest(request, plano, businessAccount);
        ProvisionarTenantResponse provisioningResponse = tenantProvisioningService.provisionar(provisioningRequest);

        Tenant tenant = tenantRepository.findById(provisioningResponse.getTenantId())
                .orElseThrow(() -> new BusinessException("Tenant provisionado nao encontrado."));
        tenantPaymentMethodBootstrapService.ensureDefaultsInCurrentTransaction(tenant);
        tenantLimitService.assertCanCreateUser(tenant.getId(), 1);

        UnidadeAtendimento unidadeDefault = provisioningResponse.getUnidadeAtendimentoId() != null
                ? unidadeAtendimentoRepository.findById(provisioningResponse.getUnidadeAtendimentoId()).orElse(null)
                : null;
        User owner = ensureOwnerUser(ownerResolution, request, unidadeDefault, temporaryPassword);
        TenantUser tenantUser = ensureTenantOwner(tenant, owner, unidadeDefault);
        BusinessAccountMember member = businessAccount != null ? ensureBusinessAccountOwnerMember(businessAccount, owner) : null;

        if (businessAccount != null) {
            if (businessAccount.getResponsavel() == null || !businessAccount.getResponsavel().getId().equals(owner.getId())) {
                businessAccount.setResponsavel(owner);
            }
            if (businessAccount.getEstado() == BusinessAccountEstado.RASCUNHO) {
                businessAccount.setEstado(BusinessAccountEstado.ATIVA);
            }
            businessAccountRepository.saveAndFlush(businessAccount);
        }

        return TenantProvisioningAccessResponse.builder()
                .businessAccountId(businessAccount != null ? businessAccount.getId() : null)
                .businessAccountNome(businessAccount != null ? businessAccount.getNome() : null)
                .tenantId(tenant.getId())
                .tenantNome(tenant.getNome())
                .tenantSlug(tenant.getSlug())
                .tenantTipo(tenant.getTipo())
                .tenantEstado(tenant.getEstado())
                .ownerUserId(owner.getId())
                .ownerUsername(owner.getUsername())
                .ownerNome(owner.getNomeCompleto())
                .ownerEmail(owner.getEmail())
                .ownerTelefone(owner.getTelefone())
                .tenantUserId(tenantUser.getId())
                .businessAccountMemberId(member != null ? member.getId() : null)
                .temporaryPassword(temporaryPassword)
                .mustChangePassword(Boolean.TRUE)
                .temporaryPasswordExpiresAt(owner.getTemporaryPasswordExpiresAt())
                .loginUrl(clientAppUrl)
                .tenantSelectRequired(Boolean.TRUE)
                .qrToken(provisioningResponse.getQrToken())
                .qrUrlPublica(provisioningResponse.getQrUrlPublica())
                .warnings(warnings)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Transactional(readOnly = true)
    public PlatformTenantAccessSummaryResponse getAccessSummary(Long tenantId) {
        tenantGuard.assertPlatformAdmin();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant nao encontrado."));

        List<PlatformTenantAccessSummaryResponse.UserAccessSummary> users = tenantUserRepository.findByTenantIdWithUser(tenantId)
                .stream()
                .map(tenantUser -> toUserAccessSummary(tenant.getBusinessAccount(), tenantUser))
                .toList();

        PlatformTenantAccessSummaryResponse.UserAccessSummary owner = users.stream()
                .filter(item -> TenantUserRole.TENANT_OWNER.name().equals(item.getTenantRole()))
                .findFirst()
                .orElse(null);

        return PlatformTenantAccessSummaryResponse.builder()
                .tenantId(tenant.getId())
                .tenantNome(tenant.getNome())
                .tenantSlug(tenant.getSlug())
                .businessAccountId(tenant.getBusinessAccount() != null ? tenant.getBusinessAccount().getId() : null)
                .businessAccountNome(tenant.getBusinessAccount() != null ? tenant.getBusinessAccount().getNome() : null)
                .owner(owner)
                .users(users)
                .build();
    }

    @Transactional
    public PlatformTenantAccessResetPasswordResponse resetTemporaryPassword(Long tenantId,
                                                                            PlatformTenantAccessResetPasswordRequest request) {
        return passwordManagementService.resetPasswordForTenant(tenantId, request);
    }

    private ProvisionarTenantRequest buildProvisioningRequest(TenantProvisioningAccessRequest request,
                                                              Plano plano,
                                                              BusinessAccount businessAccount) {
        String slug = request.getTenantSlug() != null && !request.getTenantSlug().isBlank()
                ? request.getTenantSlug()
                : ProvisioningPlanCalculator.normalizeSlug(request.getTenantNome());
        if (slug == null || slug.isBlank()) {
            slug = "tenant-" + System.currentTimeMillis();
        }

        return ProvisionarTenantRequest.builder()
                .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                        .nome(request.getTenantNome())
                        .slug(slug)
                        .nif(request.getTenantNif())
                        .telefone(request.getTenantTelefone())
                        .email(request.getTenantEmail())
                        .tipo(request.getTenantTipo())
                        .build())
                .planoCodigo(plano.getCodigo())
                .templateCodigo(resolveTemplateCode(request.getTenantTipo()))
                .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                        .nome(request.getTenantNome())
                        .sigla(null)
                        .nif(request.getTenantNif())
                        .telefone(request.getTenantTelefone())
                        .email(request.getTenantEmail())
                        .build())
                .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                        .nome(request.getOwnerNome())
                        .email(request.getOwnerEmail())
                        .telefone(request.getOwnerTelefone())
                        .criarUsuario(false)
                        .build())
                .opcoes(ProvisionarTenantRequest.OpcoesProvisionamento.builder()
                        .ativarTenant(Boolean.TRUE.equals(request.getAtivarTenant()))
                        .build())
                .businessAccountId(businessAccount != null ? businessAccount.getId() : null)
                .build();
    }

    private OwnerResolution resolveOwner(TenantProvisioningAccessRequest request, List<String> warnings) {
        Map<Long, User> matches = new LinkedHashMap<>();
        if (request.getOwnerUsername() != null && !request.getOwnerUsername().isBlank()) {
            userRepository.findByUsername(request.getOwnerUsername().trim()).ifPresent(user -> matches.put(user.getId(), user));
        }
        if (request.getOwnerEmail() != null && !request.getOwnerEmail().isBlank()) {
            userRepository.findByEmail(request.getOwnerEmail().trim().toLowerCase(Locale.ROOT)).ifPresent(user -> matches.put(user.getId(), user));
        }
        if (request.getOwnerTelefone() != null && !request.getOwnerTelefone().isBlank()) {
            userRepository.findByTelefone(request.getOwnerTelefone().trim()).ifPresent(user -> matches.put(user.getId(), user));
        }

        if (matches.size() > 1) {
            throw new BusinessException("Os dados do owner apontam para usuarios diferentes.");
        }

        User existing = matches.values().stream().findFirst().orElse(null);
        String username;
        if (existing != null) {
            username = existing.getUsername();
            if (request.getOwnerUsername() != null
                    && !request.getOwnerUsername().isBlank()
                    && !request.getOwnerUsername().equalsIgnoreCase(existing.getUsername())) {
                warnings.add("Owner existente reutilizado com username atual: " + existing.getUsername());
            }
        } else {
            username = resolveUniqueUsername(request);
        }
        return new OwnerResolution(existing, username);
    }

    private String resolveTemporaryPassword(TenantProvisioningAccessRequest request) {
        if (request.getOwnerPassword() != null && !request.getOwnerPassword().isBlank()) {
            return request.getOwnerPassword();
        }
        if (Boolean.FALSE.equals(request.getGerarSenhaTemporaria())) {
            throw new BusinessException("Owner password obrigatoria quando gerarSenhaTemporaria=false.");
        }
        return passwordManagementService.generateTemporaryPassword();
    }

    private Plano resolvePlano(Long planoId) {
        if (planoId == null) {
            return planoRepository.findByCodigo("PILOTO")
                    .orElseThrow(() -> new BusinessException("Plano padrao PILOTO nao encontrado."));
        }
        return planoRepository.findById(planoId)
                .filter(Plano::getAtivo)
                .orElseThrow(() -> new BusinessException("Plano nao encontrado ou inativo."));
    }

    private BusinessAccount resolveOrCreateBusinessAccount(TenantProvisioningAccessRequest request) {
        if (request.getBusinessAccountId() != null && Boolean.TRUE.equals(request.getCreateBusinessAccount())) {
            throw new BusinessException("Informe businessAccountId ou createBusinessAccount=true, nao ambos.");
        }
        if (request.getBusinessAccountId() != null) {
            return businessAccountRepository.findById(request.getBusinessAccountId())
                    .orElseThrow(() -> new BusinessException("BusinessAccount nao encontrada."));
        }
        if (!Boolean.TRUE.equals(request.getCreateBusinessAccount())) {
            throw new BusinessException("BusinessAccount obrigatoria para o provisionamento.");
        }
        if (request.getBusinessAccountNome() == null || request.getBusinessAccountNome().isBlank()) {
            throw new BusinessException("businessAccountNome e obrigatorio quando createBusinessAccount=true.");
        }
        BusinessAccount account = new BusinessAccount();
        account.setNome(request.getBusinessAccountNome());
        account.setSlug(nextBusinessAccountSlug(request.getBusinessAccountNome()));
        account.setNif(request.getBusinessAccountNif());
        account.setEmail(request.getBusinessAccountEmail());
        account.setTelefone(request.getBusinessAccountTelefone());
        account.setEstado(BusinessAccountEstado.ATIVA);
        account.setMaxTenants(request.getMaxTenants() != null ? request.getMaxTenants() : 1);
        account.setObservacao(request.getObservacao());
        account.setProvisionedAt(LocalDateTime.now());
        account.setProvisionedBy(resolveProvisionedBy());
        return businessAccountRepository.saveAndFlush(account);
    }

    private User ensureOwnerUser(OwnerResolution ownerResolution,
                                 TenantProvisioningAccessRequest request,
                                 UnidadeAtendimento unidadeDefault,
                                 String temporaryPassword) {
        User owner = ownerResolution.existingUser() != null ? ownerResolution.existingUser() : new User();
        if (owner.getId() == null) {
            owner.setUsername(ownerResolution.username());
        }
        owner.setNomeCompleto(request.getOwnerNome());
        if (request.getOwnerEmail() != null && !request.getOwnerEmail().isBlank()) {
            owner.setEmail(request.getOwnerEmail().trim().toLowerCase(Locale.ROOT));
        }
        owner.setTelefone(request.getOwnerTelefone().trim());
        if (owner.getUnidadeAtendimento() == null) {
            owner.setUnidadeAtendimento(unidadeDefault);
        }
        owner.setAtivo(true);
        Set<Role> roles = owner.getRoles() == null || owner.getRoles().isEmpty()
                ? new java.util.HashSet<>()
                : new java.util.HashSet<>(owner.getRoles());
        roles.add(Role.ROLE_GERENTE);
        owner.setRoles(roles);
        passwordManagementService.applyTemporaryPassword(owner, temporaryPassword);
        return userRepository.saveAndFlush(owner);
    }

    private TenantUser ensureTenantOwner(Tenant tenant, User owner, UnidadeAtendimento unidadeDefault) {
        TenantUser tenantUser = tenantUserRepository.findByTenantIdAndUserId(tenant.getId(), owner.getId())
                .orElseGet(TenantUser::new);
        tenantUser.setTenant(tenant);
        tenantUser.setUser(owner);
        tenantUser.setRole(TenantUserRole.TENANT_OWNER);
        tenantUser.setEstado(TenantUserEstado.ATIVO);
        tenantUser.setUnidadeAtendimentoDefault(unidadeDefault);
        return tenantUserRepository.saveAndFlush(tenantUser);
    }

    private BusinessAccountMember ensureBusinessAccountOwnerMember(BusinessAccount businessAccount, User owner) {
        BusinessAccountMember member = businessAccountMemberRepository.findByBusinessAccountIdAndUserId(businessAccount.getId(), owner.getId())
                .orElseGet(BusinessAccountMember::new);
        member.setBusinessAccount(businessAccount);
        member.setUser(owner);
        member.setRole(BusinessAccountRole.OWNER);
        member.setEstado(BusinessAccountMemberEstado.ATIVO);
        return businessAccountMemberRepository.saveAndFlush(member);
    }

    private PlatformTenantAccessSummaryResponse.UserAccessSummary toUserAccessSummary(BusinessAccount businessAccount,
                                                                                      TenantUser tenantUser) {
        BusinessAccountMember member = businessAccount != null
                ? businessAccountMemberRepository.findByBusinessAccountIdAndUserId(businessAccount.getId(), tenantUser.getUser().getId()).orElse(null)
                : null;
        User user = tenantUser.getUser();
        return PlatformTenantAccessSummaryResponse.UserAccessSummary.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nome(user.getNomeCompleto())
                .email(user.getEmail())
                .telefone(user.getTelefone())
                .tenantUserId(tenantUser.getId())
                .tenantRole(tenantUser.getRole() != null ? tenantUser.getRole().name() : null)
                .tenantEstado(tenantUser.getEstado() != null ? tenantUser.getEstado().name() : null)
                .businessAccountMemberId(member != null ? member.getId() : null)
                .businessAccountRole(member != null && member.getRole() != null ? member.getRole().name() : null)
                .businessAccountMemberEstado(member != null && member.getEstado() != null ? member.getEstado().name() : null)
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .passwordResetRequired(Boolean.TRUE.equals(user.getPasswordResetRequired()))
                .temporaryPasswordExpiresAt(user.getTemporaryPasswordExpiresAt())
                .lastPasswordChangedAt(user.getLastPasswordChangedAt())
                .build();
    }

    private String resolveUniqueUsername(TenantProvisioningAccessRequest request) {
        String base = request.getOwnerUsername();
        if (base == null || base.isBlank()) {
            if (request.getOwnerEmail() != null && !request.getOwnerEmail().isBlank()) {
                base = request.getOwnerEmail().trim().toLowerCase(Locale.ROOT);
            } else {
                base = "u" + request.getOwnerTelefone().replaceAll("[^0-9]", "");
            }
        }
        String candidate = normalizeUsername(base);
        if (candidate.isBlank()) {
            candidate = "user" + randomSuffix(6);
        }
        String unique = candidate;
        int suffix = 1;
        while (userRepository.existsByUsername(unique)) {
            unique = candidate + "." + suffix++;
        }
        return unique;
    }

    private String nextBusinessAccountSlug(String nome) {
        String base = ProvisioningPlanCalculator.normalizeSlug(nome);
        if (base == null || base.isBlank()) {
            base = "business-account";
        }
        String candidate = base;
        int suffix = 1;
        while (businessAccountRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String resolveTemplateCode(TenantTipo tipo) {
        return switch (tipo) {
            case RESTAURANTE, FOOD_COURT, CLUBE, INSTITUCIONAL -> "RESTAURANTE_SIMPLES";
            case BAR -> "BAR";
            case EVENTO -> "EVENTO";
            case LOJA -> "LOJA";
            case VENDEDOR_RUA -> "VENDEDOR_RUA";
        };
    }

    private String resolveProvisionedBy() {
        return TenantContextHolder.get()
                .map(ctx -> ctx.userId() != null ? String.valueOf(ctx.userId()) : null)
                .orElseGet(() -> {
                    Long uid = resolvePlatformAdminUserId();
                    return uid != null ? String.valueOf(uid) : null;
                });
    }

    // =========================================================================
    // OWNER GUARDS — enforce that Platform Admin cannot own a tenant/business
    // =========================================================================

    /**
     * Asserts that the request contains at least minimal owner identification data.
     * The request must have ownerNome + (ownerTelefone or ownerEmail or ownerUsername).
     */
    private void assertOwnerDataPresent(TenantProvisioningAccessRequest request) {
        boolean hasName = request.getOwnerNome() != null && !request.getOwnerNome().isBlank();
        boolean hasContact = (request.getOwnerTelefone() != null && !request.getOwnerTelefone().isBlank())
                || (request.getOwnerEmail() != null && !request.getOwnerEmail().isBlank())
                || (request.getOwnerUsername() != null && !request.getOwnerUsername().isBlank());
        if (!hasName || !hasContact) {
            throw new TenantOwnerRequiredException();
        }
    }

    /**
     * Asserts that the owner specified in the request is NOT the currently authenticated Platform Admin.
     * Checks by userId (via TenantContext or JwtPrincipal), email, and username.
     */
    private void assertOwnerIsNotPlatformAdmin(TenantProvisioningAccessRequest request, Long platformAdminUserId) {
        String adminUsername = resolvePlatformAdminUsername();
        String adminEmail = resolvePlatformAdminEmail();

        // Check by email
        if (adminEmail != null && request.getOwnerEmail() != null
                && adminEmail.equalsIgnoreCase(request.getOwnerEmail().trim())) {
            throw new PlatformAdminCannotOwnTenantException();
        }
        // Check by username
        if (adminUsername != null && request.getOwnerUsername() != null
                && adminUsername.equalsIgnoreCase(request.getOwnerUsername().trim())) {
            throw new PlatformAdminCannotOwnTenantException();
        }
        // If email/username matches a User with ROLE_ADMIN, also block
        if (platformAdminUserId != null) {
            userRepository.findById(platformAdminUserId).ifPresent(adminUser -> {
                if (adminUser.getEmail() != null && request.getOwnerEmail() != null
                        && adminUser.getEmail().equalsIgnoreCase(request.getOwnerEmail().trim())) {
                    throw new PlatformAdminCannotOwnTenantException();
                }
                if (adminUser.getTelefone() != null && request.getOwnerTelefone() != null
                        && adminUser.getTelefone().equals(request.getOwnerTelefone().trim())) {
                    throw new PlatformAdminCannotOwnTenantException();
                }
            });
        }
    }

    /**
     * Asserts that a resolved (existing) User entity is not the Platform Admin.
     * Blocks when the user's only role is ROLE_ADMIN (platform-only identity).
     */
    private void assertUserIsNotPlatformAdmin(User user, Long platformAdminUserId) {
        // Block if user IS the authenticated platform admin
        if (platformAdminUserId != null && platformAdminUserId.equals(user.getId())) {
            throw new PlatformAdminCannotOwnTenantException();
        }
        // Block if user holds ONLY ROLE_ADMIN (no business role)
        Set<Role> roles = user.getRoles();
        if (roles != null && !roles.isEmpty()
                && roles.stream().allMatch(r -> r == Role.ROLE_ADMIN)) {
            throw new PlatformAdminCannotOwnTenantException();
        }
    }

    /**
     * Resolves the authenticated Platform Admin's User ID from SecurityContext.
     * Returns null if not identifiable (e.g. test/mock contexts without JwtPrincipal).
     */
    private Long resolvePlatformAdminUserId() {
        // Prefer TenantContext (set by TenantContextFilter)
        Optional<Long> fromCtx = TenantContextHolder.get()
                .map(ctx -> ctx.userId());
        if (fromCtx.isPresent()) {
            return fromCtx.get();
        }
        // Fallback: JwtPrincipal in SecurityContext
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtPrincipal jp) {
            return jp.getUserId();
        }
        return null;
    }

    private String resolvePlatformAdminUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtPrincipal jp) {
            return jp.getUsername();
        }
        return auth != null ? auth.getName() : null;
    }

    private String resolvePlatformAdminEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof JwtPrincipal jp) {
            return jp.getEmail();
        }
        return null;
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return "";
        }
        return username.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9@._-]", "");
    }

    private String randomSuffix(int len) {
        String normalized = UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
        return normalized.substring(0, Math.min(len, normalized.length()));
    }

    private record OwnerResolution(User existingUser, String username) {}
}
