package com.restaurante.service;

import com.restaurante.dto.request.OnboardingRequestApproveRequest;
import com.restaurante.dto.request.OnboardingRequestCreateRequest;
import com.restaurante.dto.request.OnboardingRequestRejectRequest;
import com.restaurante.dto.response.OnboardingRequestResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.BusinessAccount;
import com.restaurante.model.entity.OnboardingRequest;
import com.restaurante.model.entity.BusinessAccountMember;
import com.restaurante.model.entity.User;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.BusinessAccountMemberEstado;
import com.restaurante.model.enums.BusinessAccountRole;
import com.restaurante.model.enums.OnboardingPaymentStatus;
import com.restaurante.model.enums.OnboardingRequestStatus;
import com.restaurante.repository.BusinessAccountRepository;
import com.restaurante.repository.BusinessAccountMemberRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.repository.OnboardingRequestRepository;
import com.restaurante.repository.PlanoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.provisioning.ProvisioningPlanCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlatformOnboardingRequestService {

    private final TenantGuard tenantGuard;
    private final OnboardingRequestRepository onboardingRequestRepository;
    private final PlanoRepository planoRepository;
    private final BusinessAccountRepository businessAccountRepository;
    private final TenantRepository tenantRepository;
    private final BusinessAccountMemberRepository businessAccountMemberRepository;
    private final UserRepository userRepository;

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
    public OnboardingRequestResponse aprovar(Long id, OnboardingRequestApproveRequest request) {
        tenantGuard.assertPlatformAdmin();
        OnboardingRequest onboardingRequest = getRequest(id);
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

        BusinessAccount businessAccount = resolveOrCreateBusinessAccount(onboardingRequest, request);
        if (businessAccount != null) {
            onboardingRequest.setBusinessAccount(businessAccount);
        }

        Tenant tenant = resolveTenant(request.getTenantId());
        if (tenant != null) {
            if (businessAccount != null) {
                ensureTenantCanAttach(businessAccount, tenant);
                tenant.setBusinessAccount(businessAccount);
                tenantRepository.saveAndFlush(tenant);
            }
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

        return toResponse(onboardingRequestRepository.saveAndFlush(onboardingRequest));
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

    private Tenant resolveTenant(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant nao encontrado para onboarding."));
    }

    private BusinessAccount resolveOrCreateBusinessAccount(OnboardingRequest onboardingRequest,
                                                           OnboardingRequestApproveRequest request) {
        if (request.getBusinessAccountId() != null) {
            return businessAccountRepository.findById(request.getBusinessAccountId())
                    .orElseThrow(() -> new BusinessException("BusinessAccount nao encontrada para onboarding."));
        }
        if (!Boolean.TRUE.equals(request.getCriarBusinessAccountSeAusente())) {
            return onboardingRequest.getBusinessAccount();
        }
        BusinessAccount existing = onboardingRequest.getBusinessAccount();
        if (existing != null) {
            return existing;
        }
        if (request.getResponsavelUserId() == null) {
            throw new BusinessException("Onboarding não pode criar Conta Empresarial sem responsável principal explícito.");
        }
        User owner = userRepository.findById(request.getResponsavelUserId())
                .filter(u -> Boolean.TRUE.equals(u.getAtivo()))
                .orElseThrow(() -> new BusinessException("Responsável principal activo não encontrado para onboarding."));
        BusinessAccount businessAccount = new BusinessAccount();
        businessAccount.setNome(onboardingRequest.getNomeNegocio());
        businessAccount.setSlug(nextBusinessAccountSlug(request.getBusinessAccountSlug(), onboardingRequest.getNomeNegocio()));
        businessAccount.setEstado(BusinessAccountEstado.RASCUNHO);
        businessAccount.setNif(onboardingRequest.getNif());
        businessAccount.setEmail(onboardingRequest.getEmail());
        businessAccount.setTelefone(onboardingRequest.getTelefone());
        businessAccount.setMaxTenants(1);
        businessAccount.setResponsavel(owner);
        businessAccount.setObservacao("Criada a partir do onboarding request #" + onboardingRequest.getId());
        businessAccount.setProvisionedAt(LocalDateTime.now());
        businessAccount.setProvisionedBy(resolveProvisionedBy());
        businessAccount = businessAccountRepository.saveAndFlush(businessAccount);
        BusinessAccountMember ownerMember = new BusinessAccountMember();
        ownerMember.setBusinessAccount(businessAccount);
        ownerMember.setUser(owner);
        ownerMember.setRole(BusinessAccountRole.OWNER);
        ownerMember.setEstado(BusinessAccountMemberEstado.ATIVO);
        businessAccountMemberRepository.saveAndFlush(ownerMember);
        return businessAccount;
    }

    private void ensureTenantCanAttach(BusinessAccount businessAccount, Tenant tenant) {
        if (tenant.getBusinessAccount() != null && !tenant.getBusinessAccount().getId().equals(businessAccount.getId())) {
            throw new BusinessException("Tenant ja pertence a outra BusinessAccount.");
        }
        long currentCount = tenantRepository.countByBusinessAccountId(businessAccount.getId());
        boolean alreadyLinked = tenant.getBusinessAccount() != null && businessAccount.getId().equals(tenant.getBusinessAccount().getId());
        if (!alreadyLinked && businessAccount.getMaxTenants() != null && currentCount >= businessAccount.getMaxTenants()) {
            throw new BusinessException("BusinessAccount atingiu o limite maximo de tenants vinculados.");
        }
    }

    private OnboardingPaymentStatus resolveInitialPaymentStatus(BigDecimal valor) {
        if (valor == null || BigDecimal.ZERO.compareTo(valor) >= 0) {
            return OnboardingPaymentStatus.NAO_APLICAVEL;
        }
        return OnboardingPaymentStatus.PENDENTE;
    }

    private String nextBusinessAccountSlug(String requestedSlug, String businessName) {
        String baseSlug = ProvisioningPlanCalculator.normalizeSlug(
                requestedSlug != null && !requestedSlug.isBlank() ? requestedSlug : businessName
        );
        if (baseSlug == null || baseSlug.isBlank()) {
            baseSlug = "business-account";
        }
        String candidate = baseSlug;
        int suffix = 1;
        while (businessAccountRepository.existsBySlug(candidate)) {
            candidate = baseSlug + "-" + suffix++;
        }
        return candidate;
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
