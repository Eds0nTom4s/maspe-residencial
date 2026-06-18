package com.restaurante.service;

import com.restaurante.billing.repository.TenantSubscriptionRepository;
import com.restaurante.dto.request.BusinessAccountCreateRequest;
import com.restaurante.dto.request.BusinessAccountEstadoUpdateRequest;
import com.restaurante.dto.request.BusinessAccountLegacyGovernanceBackfillRequest;
import com.restaurante.dto.request.BusinessAccountLimitsUpdateRequest;
import com.restaurante.dto.request.BusinessAccountMemberCreateRequest;
import com.restaurante.dto.request.BusinessAccountMemberEstadoUpdateRequest;
import com.restaurante.dto.request.BusinessAccountMemberRoleUpdateRequest;
import com.restaurante.dto.response.BusinessAccountBillingResponse;
import com.restaurante.dto.response.BusinessAccountGovernanceDiagnosticResponse;
import com.restaurante.dto.response.BusinessAccountLegacyGovernanceBackfillResponse;
import com.restaurante.dto.response.BusinessAccountLimitsResponse;
import com.restaurante.dto.response.BusinessAccountMemberResponse;
import com.restaurante.dto.response.BusinessAccountResponse;
import com.restaurante.dto.response.BusinessAccountSummaryResponse;
import com.restaurante.dto.response.PlatformTenantResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.BusinessAccount;
import com.restaurante.model.entity.BusinessAccountLimitOverride;
import com.restaurante.model.entity.BusinessAccountMember;
import com.restaurante.model.entity.BillingPlan;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.Subscricao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantSubscription;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.BusinessAccountLimitOrigin;
import com.restaurante.model.enums.BusinessAccountMemberEstado;
import com.restaurante.model.enums.BusinessAccountRole;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.BusinessAccountLimitOverrideRepository;
import com.restaurante.repository.BusinessAccountMemberRepository;
import com.restaurante.repository.BusinessAccountRepository;
import com.restaurante.repository.SubscricaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.provisioning.ProvisioningPlanCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class BusinessAccountService {

    private final TenantGuard tenantGuard;
    private final BusinessAccountRepository businessAccountRepository;
    private final BusinessAccountMemberRepository businessAccountMemberRepository;
    private final BusinessAccountLimitOverrideRepository businessAccountLimitOverrideRepository;
    private final TenantRepository tenantRepository;
    private final TenantUserRepository tenantUserRepository;
    private final UserRepository userRepository;
    private final SubscricaoRepository subscricaoRepository;
    private final TenantSubscriptionRepository tenantSubscriptionRepository;
    private final PlatformTenantAccessService platformTenantAccessService;

    @Transactional(readOnly = true)
    public List<BusinessAccountSummaryResponse> listar(Pageable pageable,
                                                       BusinessAccountEstado estado,
                                                       String search,
                                                       Long responsavelUserId,
                                                       Boolean hasTenants) {
        tenantGuard.assertPlatformAdmin();
        return businessAccountRepository.search(estado, search, responsavelUserId, hasTenants, pageable)
                .stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BusinessAccountResponse buscarPorId(Long id) {
        tenantGuard.assertPlatformAdmin();
        return toResponse(getBusinessAccount(id));
    }

    @Transactional(readOnly = true)
    public BusinessAccountResponse buscarPorSlug(String slug) {
        tenantGuard.assertPlatformAdmin();
        BusinessAccount account = businessAccountRepository.findBySlug(normalizeSlug(slug))
                .orElseThrow(() -> new BusinessException("BusinessAccount nao encontrada."));
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public List<PlatformTenantResponse> listarTenants(Long businessAccountId) {
        tenantGuard.assertPlatformAdmin();
        getBusinessAccount(businessAccountId);
        return tenantRepository.findByBusinessAccountIdOrderByIdAsc(businessAccountId)
                .stream()
                .map(platformTenantAccessService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BusinessAccountLimitsResponse buscarLimites(Long businessAccountId) {
        tenantGuard.assertPlatformAdmin();
        return resolveEffectiveLimits(getBusinessAccount(businessAccountId));
    }

    @Transactional(readOnly = true)
    public BusinessAccountBillingResponse buscarBilling(Long businessAccountId) {
        tenantGuard.assertPlatformAdmin();
        return toBillingResponse(getBusinessAccount(businessAccountId));
    }

    @Transactional(readOnly = true)
    public BusinessAccountGovernanceDiagnosticResponse diagnosticarGovernanca(Long businessAccountId) {
        tenantGuard.assertPlatformAdmin();
        return buildGovernanceDiagnostic(getBusinessAccount(businessAccountId), null);
    }

    @Transactional
    public BusinessAccountLegacyGovernanceBackfillResponse executarLegacyGovernanceBackfill(
            Long businessAccountId,
            BusinessAccountLegacyGovernanceBackfillRequest request
    ) {
        tenantGuard.assertPlatformAdmin();
        BusinessAccount account = getBusinessAccount(businessAccountId);
        BusinessAccountGovernanceDiagnosticResponse before = buildGovernanceDiagnostic(account, null);
        BusinessAccountEstado estadoBefore = account.getEstado();
        Long responsavelBefore = account.getResponsavel() != null ? account.getResponsavel().getId() : null;

        if (!Boolean.TRUE.equals(before.getRequiresBackfill())) {
            List<BusinessAccountMember> currentOwners = businessAccountMemberRepository
                    .findByBusinessAccountIdOrderByIdAsc(account.getId())
                    .stream()
                    .filter(member -> member.getRole() == BusinessAccountRole.OWNER
                            && member.getEstado() == BusinessAccountMemberEstado.ATIVO)
                    .toList();
            return BusinessAccountLegacyGovernanceBackfillResponse.builder()
                    .businessAccountId(account.getId())
                    .estadoBefore(estadoBefore)
                    .estadoAfter(account.getEstado())
                    .responsavelUserIdBefore(responsavelBefore)
                    .responsavelUserIdAfter(responsavelBefore)
                    .ownerUserIds(currentOwners.stream().map(member -> member.getUser().getId()).toList())
                    .businessAccountMemberIds(currentOwners.stream().map(BusinessAccountMember::getId).toList())
                    .promotedToAtiva(false)
                    .responsavelUpdated(false)
                    .requiresBackfillBefore(false)
                    .requiresBackfillAfter(false)
                    .status("ALREADY_COMPLETE")
                    .diagnosticBefore(before)
                    .diagnosticAfter(before)
                    .build();
        }

        List<Tenant> activeTenants = tenantRepository.findByBusinessAccountIdOrderByIdAsc(account.getId())
                .stream()
                .filter(tenant -> tenant.getEstado() == TenantEstado.ATIVO)
                .toList();
        if (activeTenants.isEmpty()) {
            throw new BusinessException("Backfill legado exige pelo menos um tenant ativo vinculado a BusinessAccount.");
        }

        List<User> owners = resolveLegacyBackfillOwners(activeTenants, request);
        revokePlatformAdminOwnersFromBusinessAccount(account);
        revokePlatformAdminOwnersFromTenants(activeTenants);

        List<BusinessAccountMember> ownerMembers = new ArrayList<>();
        for (User owner : owners) {
            assertUserIsNotPlatformAdminOwner(owner);
            if (!Boolean.TRUE.equals(owner.getAtivo())) {
                owner.setAtivo(true);
            }
            Set<Role> roles = owner.getRoles() == null ? new HashSet<>() : new HashSet<>(owner.getRoles());
            if (!roles.contains(Role.ROLE_GERENTE)) {
                roles.add(Role.ROLE_GERENTE);
                owner.setRoles(roles);
            }
            userRepository.save(owner);
            ownerMembers.add(upsertMember(account, owner, BusinessAccountRole.OWNER, BusinessAccountMemberEstado.ATIVO));
        }
        userRepository.flush();

        boolean promotedToAtiva = false;
        if (account.getEstado() != BusinessAccountEstado.ATIVA) {
            account.setEstado(BusinessAccountEstado.ATIVA);
            promotedToAtiva = true;
        }

        boolean responsavelUpdated = false;
        User currentResponsavel = account.getResponsavel();
        Set<Long> ownerIds = owners.stream().map(User::getId).collect(java.util.stream.Collectors.toSet());
        if (currentResponsavel == null || isPlatformAdmin(currentResponsavel) || !ownerIds.contains(currentResponsavel.getId())) {
            account.setResponsavel(owners.get(0));
            responsavelUpdated = true;
        }

        if (request != null && request.getReason() != null && !request.getReason().isBlank()) {
            account.setObservacao(mergeObservacao(account.getObservacao(), "Legacy governance backfill: " + request.getReason().trim()));
        }
        account = businessAccountRepository.saveAndFlush(account);

        BusinessAccountGovernanceDiagnosticResponse after = buildGovernanceDiagnostic(account, null);
        return BusinessAccountLegacyGovernanceBackfillResponse.builder()
                .businessAccountId(account.getId())
                .estadoBefore(estadoBefore)
                .estadoAfter(account.getEstado())
                .responsavelUserIdBefore(responsavelBefore)
                .responsavelUserIdAfter(account.getResponsavel() != null ? account.getResponsavel().getId() : null)
                .ownerUserIds(owners.stream().map(User::getId).toList())
                .businessAccountMemberIds(ownerMembers.stream().map(BusinessAccountMember::getId).toList())
                .promotedToAtiva(promotedToAtiva)
                .responsavelUpdated(responsavelUpdated)
                .requiresBackfillBefore(Boolean.TRUE.equals(before.getRequiresBackfill()))
                .requiresBackfillAfter(Boolean.TRUE.equals(after.getRequiresBackfill()))
                .status(Boolean.TRUE.equals(after.getRequiresBackfill()) ? "COMPLETED_WITH_WARNINGS" : "COMPLETED")
                .diagnosticBefore(before)
                .diagnosticAfter(after)
                .build();
    }

    @Transactional
    public BusinessAccountResponse criar(BusinessAccountCreateRequest request) {
        tenantGuard.assertPlatformAdmin();
        String slug = normalizeSlug(request.getSlug());
        if (slug == null || slug.isBlank()) {
            throw new BusinessException("Slug invalido para BusinessAccount.");
        }
        if (businessAccountRepository.existsBySlug(slug)) {
            throw new BusinessException("Ja existe BusinessAccount com este slug.");
        }

        int requestedTenantCount = request.getTenantIds() == null ? 0 : new LinkedHashSet<>(request.getTenantIds()).size();
        int effectiveMaxTenants = normalizeMaxTenants(request.getMaxTenants(), requestedTenantCount);

        BusinessAccount account = new BusinessAccount();
        account.setNome(request.getNome());
        account.setSlug(slug);
        account.setNif(request.getNif());
        account.setEmail(request.getEmail());
        account.setTelefone(request.getTelefone());
        account.setEstado(request.getEstado() != null ? request.getEstado() : BusinessAccountEstado.RASCUNHO);
        account.setObservacao(request.getObservacao());
        account.setMaxTenants(effectiveMaxTenants);
        account.setProvisionedAt(LocalDateTime.now());
        account.setProvisionedBy(resolveProvisionedBy());
        account.setResponsavel(resolveUser(request.getResponsavelUserId()));
        account = businessAccountRepository.saveAndFlush(account);

        if (account.getResponsavel() != null) {
            assertUserIsNotPlatformAdminOwner(account.getResponsavel());
            upsertMember(account, account.getResponsavel(), BusinessAccountRole.OWNER, BusinessAccountMemberEstado.ATIVO);
        }
        ensureCanTransitionToState(account, account.getEstado());
        associateTenants(account, request.getTenantIds());
        return toResponse(account);
    }

    @Transactional
    public BusinessAccountResponse atualizarEstado(Long id, BusinessAccountEstadoUpdateRequest request) {
        tenantGuard.assertPlatformAdmin();
        BusinessAccount account = getBusinessAccount(id);
        ensureCanTransitionToState(account, request.getEstado());
        account.setEstado(request.getEstado());
        if (request.getMotivo() != null && !request.getMotivo().isBlank()) {
            account.setObservacao(mergeObservacao(account.getObservacao(), "Estado " + request.getEstado().name() + ": " + request.getMotivo()));
        }
        return toResponse(businessAccountRepository.saveAndFlush(account));
    }

    @Transactional
    public PlatformTenantResponse associarTenant(Long businessAccountId, Long tenantId) {
        tenantGuard.assertPlatformAdmin();
        BusinessAccount account = getBusinessAccount(businessAccountId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant nao encontrado."));
        ensureTenantCanAttach(account, tenant);
        ensureBusinessAccountCanHoldTenant(account, tenant);
        tenant.setBusinessAccount(account);
        tenantRepository.saveAndFlush(tenant);
        return platformTenantAccessService.toResponse(tenant);
    }

    @Transactional
    public void desassociarTenant(Long businessAccountId, Long tenantId) {
        tenantGuard.assertPlatformAdmin();
        getBusinessAccount(businessAccountId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant nao encontrado."));
        if (tenant.getBusinessAccount() == null || !businessAccountId.equals(tenant.getBusinessAccount().getId())) {
            throw new BusinessException("Tenant nao esta vinculado a esta BusinessAccount.");
        }
        tenant.setBusinessAccount(null);
        tenantRepository.saveAndFlush(tenant);
    }

    @Transactional
    public BusinessAccountMemberResponse adicionarMembro(Long businessAccountId, BusinessAccountMemberCreateRequest request) {
        tenantGuard.assertPlatformAdmin();
        BusinessAccount account = getBusinessAccount(businessAccountId);
        User user = resolveUserRequired(request.getUserId());
        if (request.getRole() == BusinessAccountRole.OWNER) {
            assertUserIsNotPlatformAdminOwner(user);
        }
        BusinessAccountMember member = upsertMember(
                account,
                user,
                request.getRole(),
                request.getEstado() != null ? request.getEstado() : BusinessAccountMemberEstado.ATIVO
        );
        if (account.getResponsavel() == null && request.getRole() == BusinessAccountRole.OWNER) {
            account.setResponsavel(user);
            businessAccountRepository.save(account);
        }
        return toMemberResponse(member);
    }

    @Transactional(readOnly = true)
    public List<BusinessAccountMemberResponse> listarMembros(Long businessAccountId) {
        tenantGuard.assertPlatformAdmin();
        getBusinessAccount(businessAccountId);
        return businessAccountMemberRepository.findByBusinessAccountIdOrderByIdAsc(businessAccountId)
                .stream()
                .map(this::toMemberResponse)
                .toList();
    }

    @Transactional
    public BusinessAccountMemberResponse atualizarEstadoMembro(Long businessAccountId,
                                                              Long memberId,
                                                              BusinessAccountMemberEstadoUpdateRequest request) {
        tenantGuard.assertPlatformAdmin();
        getBusinessAccount(businessAccountId);
        BusinessAccountMember member = businessAccountMemberRepository.findByBusinessAccountIdAndId(businessAccountId, memberId)
                .orElseThrow(() -> new BusinessException("Membro da BusinessAccount nao encontrado."));
        ensureOwnerWillRemainActive(member, request.getEstado(), member.getRole());
        member.setEstado(request.getEstado());
        return toMemberResponse(businessAccountMemberRepository.saveAndFlush(member));
    }

    @Transactional
    public BusinessAccountMemberResponse atualizarRoleMembro(Long businessAccountId,
                                                            Long memberId,
                                                            BusinessAccountMemberRoleUpdateRequest request) {
        tenantGuard.assertPlatformAdmin();
        getBusinessAccount(businessAccountId);
        BusinessAccountMember member = businessAccountMemberRepository.findByBusinessAccountIdAndId(businessAccountId, memberId)
                .orElseThrow(() -> new BusinessException("Membro da BusinessAccount nao encontrado."));
        ensureOwnerWillRemainActive(member, member.getEstado(), request.getRole());
        if (request.getRole() == BusinessAccountRole.OWNER) {
            assertUserIsNotPlatformAdminOwner(member.getUser());
        }
        member.setRole(request.getRole());
        return toMemberResponse(businessAccountMemberRepository.saveAndFlush(member));
    }

    @Transactional
    public BusinessAccountLimitsResponse atualizarLimites(Long businessAccountId,
                                                          BusinessAccountLimitsUpdateRequest request) {
        tenantGuard.assertPlatformAdmin();
        BusinessAccount account = getBusinessAccount(businessAccountId);
        BusinessAccountLimitsResponse current = resolveEffectiveLimits(account);

        if (Boolean.FALSE.equals(request.getAtivo())) {
            businessAccountLimitOverrideRepository.findByBusinessAccountId(businessAccountId)
                    .ifPresent(existing -> {
                        existing.setAtivo(false);
                        existing.setObservacao(request.getObservacao());
                        existing.setConfiguradoEm(LocalDateTime.now());
                        existing.setConfiguradoPor(resolveProvisionedBy());
                        businessAccountLimitOverrideRepository.save(existing);
                    });
            if (request.getMaxTenants() != null) {
                account.setMaxTenants(request.getMaxTenants());
                businessAccountRepository.saveAndFlush(account);
            }
            return resolveEffectiveLimits(account);
        }

        BusinessAccountLimitOverride override = businessAccountLimitOverrideRepository.findByBusinessAccountId(businessAccountId)
                .orElseGet(BusinessAccountLimitOverride::new);
        override.setBusinessAccount(account);
        override.setAtivo(true);
        override.setMaxInstituicoes(firstNonNull(request.getMaxInstituicoes(), override.getMaxInstituicoes(), current.getMaxInstituicoes()));
        override.setMaxUnidadesAtendimento(firstNonNull(request.getMaxUnidadesAtendimento(), override.getMaxUnidadesAtendimento(), current.getMaxUnidadesAtendimento()));
        override.setMaxProdutos(firstNonNull(request.getMaxProdutos(), override.getMaxProdutos(), current.getMaxProdutos()));
        override.setMaxCategorias(firstNonNull(request.getMaxCategorias(), override.getMaxCategorias(), current.getMaxCategorias()));
        override.setMaxUsuarios(firstNonNull(request.getMaxUsuarios(), override.getMaxUsuarios(), current.getMaxUsuarios()));
        override.setMaxQrCodes(firstNonNull(request.getMaxQrCodes(), override.getMaxQrCodes(), current.getMaxQrCodes()));
        override.setMaxDispositivos(firstNonNull(request.getMaxDispositivos(), override.getMaxDispositivos(), current.getMaxDispositivos()));
        override.setMaxPedidosMes(firstNonNull(request.getMaxPedidosMes(), override.getMaxPedidosMes(), current.getMaxPedidosMes()));
        override.setObservacao(request.getObservacao());
        override.setConfiguradoEm(LocalDateTime.now());
        override.setConfiguradoPor(resolveProvisionedBy());
        businessAccountLimitOverrideRepository.saveAndFlush(override);

        if (request.getMaxTenants() != null) {
            account.setMaxTenants(request.getMaxTenants());
            businessAccountRepository.saveAndFlush(account);
        }

        return resolveEffectiveLimits(account);
    }

    @Transactional(readOnly = true)
    public BusinessAccount getBusinessAccount(Long id) {
        return businessAccountRepository.findById(id)
                .orElseThrow(() -> new BusinessException("BusinessAccount nao encontrada."));
    }

    private BusinessAccountSummaryResponse toSummaryResponse(BusinessAccount account) {
        long tenantCount = tenantRepository.countByBusinessAccountId(account.getId());
        return BusinessAccountSummaryResponse.builder()
                .id(account.getId())
                .nome(account.getNome())
                .slug(account.getSlug())
                .estado(account.getEstado())
                .responsavelUserId(account.getResponsavel() != null ? account.getResponsavel().getId() : null)
                .responsavelNome(account.getResponsavel() != null ? account.getResponsavel().getNomeCompleto() : null)
                .maxTenants(account.getMaxTenants())
                .tenantCount(tenantCount)
                .memberCount(businessAccountMemberRepository.countByBusinessAccountId(account.getId()))
                .hasTenants(tenantCount > 0)
                .createdAt(account.getCreatedAt())
                .build();
    }

    private BusinessAccountResponse toResponse(BusinessAccount account) {
        long tenantCount = tenantRepository.countByBusinessAccountId(account.getId());
        return BusinessAccountResponse.builder()
                .id(account.getId())
                .nome(account.getNome())
                .slug(account.getSlug())
                .nif(account.getNif())
                .email(account.getEmail())
                .telefone(account.getTelefone())
                .estado(account.getEstado())
                .responsavelUserId(account.getResponsavel() != null ? account.getResponsavel().getId() : null)
                .responsavelNome(account.getResponsavel() != null ? account.getResponsavel().getNomeCompleto() : null)
                .responsavelEmail(account.getResponsavel() != null ? account.getResponsavel().getEmail() : null)
                .observacao(account.getObservacao())
                .maxTenants(account.getMaxTenants())
                .tenantCount(tenantCount)
                .activeTenantCount(tenantCount)
                .memberCount(businessAccountMemberRepository.countByBusinessAccountId(account.getId()))
                .hasTenants(tenantCount > 0)
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }

    private BusinessAccountMemberResponse toMemberResponse(BusinessAccountMember member) {
        User user = member.getUser();
        return BusinessAccountMemberResponse.builder()
                .id(member.getId())
                .businessAccountId(member.getBusinessAccount().getId())
                .userId(user != null ? user.getId() : null)
                .username(user != null ? user.getUsername() : null)
                .nomeCompleto(user != null ? user.getNomeCompleto() : null)
                .email(user != null ? user.getEmail() : null)
                .telefone(user != null ? user.getTelefone() : null)
                .role(member.getRole())
                .estado(member.getEstado())
                .createdAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .build();
    }

    private void associateTenants(BusinessAccount account, List<Long> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return;
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(tenantIds);
        if (account.getMaxTenants() != null && uniqueIds.size() > account.getMaxTenants()) {
            throw new BusinessException("Quantidade de tenants excede o limite maximo da BusinessAccount.");
        }
        for (Long tenantId : uniqueIds) {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new BusinessException("Tenant nao encontrado."));
            ensureTenantCanAttach(account, tenant);
            ensureBusinessAccountCanHoldTenant(account, tenant);
            tenant.setBusinessAccount(account);
            tenantRepository.save(tenant);
        }
        tenantRepository.flush();
    }

    private void ensureTenantCanAttach(BusinessAccount account, Tenant tenant) {
        if (tenant.getBusinessAccount() != null && !tenant.getBusinessAccount().getId().equals(account.getId())) {
            throw new BusinessException("Tenant ja pertence a outra BusinessAccount.");
        }
        boolean alreadyLinked = tenant.getBusinessAccount() != null && account.getId().equals(tenant.getBusinessAccount().getId());
        long currentCount = tenantRepository.countByBusinessAccountId(account.getId());
        if (!alreadyLinked && account.getMaxTenants() != null && currentCount >= account.getMaxTenants()) {
            throw new BusinessException("BusinessAccount atingiu o limite maximo de tenants vinculados.");
        }
    }

    private void ensureCanTransitionToState(BusinessAccount account, BusinessAccountEstado targetEstado) {
        if (targetEstado != BusinessAccountEstado.ATIVA) {
            return;
        }
        BusinessAccountGovernanceDiagnosticResponse diagnostic = buildGovernanceDiagnostic(account, targetEstado);
        List<String> blockingReasons = diagnostic.getBlockingReasons();
        if (blockingReasons != null && !blockingReasons.isEmpty()) {
            throw new BusinessException("BusinessAccount nao pode ser ativada sem owner real ativo: " + String.join(",", blockingReasons));
        }
    }

    private void ensureBusinessAccountCanHoldTenant(BusinessAccount account, Tenant tenant) {
        if (tenant.getEstado() != TenantEstado.ATIVO) {
            return;
        }
        BusinessAccountGovernanceDiagnosticResponse diagnostic = buildGovernanceDiagnostic(account, account.getEstado());
        List<String> blockingReasons = diagnostic.getBlockingReasons();
        if (blockingReasons != null && !blockingReasons.isEmpty()) {
            throw new BusinessException("Tenant ativo nao pode ser vinculado a BusinessAccount sem owner real ativo: " + String.join(",", blockingReasons));
        }
    }

    private BusinessAccountGovernanceDiagnosticResponse buildGovernanceDiagnostic(BusinessAccount account,
                                                                                 BusinessAccountEstado targetEstado) {
        BusinessAccountEstado effectiveEstado = targetEstado != null ? targetEstado : account.getEstado();
        List<BusinessAccountMember> members = businessAccountMemberRepository.findByBusinessAccountIdOrderByIdAsc(account.getId());
        List<Tenant> tenants = tenantRepository.findByBusinessAccountIdOrderByIdAsc(account.getId());

        User responsavel = account.getResponsavel();
        boolean hasResponsavel = responsavel != null;
        boolean responsavelAtivo = responsavel != null && Boolean.TRUE.equals(responsavel.getAtivo());
        boolean responsavelPlatformAdmin = responsavel != null && isPlatformAdmin(responsavel);

        List<BusinessAccountGovernanceDiagnosticResponse.OwnerItem> owners = members.stream()
                .filter(member -> member.getRole() == BusinessAccountRole.OWNER)
                .map(member -> {
                    User user = member.getUser();
                    return BusinessAccountGovernanceDiagnosticResponse.OwnerItem.builder()
                            .userId(user != null ? user.getId() : null)
                            .username(user != null ? user.getUsername() : null)
                            .nome(user != null ? user.getNomeCompleto() : null)
                            .email(user != null ? user.getEmail() : null)
                            .ativo(user != null ? user.getAtivo() : null)
                            .platformAdmin(isPlatformAdmin(user))
                            .role(member.getRole())
                            .estado(member.getEstado())
                            .build();
                })
                .toList();

        boolean hasOwnerMember = !owners.isEmpty();
        boolean hasPlatformAdminOwner = owners.stream().anyMatch(owner -> Boolean.TRUE.equals(owner.getPlatformAdmin()));
        boolean hasActiveOwnerMember = members.stream()
                .anyMatch(member -> member.getRole() == BusinessAccountRole.OWNER
                        && member.getEstado() == BusinessAccountMemberEstado.ATIVO
                        && member.getUser() != null
                        && Boolean.TRUE.equals(member.getUser().getAtivo()));
        boolean responsavelIsActiveOwner = responsavel != null && members.stream()
                .anyMatch(member -> member.getRole() == BusinessAccountRole.OWNER
                        && member.getEstado() == BusinessAccountMemberEstado.ATIVO
                        && member.getUser() != null
                        && member.getUser().getId().equals(responsavel.getId())
                        && Boolean.TRUE.equals(member.getUser().getAtivo())
                        && !isPlatformAdmin(member.getUser()));
        boolean hasValidBusinessOwner = responsavelIsActiveOwner && !responsavelPlatformAdmin;

        List<BusinessAccountGovernanceDiagnosticResponse.TenantItem> tenantItems = new ArrayList<>();
        long activeTenantCount = 0L;
        boolean hasActiveTenantWithoutOwner = false;
        boolean hasPlatformAdminTenantOwner = false;
        for (Tenant tenant : tenants) {
            if (tenant.getEstado() == TenantEstado.ATIVO) {
                activeTenantCount++;
            }
            List<com.restaurante.model.entity.TenantUser> tenantOwners = tenantUserRepository.findByTenantIdWithUser(tenant.getId())
                    .stream()
                    .filter(tu -> tu.getRole() == TenantUserRole.TENANT_OWNER && tu.getEstado() == TenantUserEstado.ATIVO)
                    .toList();
            boolean tenantHasOwner = tenantOwners.stream()
                    .anyMatch(tu -> tu.getUser() != null && Boolean.TRUE.equals(tu.getUser().getAtivo()) && !isPlatformAdmin(tu.getUser()));
            boolean tenantHasPlatformAdminOwner = tenantOwners.stream()
                    .anyMatch(tu -> isPlatformAdmin(tu.getUser()));
            if (tenant.getEstado() == TenantEstado.ATIVO && !tenantHasOwner) {
                hasActiveTenantWithoutOwner = true;
            }
            if (tenantHasPlatformAdminOwner) {
                hasPlatformAdminTenantOwner = true;
            }
            tenantItems.add(BusinessAccountGovernanceDiagnosticResponse.TenantItem.builder()
                    .tenantId(tenant.getId())
                    .nome(tenant.getNome())
                    .slug(tenant.getSlug())
                    .tenantCode(tenant.getTenantCode())
                    .estado(tenant.getEstado())
                    .hasTenantOwner(tenantHasOwner)
                    .hasPlatformAdminTenantOwner(tenantHasPlatformAdminOwner)
                    .owners(tenantOwners.stream()
                            .map(tu -> {
                                User user = tu.getUser();
                                return BusinessAccountGovernanceDiagnosticResponse.TenantOwnerItem.builder()
                                        .userId(user != null ? user.getId() : null)
                                        .username(user != null ? user.getUsername() : null)
                                        .nome(user != null ? user.getNomeCompleto() : null)
                                        .email(user != null ? user.getEmail() : null)
                                        .ativo(user != null ? user.getAtivo() : null)
                                        .platformAdmin(isPlatformAdmin(user))
                                        .role(tu.getRole())
                                        .estado(tu.getEstado())
                                        .build();
                            })
                            .toList())
                    .build());
        }

        List<String> blockingReasons = new ArrayList<>();
        if (effectiveEstado == BusinessAccountEstado.ATIVA || activeTenantCount > 0) {
            if (!hasResponsavel) {
                blockingReasons.add("BUSINESS_ACCOUNT_RESPONSAVEL_MISSING");
            } else if (!responsavelAtivo) {
                blockingReasons.add("BUSINESS_ACCOUNT_RESPONSAVEL_INACTIVE");
            } else if (responsavelPlatformAdmin) {
                blockingReasons.add("PLATFORM_ADMIN_IS_BUSINESS_ACCOUNT_RESPONSAVEL");
            }
            if (!hasOwnerMember) {
                blockingReasons.add("BUSINESS_ACCOUNT_OWNER_MISSING");
            } else if (!hasActiveOwnerMember) {
                blockingReasons.add("BUSINESS_ACCOUNT_ACTIVE_OWNER_MISSING");
            }
            if (hasPlatformAdminOwner) {
                blockingReasons.add("PLATFORM_ADMIN_IS_BUSINESS_ACCOUNT_OWNER");
            }
            if (hasResponsavel && !responsavelIsActiveOwner) {
                blockingReasons.add("BUSINESS_ACCOUNT_RESPONSAVEL_NOT_ACTIVE_OWNER");
            }
            if (hasActiveTenantWithoutOwner) {
                blockingReasons.add("ACTIVE_TENANT_OWNER_MISSING");
            }
            if (hasPlatformAdminTenantOwner) {
                blockingReasons.add("PLATFORM_ADMIN_IS_TENANT_OWNER");
            }
        }
        if (activeTenantCount > 0 && effectiveEstado != BusinessAccountEstado.ATIVA) {
            blockingReasons.add("ACTIVE_TENANT_LINKED_TO_NON_ACTIVE_BUSINESS_ACCOUNT");
        }

        List<String> warnings = new ArrayList<>();
        if (account.getEstado() == BusinessAccountEstado.RASCUNHO && members.isEmpty()) {
            warnings.add("RASCUNHO_SEM_OWNER_PERMITIDO_APENAS_ANTES_DA_OPERACAO");
        }
        boolean ownerGovernanceReady = hasValidBusinessOwner
                && !hasPlatformAdminOwner
                && !hasPlatformAdminTenantOwner
                && !hasActiveTenantWithoutOwner;

        return BusinessAccountGovernanceDiagnosticResponse.builder()
                .businessAccountId(account.getId())
                .businessAccountNome(account.getNome())
                .businessAccountSlug(account.getSlug())
                .estado(account.getEstado())
                .responsavelUserId(responsavel != null ? responsavel.getId() : null)
                .responsavelNome(responsavel != null ? responsavel.getNomeCompleto() : null)
                .responsavelEmail(responsavel != null ? responsavel.getEmail() : null)
                .responsavelAtivo(responsavel != null ? responsavel.getAtivo() : null)
                .responsavelPlatformAdmin(responsavelPlatformAdmin)
                .hasResponsavel(hasResponsavel)
                .hasMembers(!members.isEmpty())
                .hasOwnerMember(hasOwnerMember)
                .hasActiveOwnerMember(hasActiveOwnerMember)
                .hasValidBusinessOwner(hasValidBusinessOwner)
                .hasPlatformAdminAsBusinessAccountOwner(hasPlatformAdminOwner)
                .memberCount((long) members.size())
                .linkedTenantCount((long) tenants.size())
                .activeTenantCount(activeTenantCount)
                .hasActiveTenants(activeTenantCount > 0)
                .canActivate(ownerGovernanceReady)
                .canLinkActiveTenant(account.getEstado() == BusinessAccountEstado.ATIVA && ownerGovernanceReady)
                .canResetOwnerLogin(hasValidBusinessOwner)
                .requiresBackfill(!blockingReasons.isEmpty() || (activeTenantCount > 0 && !ownerGovernanceReady))
                .blockingReasons(blockingReasons)
                .warnings(warnings)
                .owners(owners)
                .tenants(tenantItems)
                .build();
    }

    private List<User> resolveLegacyBackfillOwners(List<Tenant> activeTenants,
                                                   BusinessAccountLegacyGovernanceBackfillRequest request) {
        if (request != null && request.getOwnerUserId() != null) {
            User explicitOwner = resolveUserRequired(request.getOwnerUserId());
            assertUserIsNotPlatformAdminOwner(explicitOwner);
            boolean isTenantOwner = activeTenants.stream()
                    .anyMatch(tenant -> tenantUserRepository.existsByTenantIdAndUserIdAndRoleAndEstado(
                            tenant.getId(),
                            explicitOwner.getId(),
                            TenantUserRole.TENANT_OWNER,
                            TenantUserEstado.ATIVO
                    ));
            if (!isTenantOwner) {
                throw new BusinessException("Usuario informado precisa ser TENANT_OWNER ativo de pelo menos um tenant vinculado.");
            }
            ensureActiveTenantsHaveRealOwners(activeTenants);
            return List.of(explicitOwner);
        }

        List<User> owners = new ArrayList<>();
        Set<Long> ownerIds = new LinkedHashSet<>();
        for (Tenant tenant : activeTenants) {
            List<User> tenantOwners = tenantUserRepository.findByTenantIdWithUser(tenant.getId())
                    .stream()
                    .filter(tu -> tu.getRole() == TenantUserRole.TENANT_OWNER && tu.getEstado() == TenantUserEstado.ATIVO)
                    .map(TenantUser::getUser)
                    .filter(user -> !isPlatformAdmin(user))
                    .toList();
            if (tenantOwners.isEmpty()) {
                throw new BusinessException("Tenant ativo " + tenant.getId() + " nao possui TENANT_OWNER real; execute ownership-backfill do tenant antes.");
            }
            for (User owner : tenantOwners) {
                if (ownerIds.add(owner.getId())) {
                    owners.add(owner);
                }
            }
        }
        return owners;
    }

    private void ensureActiveTenantsHaveRealOwners(List<Tenant> activeTenants) {
        for (Tenant tenant : activeTenants) {
            boolean hasRealOwner = tenantUserRepository.findByTenantIdWithUser(tenant.getId())
                    .stream()
                    .anyMatch(tu -> tu.getRole() == TenantUserRole.TENANT_OWNER
                            && tu.getEstado() == TenantUserEstado.ATIVO
                            && !isPlatformAdmin(tu.getUser()));
            if (!hasRealOwner) {
                throw new BusinessException("Tenant ativo " + tenant.getId() + " nao possui TENANT_OWNER real; execute ownership-backfill do tenant antes.");
            }
        }
    }

    private void revokePlatformAdminOwnersFromBusinessAccount(BusinessAccount account) {
        List<BusinessAccountMember> members = businessAccountMemberRepository.findByBusinessAccountIdOrderByIdAsc(account.getId());
        for (BusinessAccountMember member : members) {
            if (member.getRole() == BusinessAccountRole.OWNER
                    && member.getEstado() == BusinessAccountMemberEstado.ATIVO
                    && isPlatformAdmin(member.getUser())) {
                businessAccountMemberRepository.delete(member);
            }
        }
        businessAccountMemberRepository.flush();
    }

    private void revokePlatformAdminOwnersFromTenants(List<Tenant> activeTenants) {
        for (Tenant tenant : activeTenants) {
            List<TenantUser> tenantUsers = tenantUserRepository.findByTenantIdWithUser(tenant.getId());
            for (TenantUser tenantUser : tenantUsers) {
                if (tenantUser.getRole() == TenantUserRole.TENANT_OWNER
                        && tenantUser.getEstado() == TenantUserEstado.ATIVO
                        && isPlatformAdmin(tenantUser.getUser())) {
                    tenantUserRepository.delete(tenantUser);
                }
            }
        }
        tenantUserRepository.flush();
    }

    private void assertUserIsNotPlatformAdminOwner(User user) {
        if (isPlatformAdmin(user)) {
            throw new BusinessException("Platform Admin nao pode ser OWNER da BusinessAccount.");
        }
    }

    private boolean isPlatformAdmin(User user) {
        if (user == null) {
            return false;
        }
        Set<Role> roles = user.getRoles();
        return roles != null && !roles.isEmpty() && roles.stream().allMatch(role -> role == Role.ROLE_ADMIN);
    }

    private void ensureOwnerWillRemainActive(BusinessAccountMember member,
                                             BusinessAccountMemberEstado nextEstado,
                                             BusinessAccountRole nextRole) {
        boolean isCurrentOwner = member.getRole() == BusinessAccountRole.OWNER;
        boolean isCurrentlyActive = member.getEstado() == BusinessAccountMemberEstado.ATIVO;
        boolean willRemainOwner = nextRole == BusinessAccountRole.OWNER;
        boolean willRemainActive = nextEstado == BusinessAccountMemberEstado.ATIVO;
        if (isCurrentOwner && isCurrentlyActive && (!willRemainOwner || !willRemainActive)) {
            long activeOwners = businessAccountMemberRepository.countByBusinessAccountIdAndRoleAndEstado(
                    member.getBusinessAccount().getId(),
                    BusinessAccountRole.OWNER,
                    BusinessAccountMemberEstado.ATIVO
            );
            if (activeOwners <= 1) {
                throw new BusinessException("A BusinessAccount precisa manter pelo menos um OWNER ativo.");
            }
        }
    }

    private BusinessAccountLimitsResponse resolveEffectiveLimits(BusinessAccount account) {
        BusinessAccountLimitOverride override = businessAccountLimitOverrideRepository
                .findByBusinessAccountIdAndAtivoTrue(account.getId())
                .orElse(null);
        List<Plano> planos = resolveLegacyPlans(account.getId());
        boolean hasPlanLimits = !planos.isEmpty();

        Integer planMaxInstituicoes = maxPlanValue(planos, Plano::getMaxInstituicoes);
        Integer planMaxUnidades = maxPlanValue(planos, Plano::getMaxUnidadesAtendimento);
        Integer planMaxProdutos = maxPlanValue(planos, Plano::getMaxProdutos);
        Integer planMaxCategorias = maxPlanValue(planos, Plano::getMaxCategorias);
        Integer planMaxUsuarios = maxPlanValue(planos, Plano::getMaxUsuarios);
        Integer planMaxQrCodes = maxPlanValue(planos, Plano::getMaxQrCodes);
        Integer planMaxDispositivos = maxPlanValue(planos, Plano::getMaxDispositivos);
        String planCodes = joinDistinct(planos, Plano::getCodigo);
        String planNames = joinDistinct(planos, Plano::getNome);

        return BusinessAccountLimitsResponse.builder()
                .businessAccountId(account.getId())
                .businessAccountNome(account.getNome())
                .origin(override != null ? BusinessAccountLimitOrigin.OVERRIDE : hasPlanLimits ? BusinessAccountLimitOrigin.PLAN : BusinessAccountLimitOrigin.DEFAULT)
                .basePlanoCodigo(planCodes)
                .basePlanoNome(planNames)
                .maxTenants(account.getMaxTenants())
                .maxInstituicoes(firstNonNull(override != null ? override.getMaxInstituicoes() : null, planMaxInstituicoes))
                .maxUnidadesAtendimento(firstNonNull(override != null ? override.getMaxUnidadesAtendimento() : null, planMaxUnidades))
                .maxProdutos(firstNonNull(override != null ? override.getMaxProdutos() : null, planMaxProdutos))
                .maxCategorias(firstNonNull(override != null ? override.getMaxCategorias() : null, planMaxCategorias))
                .maxUsuarios(firstNonNull(override != null ? override.getMaxUsuarios() : null, planMaxUsuarios))
                .maxQrCodes(firstNonNull(override != null ? override.getMaxQrCodes() : null, planMaxQrCodes))
                .maxDispositivos(firstNonNull(override != null ? override.getMaxDispositivos() : null, planMaxDispositivos))
                .maxPedidosMes(override != null ? override.getMaxPedidosMes() : null)
                .overrideAtivo(override != null ? override.getAtivo() : null)
                .overrideObservacao(override != null ? override.getObservacao() : null)
                .updatedAt(override != null ? override.getUpdatedAt() : account.getUpdatedAt())
                .build();
    }

    private BusinessAccountBillingResponse toBillingResponse(BusinessAccount account) {
        List<Tenant> tenants = tenantRepository.findByBusinessAccountIdOrderByIdAsc(account.getId());
        List<BusinessAccountBillingResponse.TenantBillingItem> items = new ArrayList<>();
        Set<String> legacyPlanCodes = new LinkedHashSet<>();
        Set<String> billingPlanCodes = new LinkedHashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        long activeSubscriptionCount = 0;
        String currency = "AOA";

        for (Tenant tenant : tenants) {
            Subscricao subscricao = subscricaoRepository.findByTenantIdAndEstado(tenant.getId(), SubscricaoEstado.ATIVA)
                    .orElse(null);
            TenantSubscription tenantSubscription = tenantSubscriptionRepository.findTopByTenantIdOrderByIdDesc(tenant.getId())
                    .orElse(null);
            Plano legacyPlan = subscricao != null ? subscricao.getPlano() : null;
            BillingPlan billingPlan = tenantSubscription != null ? tenantSubscription.getBillingPlan() : null;
            if (legacyPlan != null) {
                legacyPlanCodes.add(legacyPlan.getCodigo());
            }
            if (billingPlan != null) {
                billingPlanCodes.add(billingPlan.getCode());
            }
            BigDecimal monthlyAmount = billingPlan != null && billingPlan.getBasePrice() != null
                    ? billingPlan.getBasePrice()
                    : legacyPlan != null && legacyPlan.getPrecoMensal() != null ? legacyPlan.getPrecoMensal() : BigDecimal.ZERO;
            total = total.add(monthlyAmount);
            if (tenantSubscription != null || subscricao != null) {
                activeSubscriptionCount++;
            }
            if (tenantSubscription != null && tenantSubscription.getCurrency() != null && !tenantSubscription.getCurrency().isBlank()) {
                currency = tenantSubscription.getCurrency();
            }
            items.add(BusinessAccountBillingResponse.TenantBillingItem.builder()
                    .tenantId(tenant.getId())
                    .tenantNome(tenant.getNome())
                    .tenantCode(tenant.getTenantCode())
                    .legacyPlanCode(legacyPlan != null ? legacyPlan.getCodigo() : null)
                    .legacyPlanName(legacyPlan != null ? legacyPlan.getNome() : null)
                    .legacySubscriptionStatus(subscricao != null && subscricao.getEstado() != null ? subscricao.getEstado().name() : null)
                    .billingPlanCode(billingPlan != null ? billingPlan.getCode() : null)
                    .billingPlanName(billingPlan != null ? billingPlan.getName() : null)
                    .billingSubscriptionStatus(tenantSubscription != null && tenantSubscription.getStatus() != null ? tenantSubscription.getStatus().name() : null)
                    .currency(tenantSubscription != null ? tenantSubscription.getCurrency() : currency)
                    .monthlyAmount(monthlyAmount)
                    .legacySubscriptionEndDate(subscricao != null ? subscricao.getFimEm() : null)
                    .billingCurrentPeriodEnd(tenantSubscription != null ? tenantSubscription.getCurrentPeriodEnd() : null)
                    .autoRenew(tenantSubscription != null ? tenantSubscription.isAutoRenew() : null)
                    .build());
        }

        return BusinessAccountBillingResponse.builder()
                .businessAccountId(account.getId())
                .businessAccountNome(account.getNome())
                .moeda(currency)
                .linkedTenantCount((long) tenants.size())
                .activeSubscriptionCount(activeSubscriptionCount)
                .totalMensalEstimado(total)
                .legacyPlanCodes(new ArrayList<>(legacyPlanCodes))
                .billingPlanCodes(new ArrayList<>(billingPlanCodes))
                .tenants(items)
                .build();
    }

    private List<Plano> resolveLegacyPlans(Long businessAccountId) {
        List<Plano> planos = new ArrayList<>();
        for (Tenant tenant : tenantRepository.findByBusinessAccountIdOrderByIdAsc(businessAccountId)) {
            subscricaoRepository.findByTenantIdAndEstado(tenant.getId(), SubscricaoEstado.ATIVA)
                    .map(Subscricao::getPlano)
                    .ifPresent(planos::add);
        }
        return planos;
    }

    private Integer maxPlanValue(List<Plano> planos, Function<Plano, Integer> extractor) {
        Integer result = null;
        for (Plano plano : planos) {
            Integer value = extractor.apply(plano);
            if (value != null && (result == null || value > result)) {
                result = value;
            }
        }
        return result;
    }

    private String joinDistinct(List<Plano> planos, Function<Plano, String> extractor) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Plano plano : planos) {
            String value = extractor.apply(plano);
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return values.isEmpty() ? null : String.join(",", values);
    }

    private BusinessAccountMember upsertMember(BusinessAccount account,
                                               User user,
                                               BusinessAccountRole role,
                                               BusinessAccountMemberEstado estado) {
        BusinessAccountMember member = businessAccountMemberRepository.findByBusinessAccountIdAndUserId(account.getId(), user.getId())
                .orElseGet(BusinessAccountMember::new);
        member.setBusinessAccount(account);
        member.setUser(user);
        member.setRole(role);
        member.setEstado(estado);
        return businessAccountMemberRepository.saveAndFlush(member);
    }

    private User resolveUser(Long userId) {
        if (userId == null) {
            return null;
        }
        return resolveUserRequired(userId);
    }

    private User resolveUserRequired(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Usuario nao encontrado."));
    }

    private int normalizeMaxTenants(Integer requested, int tenantCount) {
        if (requested != null && requested > 0) {
            return Math.max(requested, Math.max(tenantCount, 1));
        }
        return Math.max(tenantCount, 1);
    }

    private String normalizeSlug(String slug) {
        return ProvisioningPlanCalculator.normalizeSlug(slug);
    }

    private String resolveProvisionedBy() {
        return TenantContextHolder.get()
                .map(ctx -> ctx.userId() != null ? String.valueOf(ctx.userId()) : null)
                .orElse(null);
    }

    private String mergeObservacao(String atual, String adicional) {
        if (atual == null || atual.isBlank()) {
            return adicional;
        }
        return atual + " | " + adicional;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
