package com.restaurante.service;

import com.restaurante.dto.business.BusinessProvisioningContracts.BusinessPreviewRequest;
import com.restaurante.dto.business.BusinessProvisioningContracts.BusinessVertical;
import com.restaurante.dto.business.BusinessProvisioningContracts.CanonicalAccountCreateRequest;
import com.restaurante.dto.business.BusinessProvisioningContracts.PrincipalOwner;
import com.restaurante.dto.request.OnboardingOperationLinkRequest;
import com.restaurante.dto.request.OnboardingRequestApproveRequest;
import com.restaurante.dto.request.OnboardingRequestCancelRequest;
import com.restaurante.dto.request.OnboardingRequestCompleteRequest;
import com.restaurante.dto.request.OnboardingRequestCreateRequest;
import com.restaurante.dto.request.OnboardingRequestRejectRequest;
import com.restaurante.dto.response.BusinessAccountResponse;
import com.restaurante.dto.response.OnboardingHandoffResponse;
import com.restaurante.dto.response.OnboardingHandoffResponse.Availability;
import com.restaurante.dto.response.OnboardingRequestResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ConflictException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.BusinessAccount;
import com.restaurante.model.entity.BusinessProvisioningOperation;
import com.restaurante.model.entity.BusinessProvisioningPreview;
import com.restaurante.model.entity.OnboardingCommandRecord;
import com.restaurante.model.entity.OnboardingNifReservation;
import com.restaurante.model.entity.OnboardingRequest;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.OnboardingAccountChoice;
import com.restaurante.model.enums.OnboardingNifReservationState;
import com.restaurante.model.enums.OnboardingNifResolution;
import com.restaurante.model.enums.OnboardingPaymentStatus;
import com.restaurante.model.enums.OnboardingRequestStatus;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.repository.BusinessAccountRepository;
import com.restaurante.repository.BusinessProvisioningOperationRepository;
import com.restaurante.repository.BusinessProvisioningPreviewRepository;
import com.restaurante.repository.CanonicalBusinessAccountNifRepository;
import com.restaurante.repository.OnboardingCommandRecordRepository;
import com.restaurante.repository.OnboardingNifReservationRepository;
import com.restaurante.repository.OnboardingRequestRepository;
import com.restaurante.repository.PlanoRepository;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.business.BusinessAccountGovernanceService;
import com.restaurante.service.business.CanonicalCommandSupport;
import com.restaurante.service.provisioning.ProvisioningPlanCalculator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlatformOnboardingRequestService {
    public static final String CONTRACT_VERSION = "ONBOARDING_CANONICAL_V2";
    private static final String CHANNEL = "PLATFORM_ADMIN";

    private final TenantGuard tenantGuard;
    private final OnboardingRequestRepository requests;
    private final OnboardingCommandRecordRepository commandRecords;
    private final OnboardingNifReservationRepository reservations;
    private final PlanoRepository planos;
    private final BusinessAccountRepository accounts;
    private final CanonicalBusinessAccountNifRepository canonicalNifs;
    private final BusinessProvisioningOperationRepository operations;
    private final BusinessProvisioningPreviewRepository previews;
    private final BusinessAccountGovernanceService governance;
    private final CanonicalCommandSupport commands;

    @Transactional(readOnly = true)
    public List<OnboardingRequestResponse> listar(Pageable pageable, OnboardingRequestStatus status, String search) {
        tenantGuard.assertPlatformAdmin();
        return requests.search(status, search, pageable).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OnboardingRequestResponse buscarPorId(Long id) {
        tenantGuard.assertPlatformAdmin();
        return toResponse(getRequest(id));
    }

    @Transactional
    public OnboardingRequestResponse criar(OnboardingRequestCreateRequest raw, String key, HttpServletRequest http) {
        tenantGuard.assertPlatformAdmin();
        requireHeaders(key, http);
        OnboardingRequestCreateRequest request = normalizeCreate(raw);
        String scope = "PLATFORM:ONBOARDING_CREATE";
        String action = "ONBOARDING_CREATED";
        String fingerprint = commands.fingerprint(Map.of("contract", CONTRACT_VERSION, "channel", CHANNEL,
                "payload", request));
        commands.lock(scope + ":" + action + ":" + key);
        OnboardingRequestResponse replay = replay(scope, action, key, fingerprint);
        if (replay != null) return replay;

        Plano preferredPlan = resolveActivePlan(request.getPlanoCodigo());
        String normalizedNif = commands.normalizeNif(request.getNif());
        BusinessAccount candidate = null;
        OnboardingNifResolution resolution = OnboardingNifResolution.NOT_PROVIDED;
        if (normalizedNif != null) {
            commands.lock("CANONICAL_BUSINESS_ACCOUNT_NIF:" + normalizedNif);
            candidate = resolveAccountCandidate(normalizedNif);
            if (candidate != null) {
                resolution = OnboardingNifResolution.EXISTING_ACCOUNT_CANDIDATE;
            } else {
                OnboardingNifReservation active = reservations.findByNormalizedNifAndState(
                        normalizedNif, OnboardingNifReservationState.ACTIVE).orElse(null);
                if (active != null) {
                    throw new ConflictException("ONBOARDING_NIF_RESERVED:" + active.getOnboardingRequest().getId());
                }
                resolution = OnboardingNifResolution.NEW_ACCOUNT_RESERVED;
            }
        }

        OnboardingRequest onboarding = new OnboardingRequest();
        onboarding.setContractVersion(CONTRACT_VERSION);
        onboarding.setChannel(CHANNEL);
        onboarding.setNomeSolicitante(request.getNomeSolicitante());
        onboarding.setTelefone(request.getTelefone());
        onboarding.setEmail(request.getEmail());
        onboarding.setNomeNegocio(request.getNomeNegocio());
        onboarding.setNif(trimToNull(request.getNif()));
        onboarding.setNormalizedNif(normalizedNif);
        onboarding.setNifResolution(resolution);
        onboarding.setNifCandidateBusinessAccount(candidate);
        onboarding.setTipoNegocio(request.getTipoNegocio());
        onboarding.setPlano(preferredPlan);
        onboarding.setValor(request.getValor());
        onboarding.setMoeda(request.getMoeda());
        onboarding.setObservacao(trimToNull(request.getObservacao()));
        onboarding.setStatus(OnboardingRequestStatus.PENDENTE);
        onboarding.setStatusPagamento(initialPaymentStatus(request.getValor()));
        onboarding.setNotificationStatus("NAO_ENVIADO");
        onboarding.setNotificationMessage("Pedido registado para validacao interna.");
        onboarding = requests.saveAndFlush(onboarding);

        if (resolution == OnboardingNifResolution.NEW_ACCOUNT_RESERVED) {
            OnboardingNifReservation reservation = new OnboardingNifReservation();
            reservation.setNormalizedNif(normalizedNif);
            reservation.setOnboardingRequest(onboarding);
            reservation.setState(OnboardingNifReservationState.ACTIVE);
            reservations.saveAndFlush(reservation);
        }

        OnboardingRequestResponse response = toResponse(onboarding);
        record(scope, action, key, fingerprint, onboarding, commands.json(Map.of("exists", false)),
                snapshot(onboarding), null, response, http);
        return response;
    }

    @Transactional
    public OnboardingRequestResponse aprovar(Long id, OnboardingRequestApproveRequest request,
                                             String key, HttpServletRequest http) {
        tenantGuard.assertPlatformAdmin();
        requireHeaders(key, http);
        String scope = scope(id);
        String action = "ONBOARDING_APPROVED";
        String fingerprint = commands.fingerprint(Map.of("contract", CONTRACT_VERSION,
                "onboardingId", id, "payload", request));
        OnboardingRequest onboarding = lockRequest(id);
        OnboardingRequestResponse replay = replay(scope, action, key, fingerprint);
        if (replay != null) return replay;
        checkVersion(onboarding, request.getOnboardingVersion());
        requireState(onboarding, Set.of(OnboardingRequestStatus.PENDENTE,
                OnboardingRequestStatus.AGUARDANDO_APROVACAO));
        String reason = commands.requireReason(request.getReason());
        String before = snapshot(onboarding);

        ensureNifDecision(onboarding);
        Plano confirmedPlan = resolveActivePlan(request.getConfirmedPlanCode());
        BusinessAccount account = switch (request.getAccountChoice()) {
            case EXISTING -> approveExisting(onboarding, request);
            case CREATE_NEW -> approveCreateNew(onboarding, request, http);
        };
        governance.assertOwnerInvariant(account);

        onboarding.setBusinessAccount(account);
        onboarding.setAccountChoice(request.getAccountChoice());
        onboarding.setOwnerStrategy(request.getAccountChoice() == OnboardingAccountChoice.CREATE_NEW
                ? request.getOwnerChoice().strategy() : null);
        onboarding.setOwnerResultUser(account.getResponsavel());
        onboarding.setConfirmedPlan(confirmedPlan);
        onboarding.setVertical(request.getVertical());
        onboarding.setApprovalReason(reason);
        onboarding.setApprovedAt(LocalDateTime.now());
        onboarding.setStatus(OnboardingRequestStatus.APROVADO);
        onboarding.setContractVersion(CONTRACT_VERSION);
        onboarding.setMotivoRejeicao(null);
        onboarding.setNotificationStatus("APROVADO_INTERNO");
        onboarding.setNotificationMessage("Onboarding aprovado e pronto para handoff canónico.");
        onboarding = requests.saveAndFlush(onboarding);

        OnboardingRequestResponse response = toResponse(onboarding);
        record(scope, action, key, fingerprint, onboarding, before, snapshot(onboarding), reason,
                response, http);
        return response;
    }

    @Transactional
    public OnboardingRequestResponse rejeitar(Long id, OnboardingRequestRejectRequest request,
                                              String key, HttpServletRequest http) {
        tenantGuard.assertPlatformAdmin();
        requireHeaders(key, http);
        String scope = scope(id);
        String action = "ONBOARDING_REJECTED";
        String fingerprint = commands.fingerprint(Map.of("contract", CONTRACT_VERSION,
                "onboardingId", id, "payload", request));
        OnboardingRequest onboarding = lockRequest(id);
        OnboardingRequestResponse replay = replay(scope, action, key, fingerprint);
        if (replay != null) return replay;
        checkVersion(onboarding, request.getOnboardingVersion());
        requireState(onboarding, Set.of(OnboardingRequestStatus.PENDENTE,
                OnboardingRequestStatus.AGUARDANDO_APROVACAO));
        String reason = commands.requireReason(request.getReason());
        String before = snapshot(onboarding);
        releaseReservation(onboarding);
        onboarding.setStatus(OnboardingRequestStatus.REJEITADO);
        onboarding.setMotivoRejeicao(reason);
        onboarding.setRejectedAt(LocalDateTime.now());
        onboarding.setNotificationStatus("REJEITADO_INTERNO");
        onboarding.setNotificationMessage("Pedido rejeitado pela equipa da plataforma.");
        onboarding = requests.saveAndFlush(onboarding);
        OnboardingRequestResponse response = toResponse(onboarding);
        record(scope, action, key, fingerprint, onboarding, before, snapshot(onboarding), reason,
                response, http);
        return response;
    }

    @Transactional
    public OnboardingRequestResponse cancelar(Long id, OnboardingRequestCancelRequest request,
                                              String key, HttpServletRequest http) {
        tenantGuard.assertPlatformAdmin();
        requireHeaders(key, http);
        String scope = scope(id);
        String action = "ONBOARDING_CANCELLED";
        String fingerprint = commands.fingerprint(Map.of("contract", CONTRACT_VERSION,
                "onboardingId", id, "payload", request));
        OnboardingRequest onboarding = lockRequest(id);
        OnboardingRequestResponse replay = replay(scope, action, key, fingerprint);
        if (replay != null) return replay;
        checkVersion(onboarding, request.getOnboardingVersion());
        requireState(onboarding, Set.of(OnboardingRequestStatus.PENDENTE, OnboardingRequestStatus.APROVADO,
                OnboardingRequestStatus.AGUARDANDO_APROVACAO, OnboardingRequestStatus.AGUARDANDO_PAGAMENTO));
        String reason = commands.requireReason(request.getReason());
        assertCancellationSafe(onboarding);
        String before = snapshot(onboarding);
        if (onboarding.getBusinessAccount() == null) releaseReservation(onboarding);
        onboarding.setStatus(OnboardingRequestStatus.CANCELADO);
        onboarding.setCancellationReason(reason);
        onboarding.setCancelledAt(LocalDateTime.now());
        onboarding.setNotificationStatus("CANCELADO_INTERNO");
        onboarding.setNotificationMessage("Onboarding cancelado administrativamente; efeitos externos não foram compensados.");
        onboarding = requests.saveAndFlush(onboarding);
        OnboardingRequestResponse response = toResponse(onboarding);
        record(scope, action, key, fingerprint, onboarding, before, snapshot(onboarding), reason,
                response, http);
        return response;
    }

    @Transactional(readOnly = true)
    public OnboardingHandoffResponse handoff(Long id) {
        tenantGuard.assertPlatformAdmin();
        OnboardingRequest onboarding = getRequest(id);
        if (onboarding.getStatus() != OnboardingRequestStatus.APROVADO
                && onboarding.getStatus() != OnboardingRequestStatus.CONCLUIDO) {
            throw new ConflictException("ONBOARDING_STATE_CONFLICT");
        }
        Map<String, Availability> matrix = new LinkedHashMap<>();
        available(matrix, "accountVersion", onboarding.getBusinessAccount() != null);
        available(matrix, "planoCodigo", onboarding.getConfirmedPlan() != null);
        available(matrix, "vertical", onboarding.getVertical() != null);
        available(matrix, "negocio.nomeNegocio", notBlank(onboarding.getNomeNegocio()));
        available(matrix, "negocio.tipo", onboarding.getTipoNegocio() != null);
        available(matrix, "negocio.nif", onboarding.getNormalizedNif() != null);
        matrix.put("negocio.telefone", notBlank(onboarding.getTelefone())
                ? Availability.REQUIRES_HUMAN_DECISION : Availability.MISSING);
        matrix.put("negocio.email", notBlank(onboarding.getEmail())
                ? Availability.REQUIRES_HUMAN_DECISION : Availability.MISSING);
        for (String field : List.of("negocio.slug", "negocio.tenantCode", "negocio.endereco",
                "negocio.provincia", "negocio.municipio")) matrix.put(field, Availability.MISSING);
        matrix.put("config.ponto", onboarding.getVertical() == BusinessVertical.CONSUMA_PONTO
                ? Availability.REQUIRES_HUMAN_DECISION : Availability.MISSING);
        matrix.put("config.rest", onboarding.getVertical() == BusinessVertical.CONSUMA_REST
                ? Availability.REQUIRES_HUMAN_DECISION : Availability.MISSING);
        matrix.put("acessos.additionalAccesses", Availability.REQUIRES_HUMAN_DECISION);
        return OnboardingHandoffResponse.builder()
                .onboardingId(onboarding.getId()).onboardingVersion(onboarding.getVersion())
                .status(onboarding.getStatus())
                .businessAccountId(id(onboarding.getBusinessAccount()))
                .businessAccountVersion(version(onboarding.getBusinessAccount()))
                .ownerResultUserId(onboarding.getOwnerResultUser() == null ? null : onboarding.getOwnerResultUser().getId())
                .ownerStrategy(onboarding.getOwnerStrategy())
                .confirmedPlanCode(onboarding.getConfirmedPlan() == null ? null : onboarding.getConfirmedPlan().getCodigo())
                .vertical(onboarding.getVertical()).normalizedNif(onboarding.getNormalizedNif())
                .nomeNegocio(onboarding.getNomeNegocio()).tipoNegocio(onboarding.getTipoNegocio())
                .telefone(onboarding.getTelefone()).email(onboarding.getEmail())
                .provisioningOperationId(publicId(onboarding.getProvisioningOperation()))
                .tenantId(id(onboarding.getTenant())).fieldMatrix(matrix).build();
    }

    @Transactional
    public OnboardingRequestResponse linkOperation(Long id, OnboardingOperationLinkRequest request,
                                                   String key, HttpServletRequest http) {
        tenantGuard.assertPlatformAdmin();
        requireHeaders(key, http);
        String scope = scope(id);
        String action = "ONBOARDING_OPERATION_LINKED";
        String fingerprint = commands.fingerprint(Map.of("contract", CONTRACT_VERSION,
                "onboardingId", id, "payload", request));
        OnboardingRequest onboarding = lockRequest(id);
        OnboardingRequestResponse replay = replay(scope, action, key, fingerprint);
        if (replay != null) return replay;
        checkVersion(onboarding, request.getOnboardingVersion());
        requireState(onboarding, Set.of(OnboardingRequestStatus.APROVADO));
        String reason = commands.requireReason(request.getReason());
        if (onboarding.getProvisioningOperation() != null) {
            throw new ConflictException("ONBOARDING_OPERATION_CONFLICT");
        }
        BusinessProvisioningOperation operation = lockOperation(request.getOperationId());
        OnboardingRequest other = requests.findByProvisioningOperationId(operation.getId()).orElse(null);
        if (other != null && !Objects.equals(other.getId(), onboarding.getId())) {
            throw new ConflictException("ONBOARDING_OPERATION_CONFLICT");
        }
        if (!Set.of("PENDING", "RUNNING", "SUCCEEDED", "FAILED_RETRYABLE").contains(operation.getStatus())) {
            throw new ConflictException("ONBOARDING_OPERATION_STATE_CONFLICT");
        }
        validateOperationConvergence(onboarding, operation);
        String before = snapshot(onboarding);
        onboarding.setProvisioningOperation(operation);
        onboarding = requests.saveAndFlush(onboarding);
        OnboardingRequestResponse response = toResponse(onboarding);
        record(scope, action, key, fingerprint, onboarding, before, snapshot(onboarding), reason,
                response, http);
        return response;
    }

    @Transactional
    public OnboardingRequestResponse completar(Long id, OnboardingRequestCompleteRequest request,
                                               String key, HttpServletRequest http) {
        tenantGuard.assertPlatformAdmin();
        requireHeaders(key, http);
        String scope = scope(id);
        String action = "ONBOARDING_COMPLETED";
        String fingerprint = commands.fingerprint(Map.of("contract", CONTRACT_VERSION,
                "onboardingId", id, "payload", request));
        OnboardingRequest onboarding = lockRequest(id);
        OnboardingRequestResponse replay = replay(scope, action, key, fingerprint);
        if (replay != null) return replay;
        checkVersion(onboarding, request.getOnboardingVersion());
        requireState(onboarding, Set.of(OnboardingRequestStatus.APROVADO));
        String reason = commands.requireReason(request.getReason());
        BusinessProvisioningOperation linked = onboarding.getProvisioningOperation();
        if (linked == null) throw new ConflictException("ONBOARDING_OPERATION_REQUIRED");
        if (notBlank(request.getOperationId()) && !Objects.equals(linked.getOperationId(), request.getOperationId())) {
            throw new ConflictException("ONBOARDING_OPERATION_CONFLICT");
        }
        BusinessProvisioningOperation operation = lockOperation(linked.getOperationId());
        validateOperationConvergence(onboarding, operation);
        if (!"SUCCEEDED".equals(operation.getStatus())) {
            throw new ConflictException("ONBOARDING_OPERATION_NOT_SUCCEEDED");
        }
        if (!Boolean.TRUE.equals(operation.getEffectsCommitted())) {
            throw new ConflictException("ONBOARDING_OPERATION_EFFECTS_NOT_COMMITTED");
        }
        Tenant tenant = operation.getTenant();
        if (tenant == null) throw new ConflictException("ONBOARDING_OPERATION_TENANT_REQUIRED");
        if (tenant.getBusinessAccount() == null
                || !Objects.equals(tenant.getBusinessAccount().getId(), onboarding.getBusinessAccount().getId())) {
            throw new ConflictException("ONBOARDING_TENANT_ACCOUNT_MISMATCH");
        }
        if (tenant.getEstado() != TenantEstado.RASCUNHO) {
            throw new ConflictException("ONBOARDING_TENANT_NOT_RASCUNHO");
        }
        BusinessAccount account = onboarding.getBusinessAccount();
        if (account.getEstado() != BusinessAccountEstado.RASCUNHO
                && account.getEstado() != BusinessAccountEstado.ATIVA) {
            throw new ConflictException("BUSINESS_ACCOUNT_STATE_BLOCKS_ONBOARDING");
        }
        governance.assertOwnerInvariant(account);
        String before = snapshot(onboarding);
        onboarding.setTenant(tenant);
        onboarding.setStatus(OnboardingRequestStatus.CONCLUIDO);
        onboarding.setCompletedAt(LocalDateTime.now());
        onboarding.setNotificationStatus("CONCLUIDO_INTERNO");
        onboarding.setNotificationMessage("Provisionamento canónico validado; activações permanecem explícitas.");
        onboarding = requests.saveAndFlush(onboarding);
        OnboardingRequestResponse response = toResponse(onboarding);
        record(scope, action, key, fingerprint, onboarding, before, snapshot(onboarding), reason,
                response, http);
        return response;
    }

    private OnboardingRequestCreateRequest normalizeCreate(OnboardingRequestCreateRequest raw) {
        if (raw.getValor() != null && raw.getValor().signum() < 0) throw new BusinessException("ONBOARDING_VALUE_NEGATIVE");
        String currency = trimToNull(raw.getMoeda());
        currency = currency == null ? "AOA" : currency.toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) throw new BusinessException("ONBOARDING_CURRENCY_INVALID");
        return OnboardingRequestCreateRequest.builder()
                .nomeSolicitante(requiredTrim(raw.getNomeSolicitante(), "nomeSolicitante"))
                .telefone(requiredTrim(raw.getTelefone(), "telefone"))
                .email(trimToNull(raw.getEmail()))
                .nomeNegocio(requiredTrim(raw.getNomeNegocio(), "nomeNegocio"))
                .nif(trimToNull(raw.getNif())).tipoNegocio(raw.getTipoNegocio())
                .planoCodigo(requiredTrim(raw.getPlanoCodigo(), "planoCodigo").toUpperCase(Locale.ROOT))
                .valor(raw.getValor()).moeda(currency).observacao(trimToNull(raw.getObservacao())).build();
    }

    private BusinessAccount approveExisting(OnboardingRequest onboarding, OnboardingRequestApproveRequest request) {
        if (request.getBusinessAccountId() == null || request.getAccountVersion() == null
                || !Boolean.TRUE.equals(request.getConfirmExistingAccount())) {
            throw new BusinessException("ONBOARDING_EXISTING_ACCOUNT_CONFIRMATION_REQUIRED");
        }
        if (request.getOwnerChoice() != null) throw new BusinessException("EXISTING_ACCOUNT_DOES_NOT_ACCEPT_OWNER_CHOICE");
        BusinessAccount account = accounts.findByIdForUpdate(request.getBusinessAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("BusinessAccount", "id", request.getBusinessAccountId()));
        if (!Objects.equals(account.getVersion(), request.getAccountVersion())) {
            throw new ConflictException("OPTIMISTIC_VERSION_CONFLICT");
        }
        assertAccountState(account);
        governance.assertOwnerInvariant(account);
        assertAccountNif(onboarding, account);
        BusinessAccount candidate = onboarding.getNifCandidateBusinessAccount();
        if (candidate != null && !Objects.equals(candidate.getId(), account.getId())) {
            throw new ConflictException("ONBOARDING_ACCOUNT_NIF_MISMATCH");
        }
        consumeReservation(onboarding, account);
        return account;
    }

    private BusinessAccount approveCreateNew(OnboardingRequest onboarding, OnboardingRequestApproveRequest request,
                                             HttpServletRequest http) {
        if (request.getBusinessAccountId() != null || request.getAccountVersion() != null
                || Boolean.TRUE.equals(request.getConfirmExistingAccount())) {
            throw new BusinessException("CREATE_NEW_DOES_NOT_ACCEPT_EXISTING_ACCOUNT_FIELDS");
        }
        PrincipalOwner owner = request.getOwnerChoice();
        if (owner == null) throw new BusinessException("ONBOARDING_OWNER_CHOICE_REQUIRED");
        String normalizedNif = onboarding.getNormalizedNif();
        if (normalizedNif != null) {
            commands.lock("CANONICAL_BUSINESS_ACCOUNT_NIF:" + normalizedNif);
            BusinessAccount candidate = resolveAccountCandidate(normalizedNif);
            if (candidate != null) throw new ConflictException("ONBOARDING_ACCOUNT_ALREADY_EXISTS_FOR_NIF");
            ensureOwnedReservation(onboarding);
        }
        String slug = ProvisioningPlanCalculator.normalizeSlug(notBlank(request.getBusinessAccountSlug())
                ? request.getBusinessAccountSlug() : onboarding.getNomeNegocio());
        if (!notBlank(slug)) throw new BusinessException("ONBOARDING_ACCOUNT_SLUG_INVALID");
        CanonicalAccountCreateRequest create = new CanonicalAccountCreateRequest(
                onboarding.getNomeNegocio(), slug, normalizedNif, null, null,
                request.getMaxTenants() == null ? 1 : request.getMaxTenants(), owner);
        String internalKey = "ONBOARDING:" + onboarding.getId() + ":ACCOUNT_CREATE:V2";
        BusinessAccountResponse created = governance.createForOnboarding(create, internalKey, onboarding.getId(), http);
        BusinessAccount account = accounts.findByIdForUpdate(created.getId())
                .orElseThrow(() -> new ResourceNotFoundException("BusinessAccount", "id", created.getId()));
        consumeReservation(onboarding, account);
        return account;
    }

    private void ensureNifDecision(OnboardingRequest onboarding) {
        if (onboarding.getNormalizedNif() == null && notBlank(onboarding.getNif())) {
            onboarding.setNormalizedNif(commands.normalizeNif(onboarding.getNif()));
        }
        String nif = onboarding.getNormalizedNif();
        if (nif == null) {
            onboarding.setNifResolution(OnboardingNifResolution.NOT_PROVIDED);
            return;
        }
        commands.lock("CANONICAL_BUSINESS_ACCOUNT_NIF:" + nif);
        BusinessAccount candidate = resolveAccountCandidate(nif);
        if (candidate != null) {
            onboarding.setNifCandidateBusinessAccount(candidate);
            onboarding.setNifResolution(OnboardingNifResolution.EXISTING_ACCOUNT_CANDIDATE);
            return;
        }
        ensureOwnedReservation(onboarding);
        onboarding.setNifResolution(OnboardingNifResolution.NEW_ACCOUNT_RESERVED);
    }

    private void ensureOwnedReservation(OnboardingRequest onboarding) {
        if (onboarding.getNormalizedNif() == null) return;
        OnboardingNifReservation own = reservations.findByOnboardingRequestIdAndState(
                onboarding.getId(), OnboardingNifReservationState.ACTIVE).orElse(null);
        if (own != null) return;
        OnboardingNifReservation active = reservations.findByNormalizedNifAndState(
                onboarding.getNormalizedNif(), OnboardingNifReservationState.ACTIVE).orElse(null);
        if (active != null) throw new ConflictException("ONBOARDING_NIF_RESERVED:" + active.getOnboardingRequest().getId());
        OnboardingNifReservation created = new OnboardingNifReservation();
        created.setNormalizedNif(onboarding.getNormalizedNif());
        created.setOnboardingRequest(onboarding);
        created.setState(OnboardingNifReservationState.ACTIVE);
        reservations.saveAndFlush(created);
    }

    private void consumeReservation(OnboardingRequest onboarding, BusinessAccount account) {
        reservations.findByOnboardingRequestIdAndState(onboarding.getId(), OnboardingNifReservationState.ACTIVE)
                .ifPresent(reservation -> {
                    reservation.setState(OnboardingNifReservationState.CONSUMED);
                    reservation.setBusinessAccount(account);
                    reservations.saveAndFlush(reservation);
                });
        if (onboarding.getNormalizedNif() != null) onboarding.setNifResolution(OnboardingNifResolution.CONSUMED);
    }

    private void releaseReservation(OnboardingRequest onboarding) {
        reservations.findByOnboardingRequestIdAndState(onboarding.getId(), OnboardingNifReservationState.ACTIVE)
                .ifPresent(reservation -> {
                    reservation.setState(OnboardingNifReservationState.RELEASED);
                    reservations.saveAndFlush(reservation);
                });
        if (onboarding.getNifResolution() == OnboardingNifResolution.NEW_ACCOUNT_RESERVED) {
            onboarding.setNifResolution(OnboardingNifResolution.RELEASED);
        }
    }

    private BusinessAccount resolveAccountCandidate(String normalizedNif) {
        BusinessAccount canonical = canonicalNifs.findById(normalizedNif)
                .map(value -> value.getBusinessAccount()).orElse(null);
        if (canonical != null) return canonical;
        List<BusinessAccount> legacy = accounts.findAllByNormalizedPersistedNif(normalizedNif);
        if (legacy.size() > 1) throw new ConflictException("ONBOARDING_NIF_ACCOUNT_AMBIGUOUS");
        return legacy.isEmpty() ? null : legacy.get(0);
    }

    private void validateOperationConvergence(OnboardingRequest onboarding,
                                              BusinessProvisioningOperation operation) {
        if (onboarding.getBusinessAccount() == null || operation.getBusinessAccount() == null
                || !Objects.equals(onboarding.getBusinessAccount().getId(), operation.getBusinessAccount().getId())) {
            throw new ConflictException("ONBOARDING_OPERATION_ACCOUNT_MISMATCH");
        }
        BusinessProvisioningPreview preview = previews.findByPreviewId(operation.getPreviewId())
                .orElseThrow(() -> new ConflictException("ONBOARDING_OPERATION_PREVIEW_REQUIRED"));
        if (!Objects.equals(preview.getBusinessAccount().getId(), onboarding.getBusinessAccount().getId())) {
            throw new ConflictException("ONBOARDING_OPERATION_ACCOUNT_MISMATCH");
        }
        BusinessPreviewRequest payload = commands.read(preview.getPayloadJson(), BusinessPreviewRequest.class);
        String expectedPlan = onboarding.getConfirmedPlan() == null ? null : onboarding.getConfirmedPlan().getCodigo();
        if (!Objects.equals(upper(payload.planoCodigo()), upper(expectedPlan))) {
            throw new ConflictException("ONBOARDING_PROVISIONING_PLAN_MISMATCH");
        }
        if (!Objects.equals(payload.vertical(), onboarding.getVertical())) {
            throw new ConflictException("ONBOARDING_PROVISIONING_VERTICAL_MISMATCH");
        }
    }

    private void assertCancellationSafe(OnboardingRequest onboarding) {
        BusinessProvisioningOperation operation = onboarding.getProvisioningOperation();
        if (operation == null) return;
        if (Boolean.TRUE.equals(operation.getEffectsCommitted()) || !"FAILED_FINAL".equals(operation.getStatus())) {
            throw new ConflictException("ONBOARDING_OPERATION_BLOCKS_CANCELLATION");
        }
    }

    private void assertAccountNif(OnboardingRequest onboarding, BusinessAccount account) {
        if (onboarding.getNormalizedNif() == null) return;
        if (!Objects.equals(onboarding.getNormalizedNif(), commands.normalizeNif(account.getNif()))) {
            throw new ConflictException("ONBOARDING_ACCOUNT_NIF_MISMATCH");
        }
    }

    private void assertAccountState(BusinessAccount account) {
        if (account.getEstado() != BusinessAccountEstado.RASCUNHO
                && account.getEstado() != BusinessAccountEstado.ATIVA) {
            throw new ConflictException("BUSINESS_ACCOUNT_STATE_BLOCKS_ONBOARDING");
        }
    }

    private OnboardingRequestResponse replay(String scope, String action, String key, String fingerprint) {
        OnboardingCommandRecord record = commandRecords
                .findByScopeKeyAndActionAndIdempotencyKey(scope, action, key).orElse(null);
        if (record == null) return null;
        if (!Objects.equals(record.getRequestFingerprint(), fingerprint)) {
            throw new ConflictException("IDEMPOTENCY_CONFLICT");
        }
        return commands.read(record.getResultJson(), OnboardingRequestResponse.class);
    }

    private void record(String scope, String action, String key, String fingerprint,
                        OnboardingRequest onboarding, String before, String after, String reason,
                        OnboardingRequestResponse response, HttpServletRequest http) {
        CanonicalCommandSupport.Actor actor = commands.actor(http);
        OnboardingCommandRecord record = new OnboardingCommandRecord();
        record.setScopeKey(scope);
        record.setAction(action);
        record.setIdempotencyKey(key);
        record.setRequestFingerprint(fingerprint);
        record.setOnboardingRequest(onboarding);
        record.setActorUserId(actor.userId());
        record.setActorRoles(actor.roles());
        record.setCorrelationId(actor.correlationId());
        record.setIpAddress(actor.ip());
        record.setUserAgent(actor.userAgent());
        record.setBeforeState(before);
        record.setAfterState(after);
        record.setReason(reason);
        record.setResultJson(commands.json(response));
        record.setResultAccountId(id(onboarding.getBusinessAccount()));
        record.setResultOperationId(publicId(onboarding.getProvisioningOperation()));
        record.setResultTenantId(id(onboarding.getTenant()));
        commandRecords.saveAndFlush(record);
    }

    private OnboardingRequestResponse toResponse(OnboardingRequest value) {
        BusinessAccount account = value.getBusinessAccount();
        Tenant tenant = value.getTenant();
        BusinessProvisioningOperation operation = value.getProvisioningOperation();
        BusinessAccount candidate = value.getNifCandidateBusinessAccount();
        return OnboardingRequestResponse.builder()
                .id(value.getId()).version(value.getVersion()).contractVersion(value.getContractVersion())
                .legacyStatusIndicator(!CONTRACT_VERSION.equals(value.getContractVersion())).channel(value.getChannel())
                .nomeSolicitante(value.getNomeSolicitante()).telefone(value.getTelefone()).email(value.getEmail())
                .nomeNegocio(value.getNomeNegocio()).nif(value.getNif()).normalizedNif(value.getNormalizedNif())
                .nifResolution(value.getNifResolution()).businessAccountCandidate(candidate(candidate))
                .tipoNegocio(value.getTipoNegocio())
                .planoCodigo(value.getPlano() == null ? null : value.getPlano().getCodigo())
                .planoNome(value.getPlano() == null ? null : value.getPlano().getNome())
                .confirmedPlanCode(value.getConfirmedPlan() == null ? null : value.getConfirmedPlan().getCodigo())
                .confirmedPlanName(value.getConfirmedPlan() == null ? null : value.getConfirmedPlan().getNome())
                .vertical(value.getVertical()).accountChoice(value.getAccountChoice())
                .ownerStrategy(value.getOwnerStrategy())
                .ownerResultUserId(value.getOwnerResultUser() == null ? null : value.getOwnerResultUser().getId())
                .businessAccountId(id(account)).businessAccountNome(account == null ? null : account.getNome())
                .businessAccountEstado(account == null ? null : account.getEstado().name())
                .businessAccountVersion(version(account))
                .tenantId(id(tenant)).tenantNome(tenant == null ? null : tenant.getNome())
                .tenantEstado(tenant == null ? null : tenant.getEstado().name()).tenantVersion(version(tenant))
                .provisioningOperationId(publicId(operation))
                .provisioningOperationStatus(operation == null ? null : operation.getStatus())
                .provisioningEffectsCommitted(operation == null ? null : operation.getEffectsCommitted())
                .status(value.getStatus()).statusPagamento(value.getStatusPagamento())
                .valor(value.getValor()).moeda(value.getMoeda()).observacao(value.getObservacao())
                .motivoRejeicao(value.getMotivoRejeicao()).approvalReason(value.getApprovalReason())
                .cancellationReason(value.getCancellationReason()).notificationStatus(value.getNotificationStatus())
                .notificationMessage(value.getNotificationMessage()).approvedAt(value.getApprovedAt())
                .rejectedAt(value.getRejectedAt()).cancelledAt(value.getCancelledAt())
                .completedAt(value.getCompletedAt()).activatedAt(value.getActivatedAt())
                .createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt()).build();
    }

    private OnboardingRequestResponse.BusinessAccountCandidate candidate(BusinessAccount value) {
        if (value == null) return null;
        return OnboardingRequestResponse.BusinessAccountCandidate.builder()
                .id(value.getId()).version(value.getVersion()).nome(value.getNome()).nif(value.getNif())
                .estado(value.getEstado().name()).build();
    }

    private Plano resolveActivePlan(String code) {
        String normalized = requiredTrim(code, "planoCodigo").toUpperCase(Locale.ROOT);
        Plano plan = planos.findByCodigo(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Plano", "codigo", normalized));
        if (!Boolean.TRUE.equals(plan.getAtivo())) throw new ConflictException("ONBOARDING_PLAN_INACTIVE");
        return plan;
    }

    private OnboardingRequest getRequest(Long id) {
        return requests.findById(id).orElseThrow(() -> new ResourceNotFoundException("OnboardingRequest", "id", id));
    }

    private OnboardingRequest lockRequest(Long id) {
        return requests.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("OnboardingRequest", "id", id));
    }

    private BusinessProvisioningOperation lockOperation(String operationId) {
        return operations.findByOperationIdForUpdate(operationId)
                .orElseThrow(() -> new ResourceNotFoundException("BusinessProvisioningOperation", "operationId", operationId));
    }

    private void checkVersion(OnboardingRequest request, Long expected) {
        if (expected == null || !Objects.equals(request.getVersion(), expected)) {
            throw new ConflictException("OPTIMISTIC_VERSION_CONFLICT");
        }
    }

    private void requireState(OnboardingRequest request, Set<OnboardingRequestStatus> allowed) {
        if (!allowed.contains(request.getStatus())) throw new ConflictException("ONBOARDING_STATE_CONFLICT");
    }

    private void requireHeaders(String key, HttpServletRequest http) {
        commands.requireKey(key);
        commands.requireCorrelationId(http);
        commands.actor(http);
    }

    private String snapshot(OnboardingRequest value) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("onboardingId", value.getId());
        state.put("version", value.getVersion());
        state.put("status", value.getStatus() == null ? null : value.getStatus().name());
        state.put("businessAccountId", id(value.getBusinessAccount()));
        state.put("operationId", publicId(value.getProvisioningOperation()));
        state.put("tenantId", id(value.getTenant()));
        return commands.json(state);
    }

    private static void available(Map<String, Availability> matrix, String field, boolean present) {
        matrix.put(field, present ? Availability.AVAILABLE : Availability.MISSING);
    }

    private static OnboardingPaymentStatus initialPaymentStatus(BigDecimal value) {
        return value == null || value.signum() == 0
                ? OnboardingPaymentStatus.NAO_APLICAVEL : OnboardingPaymentStatus.PENDENTE;
    }

    private static String scope(Long id) { return "ONBOARDING:" + id; }
    private static Long id(BusinessAccount value) { return value == null ? null : value.getId(); }
    private static Long id(Tenant value) { return value == null ? null : value.getId(); }
    private static Long version(BusinessAccount value) { return value == null ? null : value.getVersion(); }
    private static Long version(Tenant value) { return value == null ? null : value.getVersion(); }
    private static String publicId(BusinessProvisioningOperation value) {
        return value == null ? null : value.getOperationId();
    }
    private static boolean notBlank(String value) { return value != null && !value.isBlank(); }
    private static String trimToNull(String value) {
        return !notBlank(value) ? null : value.trim();
    }
    private static String requiredTrim(String value, String field) {
        String result = trimToNull(value);
        if (result == null) throw new BusinessException(field + " obrigatório.");
        return result;
    }
    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
