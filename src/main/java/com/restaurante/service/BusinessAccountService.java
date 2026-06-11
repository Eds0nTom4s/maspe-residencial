package com.restaurante.service;

import com.restaurante.billing.repository.TenantSubscriptionRepository;
import com.restaurante.dto.request.BusinessAccountCreateRequest;
import com.restaurante.dto.request.BusinessAccountEstadoUpdateRequest;
import com.restaurante.dto.request.BusinessAccountLimitsUpdateRequest;
import com.restaurante.dto.request.BusinessAccountMemberCreateRequest;
import com.restaurante.dto.request.BusinessAccountMemberEstadoUpdateRequest;
import com.restaurante.dto.request.BusinessAccountMemberRoleUpdateRequest;
import com.restaurante.dto.response.BusinessAccountBillingResponse;
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
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.BusinessAccountLimitOrigin;
import com.restaurante.model.enums.BusinessAccountMemberEstado;
import com.restaurante.model.enums.BusinessAccountRole;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.repository.BusinessAccountLimitOverrideRepository;
import com.restaurante.repository.BusinessAccountMemberRepository;
import com.restaurante.repository.BusinessAccountRepository;
import com.restaurante.repository.SubscricaoRepository;
import com.restaurante.repository.TenantRepository;
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
            upsertMember(account, account.getResponsavel(), BusinessAccountRole.OWNER, BusinessAccountMemberEstado.ATIVO);
        }
        associateTenants(account, request.getTenantIds());
        return toResponse(account);
    }

    @Transactional
    public BusinessAccountResponse atualizarEstado(Long id, BusinessAccountEstadoUpdateRequest request) {
        tenantGuard.assertPlatformAdmin();
        BusinessAccount account = getBusinessAccount(id);
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
