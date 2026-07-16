package com.restaurante.service;

import com.restaurante.dto.request.OnboardingRequestApproveRequest;
import com.restaurante.dto.request.OnboardingRequestCreateRequest;
import com.restaurante.dto.request.OnboardingRequestRejectRequest;
import com.restaurante.dto.response.OnboardingRequestResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ConflictException;
import com.restaurante.dto.business.BusinessProvisioningContracts.CanonicalAccountCreateRequest;
import com.restaurante.dto.business.BusinessProvisioningContracts.PrincipalOwner;
import com.restaurante.dto.business.BusinessProvisioningContracts.PrincipalOwnerStrategy;
import com.restaurante.model.entity.BusinessAccount;
import com.restaurante.model.entity.OnboardingRequest;
import com.restaurante.model.entity.BusinessAccountGovernanceEvent;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.OnboardingPaymentStatus;
import com.restaurante.model.enums.OnboardingRequestStatus;
import com.restaurante.repository.BusinessAccountGovernanceEventRepository;
import com.restaurante.repository.OnboardingRequestRepository;
import com.restaurante.repository.PlanoRepository;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.provisioning.ProvisioningPlanCalculator;
import com.restaurante.service.business.BusinessAccountGovernanceService;
import com.restaurante.service.business.CanonicalCommandSupport;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PlatformOnboardingRequestService {

    private final TenantGuard tenantGuard;
    private final OnboardingRequestRepository onboardingRequestRepository;
    private final PlanoRepository planoRepository;
    private final BusinessAccountGovernanceService governanceService;
    private final BusinessAccountGovernanceEventRepository governanceEvents;
    private final CanonicalCommandSupport commands;

    @Transactional(readOnly = true)
    public List<OnboardingRequestResponse> listar(Pageable pageable,
                                                  OnboardingRequestStatus status,
                                                  String search) {
        tenantGuard.assertPlatformAdmin();
        return onboardingRequestRepository.search(status, search, pageable)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OnboardingRequestResponse buscarPorId(Long id) {
        tenantGuard.assertPlatformAdmin();
        return toResponse(getRequest(id));
    }

    @Transactional
    public OnboardingRequestResponse criar(OnboardingRequestCreateRequest request) {
        tenantGuard.assertPlatformAdmin();
        Plano plano = resolvePlano(request.getPlanoCodigo());
        OnboardingRequest onboardingRequest = new OnboardingRequest();
        onboardingRequest.setNomeSolicitante(request.getNomeSolicitante());
        onboardingRequest.setTelefone(request.getTelefone());
        onboardingRequest.setEmail(request.getEmail());
        onboardingRequest.setNomeNegocio(request.getNomeNegocio());
        onboardingRequest.setNif(request.getNif());
        onboardingRequest.setTipoNegocio(request.getTipoNegocio());
        onboardingRequest.setPlano(plano);
        onboardingRequest.setValor(request.getValor());
        onboardingRequest.setMoeda(request.getMoeda() != null && !request.getMoeda().isBlank() ? request.getMoeda() : "AOA");
        onboardingRequest.setObservacao(request.getObservacao());
        onboardingRequest.setStatus(OnboardingRequestStatus.PENDENTE);
        onboardingRequest.setStatusPagamento(resolveInitialPaymentStatus(request.getValor()));
        onboardingRequest.setNotificationStatus("NAO_ENVIADO");
        onboardingRequest.setNotificationMessage("Pedido registado para validacao interna.");
        return toResponse(onboardingRequestRepository.saveAndFlush(onboardingRequest));
    }

    @Transactional
    public OnboardingRequestResponse aprovar(Long id, OnboardingRequestApproveRequest request,
                                             String idempotencyKey, HttpServletRequest http) {
        tenantGuard.assertPlatformAdmin();
        commands.requireKey(idempotencyKey);
        CanonicalCommandSupport.Actor actor = commands.actor(http);
        String fingerprint = commands.fingerprint(Map.of("contract", "ONBOARDING_APPROVAL_V1",
                "onboardingId", id, "payload", request));
        OnboardingRequest onboardingRequest = onboardingRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException("Pedido de onboarding nao encontrado."));
        if (onboardingRequest.getApprovalIdempotencyKey() != null) {
            if (!Objects.equals(onboardingRequest.getApprovalIdempotencyKey(), idempotencyKey)
                    || !Objects.equals(onboardingRequest.getApprovalFingerprint(), fingerprint)) {
                throw new ConflictException("IDEMPOTENCY_CONFLICT");
            }
            return toResponse(onboardingRequest);
        }
        if (onboardingRequest.getStatus() == OnboardingRequestStatus.REJEITADO
                || onboardingRequest.getStatus() == OnboardingRequestStatus.CANCELADO) {
            throw new BusinessException("Pedido de onboarding nao pode ser aprovado a partir do estado atual.");
        }

        if (request.getStatusPagamento() != null) {
            onboardingRequest.setStatusPagamento(request.getStatusPagamento());
        }
        if (request.getObservacao() != null && !request.getObservacao().isBlank()) {
            onboardingRequest.setObservacao(mergeObservacao(onboardingRequest.getObservacao(), request.getObservacao()));
        }

        Long businessAccountId = resolveOrCreateBusinessAccountId(onboardingRequest, request, http);
        if (businessAccountId == null) {
            throw new BusinessException("Aprovação exige BusinessAccount existente ou criação pelo contrato canónico.");
        }
        BusinessAccountGovernanceService.OnboardingAssignment assignment = governanceService
                .governOnboardingAssignment(businessAccountId, request.getTenantId(), onboardingRequest.getId(),
                        idempotencyKey, fingerprint, http);
        BusinessAccount businessAccount = assignment.account();
        onboardingRequest.setBusinessAccount(businessAccount);

        Tenant tenant = assignment.tenant();
        if (tenant != null) {
            onboardingRequest.setTenant(tenant);
        }

        boolean pagamentoOk = onboardingRequest.getStatusPagamento() == OnboardingPaymentStatus.PAGO
                || onboardingRequest.getStatusPagamento() == OnboardingPaymentStatus.NAO_APLICAVEL;
        onboardingRequest.setApprovedAt(LocalDateTime.now());
        onboardingRequest.setMotivoRejeicao(null);
        onboardingRequest.setNotificationStatus("APROVADO_INTERNO");

        if (tenant != null && pagamentoOk) {
            onboardingRequest.setStatus(OnboardingRequestStatus.ATIVADO);
            onboardingRequest.setActivatedAt(LocalDateTime.now());
            onboardingRequest.setNotificationMessage("Onboarding aprovado e associado a tenant existente.");
        } else if (pagamentoOk) {
            onboardingRequest.setStatus(OnboardingRequestStatus.APROVADO);
            onboardingRequest.setNotificationMessage("Onboarding aprovado e pronto para provisionamento.");
        } else {
            onboardingRequest.setStatus(OnboardingRequestStatus.AGUARDANDO_PAGAMENTO);
            onboardingRequest.setNotificationMessage("Onboarding aprovado internamente, aguardando confirmacao de pagamento.");
        }
        onboardingRequest.setApprovalIdempotencyKey(idempotencyKey);
        onboardingRequest.setApprovalFingerprint(fingerprint);
        onboardingRequest = onboardingRequestRepository.saveAndFlush(onboardingRequest);
        saveApprovalAudit(onboardingRequest, businessAccount, idempotencyKey, fingerprint, actor);
        return toResponse(onboardingRequest);
    }

    @Transactional
    public OnboardingRequestResponse rejeitar(Long id, OnboardingRequestRejectRequest request) {
        tenantGuard.assertPlatformAdmin();
        OnboardingRequest onboardingRequest = getRequest(id);
        onboardingRequest.setStatus(OnboardingRequestStatus.REJEITADO);
        onboardingRequest.setMotivoRejeicao(request.getMotivo());
        onboardingRequest.setRejectedAt(LocalDateTime.now());
        onboardingRequest.setNotificationStatus("REJEITADO_INTERNO");
        onboardingRequest.setNotificationMessage("Pedido rejeitado pela equipa da plataforma.");
        return toResponse(onboardingRequestRepository.saveAndFlush(onboardingRequest));
    }

    private OnboardingRequest getRequest(Long id) {
        return onboardingRequestRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Pedido de onboarding nao encontrado."));
    }

    private Plano resolvePlano(String planoCodigo) {
        return planoRepository.findByCodigo(planoCodigo)
                .orElseThrow(() -> new BusinessException("Plano nao encontrado para onboarding."));
    }

    private Long resolveOrCreateBusinessAccountId(OnboardingRequest onboardingRequest,
                                                  OnboardingRequestApproveRequest request,
                                                  HttpServletRequest http) {
        if (request.getBusinessAccountId() != null) {
            return request.getBusinessAccountId();
        }
        if (!Boolean.TRUE.equals(request.getCriarBusinessAccountSeAusente())) {
            return onboardingRequest.getBusinessAccount() == null ? null : onboardingRequest.getBusinessAccount().getId();
        }
        BusinessAccount existing = onboardingRequest.getBusinessAccount();
        if (existing != null) {
            return existing.getId();
        }
        if (request.getResponsavelUserId() == null) {
            throw new BusinessException("Onboarding não pode criar Conta Empresarial sem responsável principal explícito.");
        }
        String slug = canonicalOnboardingSlug(request.getBusinessAccountSlug(), onboardingRequest.getNomeNegocio());
        CanonicalAccountCreateRequest create = new CanonicalAccountCreateRequest(
                onboardingRequest.getNomeNegocio(), slug, onboardingRequest.getNif(), onboardingRequest.getEmail(),
                onboardingRequest.getTelefone(), 1,
                new PrincipalOwner(PrincipalOwnerStrategy.ASSOCIATE_EXISTING, request.getResponsavelUserId(),
                        true, null, null, null, null, null));
        String internalKey = "onboarding-account-" + onboardingRequest.getId();
        return governanceService.create(create, internalKey, http).getId();
    }

    private OnboardingPaymentStatus resolveInitialPaymentStatus(BigDecimal valor) {
        if (valor == null || BigDecimal.ZERO.compareTo(valor) >= 0) {
            return OnboardingPaymentStatus.NAO_APLICAVEL;
        }
        return OnboardingPaymentStatus.PENDENTE;
    }

    private String canonicalOnboardingSlug(String requestedSlug, String businessName) {
        String slug = ProvisioningPlanCalculator.normalizeSlug(
                requestedSlug != null && !requestedSlug.isBlank() ? requestedSlug : businessName
        );
        if (slug == null || slug.isBlank()) throw new BusinessException("Slug inválido para onboarding canónico.");
        return slug;
    }

    private void saveApprovalAudit(OnboardingRequest onboarding, BusinessAccount account, String key,
                                   String fingerprint, CanonicalCommandSupport.Actor actor) {
        BusinessAccountGovernanceEvent event = new BusinessAccountGovernanceEvent();
        event.setBusinessAccount(account);
        event.setScopeKey("ONBOARDING:" + onboarding.getId());
        event.setAction("ONBOARDING_APPROVED");
        event.setIdempotencyKey(key);
        event.setRequestFingerprint(fingerprint);
        event.setActorUserId(actor.userId());
        event.setActorRoles(actor.roles());
        event.setCorrelationId(actor.correlationId());
        event.setIpAddress(actor.ip());
        event.setUserAgent(actor.userAgent());
        event.setBeforeState(commands.json(Map.of("onboardingId", onboarding.getId(), "status", "PENDENTE")));
        event.setAfterState(commands.json(Map.of("onboardingId", onboarding.getId(), "status",
                onboarding.getStatus().name(), "businessAccountId", account == null ? -1L : account.getId())));
        event.setResultAccountId(account == null ? null : account.getId());
        governanceEvents.saveAndFlush(event);
    }

    private String mergeObservacao(String atual, String adicional) {
        if (atual == null || atual.isBlank()) {
            return adicional;
        }
        return atual + " | " + adicional;
    }

    private OnboardingRequestResponse toResponse(OnboardingRequest request) {
        return OnboardingRequestResponse.builder()
                .id(request.getId())
                .nomeSolicitante(request.getNomeSolicitante())
                .telefone(request.getTelefone())
                .email(request.getEmail())
                .nomeNegocio(request.getNomeNegocio())
                .nif(request.getNif())
                .tipoNegocio(request.getTipoNegocio())
                .planoCodigo(request.getPlano() != null ? request.getPlano().getCodigo() : null)
                .planoNome(request.getPlano() != null ? request.getPlano().getNome() : null)
                .businessAccountId(request.getBusinessAccount() != null ? request.getBusinessAccount().getId() : null)
                .businessAccountNome(request.getBusinessAccount() != null ? request.getBusinessAccount().getNome() : null)
                .tenantId(request.getTenant() != null ? request.getTenant().getId() : null)
                .tenantNome(request.getTenant() != null ? request.getTenant().getNome() : null)
                .status(request.getStatus())
                .statusPagamento(request.getStatusPagamento())
                .valor(request.getValor())
                .moeda(request.getMoeda())
                .observacao(request.getObservacao())
                .motivoRejeicao(request.getMotivoRejeicao())
                .notificationStatus(request.getNotificationStatus())
                .notificationMessage(request.getNotificationMessage())
                .approvedAt(request.getApprovedAt())
                .rejectedAt(request.getRejectedAt())
                .activatedAt(request.getActivatedAt())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }
}
