package com.restaurante.service.business;

import com.restaurante.billing.repository.TenantSubscriptionRepository;
import com.restaurante.businesstemplate.BusinessTemplateService;
import com.restaurante.businesstemplate.dto.BusinessTemplatePreviewResponse;
import com.restaurante.businesstemplate.dto.BusinessTemplateProvisionRequest;
import com.restaurante.businesstemplate.dto.BusinessTemplateProvisionResponse;
import com.restaurante.dto.business.BusinessProvisioningContracts.*;
import com.restaurante.dto.business.BusinessProvisioningContracts;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ConflictException;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.repository.*;
import com.restaurante.security.tenant.TenantGuard;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BusinessProvisioningService {
    private final TenantGuard tenantGuard;
    private final BusinessAccountRepository accounts;
    private final BusinessAccountMemberRepository accountMembers;
    private final BusinessProvisioningPreviewRepository previews;
    private final BusinessProvisioningOperationRepository operations;
    private final BusinessAccountGovernanceEventRepository governanceEvents;
    private final PlanoRepository planos;
    private final TenantRepository tenants;
    private final SubscricaoRepository subscricoes;
    private final TenantSubscriptionRepository billingSubscriptions;
    private final InstituicaoRepository instituicoes;
    private final UnidadeAtendimentoRepository unidades;
    private final TenantUserRepository tenantUsers;
    private final UserRepository users;
    private final BusinessTemplateService templates;
    private final BusinessAccountGovernanceService governance;
    private final CanonicalCommandSupport commands;
    private final PlatformTransactionManager transactionManager;

    @Value("${consuma.business-provisioning.preview-ttl-minutes:15}")
    private long previewTtlMinutes;

    @Transactional
    public BusinessPreviewResponse preview(Long accountId, BusinessPreviewRequest raw, String key,
                                           HttpServletRequest http) {
        tenantGuard.assertPlatformAdmin();
        commands.requireKey(key);
        BusinessAccount account = lockAccount(accountId);
        BusinessPreviewRequest request = normalize(raw);
        String fp = commands.fingerprint(Map.of("contract", BusinessProvisioningContracts.CONTRACT_VERSION,
                "accountId", accountId, "payload", request));
        BusinessProvisioningPreview replay = previews
                .findByBusinessAccountIdAndIdempotencyKey(accountId, key).orElse(null);
        if (replay != null) {
            if (!Objects.equals(replay.getRequestFingerprint(), fp)) throw new ConflictException("IDEMPOTENCY_CONFLICT");
            return withReplay(commands.read(replay.getResultJson(), BusinessPreviewResponse.class), true);
        }
        validateAccountVersion(account, request.accountVersion());
        validateAccountForBusiness(account);
        validateCapacity(account);
        Plano plano = resolveExplicitPlan(request.planoCodigo());
        validateBusinessData(request);
        validateAccesses(account, request.acessos());

        String templateCode = templateCode(request.vertical());
        BusinessTemplateProvisionRequest templateRequest = toTemplateRequest(account, request);
        BusinessTemplatePreviewResponse templatePreview = templates.preview(templateCode + "_V1", templateRequest);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(previewTtlMinutes);
        BusinessPreviewResponse response = new BusinessPreviewResponse(
                UUID.randomUUID().toString(), fp, expiresAt, accountId,
                List.of("TENANT", "SUBSCRICAO", "INSTITUICAO", "UNIDADE_ATENDIMENTO", "TENANT_USER", "RECURSOS_TEMPLATE"),
                plano.getCodigo(), templatePreview.getLimites(), request.vertical(), templateCode, 1,
                templatePreview.getRecursosPlanejados(), templatePreview.getPoliticas(),
                safe(templatePreview.getAvisos()), safe(templatePreview.getBloqueios()),
                Boolean.TRUE.equals(templatePreview.getPermitido()), false);

        CanonicalCommandSupport.Actor actor = commands.actor(http);
        BusinessProvisioningPreview entity = new BusinessProvisioningPreview();
        entity.setPreviewId(response.previewId());
        entity.setBusinessAccount(account);
        entity.setActorUserId(actor.userId());
        entity.setIdempotencyKey(key);
        entity.setRequestFingerprint(fp);
        entity.setContractVersion(BusinessProvisioningContracts.CONTRACT_VERSION);
        entity.setTemplateCode(templateCode);
        entity.setTemplateVersion(1);
        entity.setPlanoCodigo(plano.getCodigo());
        entity.setPayloadJson(commands.json(request));
        entity.setResultJson(commands.json(response));
        entity.setStatus("ACTIVE");
        entity.setExpiresAt(expiresAt);
        entity.setCorrelationId(actor.correlationId());
        entity.setActorRoles(actor.roles());
        entity.setIpAddress(actor.ip());
        entity.setUserAgent(actor.userAgent());
        previews.saveAndFlush(entity);
        return response;
    }

    public ProvisioningOperationResponse provision(Long accountId, BusinessProvisionRequest request, String key,
                                                   HttpServletRequest http) {
        tenantGuard.assertPlatformAdmin();
        commands.requireKey(key);
        String commandFingerprint = commands.fingerprint(Map.of("contract", "BUSINESS_PROVISION_COMMAND_V1",
                "accountId", accountId, "payload", request));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ProvisionRegistration registration = tx.execute(status ->
                registerProvision(accountId, request, key, commandFingerprint, http));
        if (registration == null) throw new IllegalStateException("Falha ao registar operação de provisionamento.");
        if (registration.replay()) return registration.response();
        try {
            ProvisioningOperationResponse response = tx.execute(status ->
                    executeProvision(registration.operationId(), accountId, request, commandFingerprint));
            if (response == null) throw new IllegalStateException("Operação de provisionamento sem resultado.");
            return response;
        } catch (RuntimeException error) {
            tx.executeWithoutResult(status -> markProvisionFailure(registration.operationId(), error));
            throw error;
        }
    }

    private ProvisionRegistration registerProvision(Long accountId, BusinessProvisionRequest request, String key,
                                                     String commandFingerprint, HttpServletRequest http) {
        BusinessAccount account = lockAccount(accountId);
        BusinessProvisioningOperation existing = operations
                .findByBusinessAccountIdAndIdempotencyKey(accountId, key).orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.getRequestFingerprint(), commandFingerprint)) {
                throw new ConflictException("IDEMPOTENCY_CONFLICT");
            }
            return new ProvisionRegistration(existing.getId(), true, operationResponse(existing, true));
        }
        if (!Boolean.TRUE.equals(request.confirmed())) throw new BusinessException("Confirmação explícita é obrigatória.");
        validateAccountVersion(account, request.accountVersion());
        validateAccountForBusiness(account);
        validateCapacity(account);

        BusinessProvisioningPreview preview = previews.findByPreviewId(request.previewId())
                .orElseThrow(() -> new BusinessException("PREVIEW_NOT_FOUND"));
        if (!Objects.equals(preview.getBusinessAccount().getId(), accountId)) throw new BusinessException("PREVIEW_ACCOUNT_MISMATCH");
        if (!Objects.equals(preview.getRequestFingerprint(), request.requestFingerprint())) {
            throw new ConflictException("PREVIEW_FINGERPRINT_MISMATCH");
        }
        if (!"ACTIVE".equals(preview.getStatus())) throw new ConflictException("PREVIEW_ALREADY_CONSUMED");
        if (!preview.getExpiresAt().isAfter(LocalDateTime.now())) {
            preview.setStatus("EXPIRED");
            previews.save(preview);
            throw new ConflictException("PREVIEW_EXPIRED");
        }
        BusinessPreviewResponse previewResponse = commands.read(preview.getResultJson(), BusinessPreviewResponse.class);
        if (!Boolean.TRUE.equals(previewResponse.allowedToProvision())) throw new ConflictException("PREVIEW_HAS_BLOCKERS");
        BusinessPreviewRequest logicalPayload = commands.read(preview.getPayloadJson(), BusinessPreviewRequest.class);
        resolveExplicitPlan(logicalPayload.planoCodigo());
        validateBusinessData(logicalPayload);
        validateAccesses(account, logicalPayload.acessos());

        CanonicalCommandSupport.Actor actor = commands.actor(http);
        BusinessProvisioningOperation operation = new BusinessProvisioningOperation();
        operation.setOperationId(UUID.randomUUID().toString());
        operation.setBusinessAccount(account);
        operation.setActorUserId(actor.userId());
        operation.setIdempotencyKey(key);
        operation.setRequestFingerprint(commandFingerprint);
        operation.setPreviewId(preview.getPreviewId());
        operation.setStatus("PENDING");
        operation.setStartedAt(LocalDateTime.now());
        operation.setCorrelationId(actor.correlationId());
        operation.setActorRoles(actor.roles());
        operation.setIpAddress(actor.ip());
        operation.setUserAgent(actor.userAgent());
        operation = operations.saveAndFlush(operation);
        return new ProvisionRegistration(operation.getId(), false, null);
    }

    private ProvisioningOperationResponse executeProvision(Long operationId, Long accountId,
                                                            BusinessProvisionRequest request,
                                                            String commandFingerprint) {
        BusinessAccount account = lockAccount(accountId);
        BusinessProvisioningOperation operation = operations.findById(operationId)
                .orElseThrow(() -> new IllegalStateException("Operação de provisionamento não encontrada."));
        if (!Objects.equals(operation.getRequestFingerprint(), commandFingerprint)) {
            throw new ConflictException("IDEMPOTENCY_CONFLICT");
        }
        if (!"PENDING".equals(operation.getStatus())) return operationResponse(operation, true);
        validateAccountVersion(account, request.accountVersion());
        validateAccountForBusiness(account);
        validateCapacity(account);
        BusinessProvisioningPreview preview = previews.findByPreviewId(operation.getPreviewId())
                .orElseThrow(() -> new BusinessException("PREVIEW_NOT_FOUND"));
        if (!Objects.equals(preview.getBusinessAccount().getId(), accountId)) throw new BusinessException("PREVIEW_ACCOUNT_MISMATCH");
        if (!Objects.equals(preview.getRequestFingerprint(), request.requestFingerprint())) {
            throw new ConflictException("PREVIEW_FINGERPRINT_MISMATCH");
        }
        if (!"ACTIVE".equals(preview.getStatus())) throw new ConflictException("PREVIEW_ALREADY_CONSUMED");
        if (!preview.getExpiresAt().isAfter(LocalDateTime.now())) throw new ConflictException("PREVIEW_EXPIRED");
        BusinessPreviewRequest logicalPayload = commands.read(preview.getPayloadJson(), BusinessPreviewRequest.class);
        resolveExplicitPlan(logicalPayload.planoCodigo());
        validateBusinessData(logicalPayload);
        validateAccesses(account, logicalPayload.acessos());
        operation.setStatus("RUNNING");
        operations.saveAndFlush(operation);

        BusinessTemplateProvisionRequest templateRequest = toTemplateRequest(account, logicalPayload);
        BusinessTemplatePreviewResponse revalidated = templates.preview(preview.getTemplateCode() + "_V" + preview.getTemplateVersion(), templateRequest);
        if (!Boolean.TRUE.equals(revalidated.getPermitido())) throw new ConflictException("CRITICAL_INVARIANT_CHANGED_AFTER_PREVIEW");
        BusinessTemplateProvisionResponse result = templates.provision(
                preview.getTemplateCode() + "_V" + preview.getTemplateVersion(), templateRequest);
        Tenant tenant = tenants.findByIdForUpdate(result.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Tenant provisionado não encontrado."));
        if (tenant.getBusinessAccount() == null || !Objects.equals(tenant.getBusinessAccount().getId(), accountId)) {
            throw new IllegalStateException("Tenant provisionado sem BusinessAccount obrigatória.");
        }
        tenant.setEstado(TenantEstado.RASCUNHO);
        tenant.setProvisioningSource("CANONICAL_BUSINESS_ACCOUNT_API");
        tenants.saveAndFlush(tenant);
        createAdditionalAccesses(account, tenant, result.getUnidadeAtendimentoId(), logicalPayload.acessos());

        preview.setStatus("CONSUMED");
        preview.setConsumedAt(LocalDateTime.now());
        previews.save(preview);
        operation.setTenant(tenant);
        operation.setStatus("SUCCEEDED");
        operation.setCompletedAt(LocalDateTime.now());
        operation.setResultJson(commands.json(result));
        operation = operations.saveAndFlush(operation);
        return operationResponse(operation, false);
    }

    private void markProvisionFailure(Long operationId, RuntimeException error) {
        BusinessProvisioningOperation operation = operations.findById(operationId).orElse(null);
        if (operation == null || "SUCCEEDED".equals(operation.getStatus())) return;
        operation.setStatus(error instanceof TransientDataAccessException ? "FAILED_RETRYABLE" : "FAILED_FINAL");
        operation.setCompletedAt(LocalDateTime.now());
        operation.setErrorCode(errorCode(error));
        operation.setErrorMessage(sanitizedProvisioningError(error));
        operations.saveAndFlush(operation);
    }

    @Transactional(readOnly = true)
    public ProvisioningOperationResponse operation(String operationId) {
        tenantGuard.assertPlatformAdmin();
        return operationResponse(operations.findByOperationId(operationId)
                .orElseThrow(() -> new BusinessException("PROVISIONING_OPERATION_NOT_FOUND")), false);
    }

    @Transactional(readOnly = true)
    public ProvisioningOperationResponse operation(Long accountId, String key) {
        tenantGuard.assertPlatformAdmin();
        return operationResponse(operations.findByBusinessAccountIdAndIdempotencyKey(accountId, key)
                .orElseThrow(() -> new BusinessException("PROVISIONING_OPERATION_NOT_FOUND")), false);
    }

    @Transactional(readOnly = true)
    public BusinessReadinessResponse readiness(Long accountId, Long tenantId) {
        tenantGuard.assertPlatformAdmin();
        BusinessAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new BusinessException("BusinessAccount não encontrada."));
        Tenant tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado."));
        if (tenant.getBusinessAccount() == null || !Objects.equals(tenant.getBusinessAccount().getId(), accountId)) {
            throw new BusinessException("Tenant não pertence à BusinessAccount indicada.");
        }
        List<ReadinessCheck> checks = new ArrayList<>();
        checks.add(check("ACCOUNT_ACTIVE", account.getEstado() == BusinessAccountEstado.ATIVA, account.getEstado().name()));
        checks.add(check("ACCOUNT_OWNER", ownerInvariant(account), "pointer e OWNER activo único"));
        checks.add(check("TENANT_PERSISTED", tenant.getId() != null, String.valueOf(tenant.getId())));
        checks.add(check("BUSINESS_ACCOUNT_LINKED", tenant.getBusinessAccount() != null, String.valueOf(accountId)));
        Subscricao legacy = subscricoes.findByTenantIdAndEstado(tenantId, SubscricaoEstado.ATIVA).orElse(null);
        checks.add(check("CANONICAL_PLAN_SUBSCRIPTION", legacy != null,
                legacy == null ? "subscrição legacy activa ausente" : legacy.getPlano().getCodigo()));
        TenantSubscription billing = billingSubscriptions.findTopByTenantIdOrderByIdDesc(tenantId).orElse(null);
        boolean billingCompatible = billing == null || legacy == null
                || Objects.equals(billing.getBillingPlan().getCode(), legacy.getPlano().getCodigo());
        checks.add(check("BILLING_PROJECTION_COMPATIBLE", billingCompatible,
                billing == null ? "projection billing ausente; Plano/Subscricao é normativo"
                        : billing.getBillingPlan().getCode()));
        checks.add(check("INSTITUTION", instituicoes.countByTenantId(tenantId) > 0, "instituição tenant-scoped"));
        checks.add(check("UNIT", unidades.countByTenantId(tenantId) > 0, "unidade tenant-scoped"));
        boolean adminAccess = tenantUsers.countByTenantIdAndRoleAndEstado(tenantId, TenantUserRole.TENANT_OWNER, TenantUserEstado.ATIVO) == 1
                || tenantUsers.countByTenantIdAndRoleAndEstado(tenantId, TenantUserRole.TENANT_ADMIN, TenantUserEstado.ATIVO) > 0;
        checks.add(check("OPERATIONAL_ADMIN_ACCESS", adminAccess, "TENANT_OWNER único ou TENANT_ADMIN activo"));
        checks.add(check("TEMPLATE_VERSION", tenant.getTemplateCode() != null && tenant.getTemplateVersion() != null,
                tenant.getTemplateCode() + "_V" + tenant.getTemplateVersion()));
        checks.add(check("TENANT_NOT_ACTIVE_PREMATURELY", tenant.getEstado() == TenantEstado.RASCUNHO || tenant.getEstado() == TenantEstado.ATIVO,
                tenant.getEstado().name()));
        List<String> blockers = checks.stream().filter(c -> !c.ready()).map(ReadinessCheck::code).toList();
        return new BusinessReadinessResponse(accountId, tenantId, blockers.isEmpty(), checks, blockers);
    }

    @Transactional
    public BusinessReadinessResponse activateBusiness(Long accountId, Long tenantId,
                                                      BusinessActivationRequest request, String key,
                                                      HttpServletRequest http) {
        tenantGuard.assertPlatformAdmin();
        commands.requireKey(key);
        BusinessAccount account = lockAccount(accountId);
        String scope = "BUSINESS_ACCOUNT:" + accountId;
        String fp = commands.fingerprint(Map.of("contract", "BUSINESS_ACTIVATION_V1", "accountId", accountId,
                "tenantId", tenantId, "payload", request));
        BusinessAccountGovernanceEvent replay = governanceEvents
                .findByScopeKeyAndActionAndIdempotencyKey(scope, "BUSINESS_ACTIVATED", key).orElse(null);
        if (replay != null) {
            if (!Objects.equals(replay.getRequestFingerprint(), fp)) throw new ConflictException("IDEMPOTENCY_CONFLICT");
            return readiness(accountId, tenantId);
        }
        validateAccountVersion(account, request.accountVersion());
        Tenant tenant = tenants.findByIdForUpdate(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado."));
        if (!Objects.equals(tenant.getVersion(), request.tenantVersion())) throw new ConflictException("OPTIMISTIC_VERSION_CONFLICT");
        BusinessReadinessResponse readiness = readiness(accountId, tenantId);
        if (!readiness.ready()) throw new ConflictException("BUSINESS_READINESS_INCOMPLETE: " + String.join(",", readiness.blockers()));
        if (tenant.getEstado() != TenantEstado.RASCUNHO && tenant.getEstado() != TenantEstado.ATIVO) {
            throw new ConflictException("TENANT_STATE_INVALID_FOR_ACTIVATION");
        }
        tenant.setEstado(TenantEstado.ATIVO);
        tenants.saveAndFlush(tenant);
        saveActivationEvent(account, tenant, scope, key, fp, request.reason(), http);
        return readiness(accountId, tenantId);
    }

    private BusinessPreviewRequest normalize(BusinessPreviewRequest request) {
        if (request == null) throw new BusinessException("Payload de preview obrigatório.");
        BusinessData b = request.negocio();
        if (b == null) return request;
        BusinessData normalizedBusiness = new BusinessData(trim(b.nomeNegocio()), normalizeSlug(b.slug()),
                upper(b.tenantCode()), b.tipo(), trim(b.nif()), trim(b.telefone()), lower(b.email()),
                trim(b.endereco()), trim(b.provincia()), trim(b.municipio()));
        AccessConfiguration accesses = request.acessos() == null ? null
                : new AccessConfiguration(request.acessos().strategy(), request.acessos().additionalAccesses() == null
                ? List.of() : request.acessos().additionalAccesses().stream()
                .sorted(Comparator.comparing(AdditionalOperationalAccess::userId)
                        .thenComparing(v -> v.tenantRole().name())).toList());
        return new BusinessPreviewRequest(request.accountVersion(), upper(request.planoCodigo()), request.vertical(),
                normalizedBusiness, request.ponto(), request.rest(), accesses);
    }

    private BusinessTemplateProvisionRequest toTemplateRequest(BusinessAccount account, BusinessPreviewRequest request) {
        User owner = account.getResponsavel();
        BusinessData b = request.negocio();
        return BusinessTemplateProvisionRequest.builder()
                .planoCodigo(request.planoCodigo())
                .businessAccountId(account.getId())
                .tenant(BusinessTemplateProvisionRequest.TenantInfo.builder()
                        .nomeNegocio(b.nomeNegocio()).slug(b.slug()).tenantCode(b.tenantCode()).tipo(b.tipo())
                        .nif(b.nif()).telefone(b.telefone()).email(b.email()).build())
                .owner(BusinessTemplateProvisionRequest.OwnerInfo.builder()
                        .existingUserId(owner.getId())
                        .nome(owner.getNomeCompleto() == null ? owner.getUsername() : owner.getNomeCompleto())
                        .telefone(owner.getTelefone()).email(owner.getEmail()).build())
                .localizacao(BusinessTemplateProvisionRequest.LocalizacaoInfo.builder()
                        .endereco(b.endereco()).provincia(b.provincia()).municipio(b.municipio()).build())
                .ponto(request.ponto()).rest(request.rest()).build();
    }

    private void validateBusinessData(BusinessPreviewRequest request) {
        BusinessData b = request.negocio();
        if (b == null || blank(b.nomeNegocio()) || blank(b.slug())) throw new BusinessException("Dados do negócio são obrigatórios.");
        if (blank(b.telefone()) && blank(b.email())) throw new BusinessException("Negócio deve possuir email ou telefone.");
        if (!blank(b.nif()) && !b.nif().matches("[A-Za-z0-9-]{5,30}")) throw new BusinessException("NIF inválido.");
        if (request.vertical() == BusinessVertical.CONSUMA_PONTO
                && b.tipo() != TenantTipo.VENDEDOR_RUA && b.tipo() != TenantTipo.LOJA && b.tipo() != TenantTipo.EVENTO) {
            throw new BusinessException("Tipo de negócio incompatível com CONSUMA_PONTO.");
        }
        if (request.vertical() == BusinessVertical.CONSUMA_REST
                && b.tipo() != TenantTipo.RESTAURANTE && b.tipo() != TenantTipo.BAR) {
            throw new BusinessException("Tipo de negócio incompatível com CONSUMA_REST.");
        }
    }

    private void validateAccesses(BusinessAccount account, AccessConfiguration config) {
        if (config == null || config.strategy() != OperationalAccessStrategy.ACCOUNT_OWNER_AS_TENANT_OWNER) {
            throw new BusinessException("Estratégia explícita ACCOUNT_OWNER_AS_TENANT_OWNER é obrigatória.");
        }
        Set<Long> seen = new HashSet<>();
        for (AdditionalOperationalAccess access : config.additionalAccesses() == null ? List.<AdditionalOperationalAccess>of() : config.additionalAccesses()) {
            if (!seen.add(access.userId())) throw new BusinessException("Acesso operacional duplicado.");
            if (access.tenantRole() != TenantUserRole.TENANT_ADMIN && access.tenantRole() != TenantUserRole.TENANT_FINANCE) {
                throw new BusinessException("Acessos adicionais aceitam apenas TENANT_ADMIN ou TENANT_FINANCE.");
            }
            if (!accountMembers.existsByBusinessAccountIdAndUserIdAndEstado(account.getId(), access.userId(), BusinessAccountMemberEstado.ATIVO)) {
                throw new BusinessException("Acesso operacional exige gestor activo da mesma BusinessAccount.");
            }
            users.findById(access.userId()).filter(u -> Boolean.TRUE.equals(u.getAtivo()))
                    .orElseThrow(() -> new BusinessException("Gestor operacional inactivo ou inexistente."));
        }
    }

    private void createAdditionalAccesses(BusinessAccount account, Tenant tenant, Long unitId,
                                          AccessConfiguration config) {
        UnidadeAtendimento unit = unitId == null ? null : unidades.findByIdAndTenantId(unitId, tenant.getId()).orElse(null);
        for (AdditionalOperationalAccess access : config.additionalAccesses() == null ? List.<AdditionalOperationalAccess>of() : config.additionalAccesses()) {
            User user = users.findById(access.userId()).orElseThrow();
            TenantUser link = new TenantUser();
            link.setTenant(tenant);
            link.setUser(user);
            link.setRole(access.tenantRole());
            link.setEstado(TenantUserEstado.ATIVO);
            link.setUnidadeAtendimentoDefault(unit);
            tenantUsers.save(link);
        }
        tenantUsers.flush();
    }

    private void validateAccountForBusiness(BusinessAccount account) {
        if (account.getEstado() != BusinessAccountEstado.RASCUNHO && account.getEstado() != BusinessAccountEstado.ATIVA) {
            throw new ConflictException("BUSINESS_ACCOUNT_STATE_BLOCKS_PROVISIONING");
        }
        governance.assertOwnerInvariant(account);
    }

    private void validateCapacity(BusinessAccount account) {
        long current = tenants.countByBusinessAccountId(account.getId());
        if (account.getMaxTenants() == null || current >= account.getMaxTenants()) {
            throw new ConflictException("BUSINESS_ACCOUNT_MAX_TENANTS_EXCEEDED");
        }
    }

    private Plano resolveExplicitPlan(String code) {
        if (blank(code)) throw new BusinessException("planoCodigo explícito é obrigatório.");
        return planos.findByCodigo(upper(code)).filter(Plano::getAtivo)
                .orElseThrow(() -> new BusinessException("PLANO_INVALIDO"));
    }

    private BusinessAccount lockAccount(Long id) {
        return accounts.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException("BusinessAccount não encontrada."));
    }

    private void validateAccountVersion(BusinessAccount account, Long expected) {
        if (expected == null || !Objects.equals(account.getVersion(), expected)) {
            throw new ConflictException("OPTIMISTIC_VERSION_CONFLICT");
        }
    }

    private boolean ownerInvariant(BusinessAccount account) {
        if (account.getResponsavel() == null || !Boolean.TRUE.equals(account.getResponsavel().getAtivo())) return false;
        return accountMembers.countByBusinessAccountIdAndRoleAndEstado(account.getId(), BusinessAccountRole.OWNER,
                BusinessAccountMemberEstado.ATIVO) == 1
                && accountMembers.existsByBusinessAccountIdAndUserIdAndRoleInAndEstado(account.getId(),
                account.getResponsavel().getId(), List.of(BusinessAccountRole.OWNER), BusinessAccountMemberEstado.ATIVO);
    }

    private ProvisioningOperationResponse operationResponse(BusinessProvisioningOperation operation, boolean replay) {
        BusinessTemplateProvisionResponse result = operation.getResultJson() == null ? null
                : commands.read(operation.getResultJson(), BusinessTemplateProvisionResponse.class);
        return new ProvisioningOperationResponse(operation.getOperationId(), operation.getBusinessAccount().getId(),
                operation.getIdempotencyKey(), operation.getRequestFingerprint(), operation.getPreviewId(),
                operation.getStatus(), operation.getTenant() == null ? null : operation.getTenant().getId(),
                operation.getStartedAt(), operation.getCompletedAt(), operation.getErrorCode(), operation.getErrorMessage(),
                operation.getCorrelationId(), "SUCCEEDED".equals(operation.getStatus()), replay, result);
    }

    private void saveActivationEvent(BusinessAccount account, Tenant tenant, String scope, String key, String fp,
                                     String reason, HttpServletRequest http) {
        CanonicalCommandSupport.Actor actor = commands.actor(http);
        BusinessAccountGovernanceEvent event = new BusinessAccountGovernanceEvent();
        event.setBusinessAccount(account);
        event.setScopeKey(scope);
        event.setAction("BUSINESS_ACTIVATED");
        event.setIdempotencyKey(key);
        event.setRequestFingerprint(fp);
        event.setActorUserId(actor.userId());
        event.setActorRoles(actor.roles());
        event.setCorrelationId(actor.correlationId());
        event.setIpAddress(actor.ip());
        event.setUserAgent(actor.userAgent());
        event.setBeforeState(commands.json(Map.of("tenantId", tenant.getId(), "estado", "RASCUNHO")));
        event.setAfterState(commands.json(Map.of("tenantId", tenant.getId(), "estado", "ATIVO", "reason", reason)));
        event.setResultAccountId(account.getId());
        governanceEvents.saveAndFlush(event);
    }

    private BusinessPreviewResponse withReplay(BusinessPreviewResponse v, boolean replay) {
        return new BusinessPreviewResponse(v.previewId(), v.requestFingerprint(), v.expiresAt(), v.accountId(),
                v.entidadesPlaneadas(), v.planoCodigo(), v.limites(), v.vertical(), v.templateCode(),
                v.templateVersion(), v.recursos(), v.politicas(), v.warnings(), v.blockers(),
                v.allowedToProvision(), replay);
    }

    private static String errorCode(RuntimeException error) {
        String message = error.getMessage();
        if (message != null && message.matches("[A-Z0-9_:-]+")) {
            return message.length() <= 120 ? message : message.substring(0, 120);
        }
        String code = error.getClass().getSimpleName().toUpperCase(Locale.ROOT);
        return code.length() <= 120 ? code : code.substring(0, 120);
    }

    private String sanitizedProvisioningError(RuntimeException error) {
        if (error instanceof BusinessException || error instanceof ConflictException) {
            return commands.sanitize(error);
        }
        if (error instanceof TransientDataAccessException) {
            return "Falha transiente de persistência; a operação pode ser retomada de forma governada.";
        }
        return "Falha interna de provisionamento; consulte o errorCode e a correlationId.";
    }

    private record ProvisionRegistration(Long operationId, boolean replay,
                                         ProvisioningOperationResponse response) {}

    private static ReadinessCheck check(String code, boolean ready, String detail) {
        return new ReadinessCheck(code, ready, detail);
    }
    private static String templateCode(BusinessVertical vertical) { return vertical.name(); }
    private static String normalizeSlug(String v) { return v == null ? null : v.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\-]+", "-").replaceAll("-{2,}", "-").replaceAll("^-|-$", ""); }
    private static String trim(String v) { return v == null ? null : v.trim(); }
    private static String upper(String v) { return v == null ? null : v.trim().toUpperCase(Locale.ROOT); }
    private static String lower(String v) { return v == null ? null : v.trim().toLowerCase(Locale.ROOT); }
    private static boolean blank(String v) { return v == null || v.isBlank(); }
    private static <T> List<T> safe(List<T> v) { return v == null ? List.of() : List.copyOf(v); }
}
