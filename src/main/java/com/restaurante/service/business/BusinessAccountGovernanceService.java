package com.restaurante.service.business;

import com.restaurante.dto.business.BusinessProvisioningContracts.AccountActivationRequest;
import com.restaurante.dto.business.BusinessProvisioningContracts.CanonicalAccountCreateRequest;
import com.restaurante.dto.business.BusinessProvisioningContracts.ManagerCommandRequest;
import com.restaurante.dto.business.BusinessProvisioningContracts.PrincipalOwner;
import com.restaurante.dto.business.BusinessProvisioningContracts.PrincipalOwnerStrategy;
import com.restaurante.dto.business.BusinessProvisioningContracts.ReplaceOwnerRequest;
import com.restaurante.dto.response.BusinessAccountMemberResponse;
import com.restaurante.dto.response.BusinessAccountResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ConflictException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.BusinessAccount;
import com.restaurante.model.entity.BusinessAccountGovernanceEvent;
import com.restaurante.model.entity.BusinessAccountMember;
import com.restaurante.model.entity.CanonicalBusinessAccountNif;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.BusinessAccountMemberEstado;
import com.restaurante.model.enums.BusinessAccountRole;
import com.restaurante.repository.BusinessAccountGovernanceEventRepository;
import com.restaurante.repository.BusinessAccountMemberRepository;
import com.restaurante.repository.BusinessAccountRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.repository.CanonicalBusinessAccountNifRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.OnboardingNifReservationRepository;
import com.restaurante.model.enums.OnboardingNifReservationState;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.BusinessAccountService;
import com.restaurante.service.provisioning.ProvisioningPlanCalculator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BusinessAccountGovernanceService {
    private final TenantGuard tenantGuard;
    private final BusinessAccountRepository accounts;
    private final BusinessAccountMemberRepository members;
    private final BusinessAccountGovernanceEventRepository events;
    private final UserRepository users;
    private final CanonicalBusinessAccountNifRepository canonicalNifs;
    private final OnboardingNifReservationRepository onboardingNifReservations;
    private final TenantRepository tenants;
    private final PasswordEncoder passwordEncoder;
    private final CanonicalCommandSupport commands;
    private final BusinessAccountService accountViews;

    @Transactional
    public BusinessAccountResponse create(CanonicalAccountCreateRequest request, String key,
                                          HttpServletRequest http) {
        return createCanonical(request, key, null, http);
    }

    @Transactional
    public BusinessAccountResponse createForOnboarding(CanonicalAccountCreateRequest request, String key,
                                                       Long onboardingId, HttpServletRequest http) {
        if (onboardingId == null) throw new BusinessException("ONBOARDING_ID_REQUIRED");
        return createCanonical(request, key, onboardingId, http);
    }

    private BusinessAccountResponse createCanonical(CanonicalAccountCreateRequest request, String key,
                                                     Long authorizedOnboardingId, HttpServletRequest http) {
        tenantGuard.assertPlatformAdmin();
        commands.requireKey(key);
        commands.actor(http); // valida headers inclusive em replay
        String normalizedNif = commands.normalizeNif(request.nif());
        CanonicalAccountCreateRequest normalizedRequest = new CanonicalAccountCreateRequest(
                request.nome().trim(), request.slug(), normalizedNif, trim(request.email()),
                trim(request.telefone()), request.maxTenants(), request.responsavelPrincipal());
        String scope = "PLATFORM:BUSINESS_ACCOUNT_CREATE";
        String fp = commands.fingerprint(Map.of("contract", "ACCOUNT_V1", "payload", normalizedRequest));
        commands.lock(scope + ":" + key);
        BusinessAccountGovernanceEvent replay = events
                .findByScopeKeyAndActionAndIdempotencyKey(scope, "ACCOUNT_CREATED", key).orElse(null);
        if (replay != null) {
            assertSameFingerprint(replay, fp);
            return accountViews.buscarPorId(replay.getResultAccountId());
        }

        String slug = ProvisioningPlanCalculator.normalizeSlug(normalizedRequest.slug());
        if (slug == null || slug.isBlank()) throw new BusinessException("Slug inválido para BusinessAccount.");
        commands.lock("CANONICAL_BUSINESS_ACCOUNT_SLUG:" + slug);
        if (accounts.existsBySlug(slug)) throw new ConflictException("Já existe BusinessAccount com este slug.");
        if (normalizedNif != null) {
            commands.lock("CANONICAL_BUSINESS_ACCOUNT_NIF:" + normalizedNif);
            onboardingNifReservations.findByNormalizedNifAndState(
                    normalizedNif, OnboardingNifReservationState.ACTIVE).ifPresent(reservation -> {
                if (!Objects.equals(authorizedOnboardingId, reservation.getOnboardingRequest().getId())) {
                    throw new ConflictException("ONBOARDING_NIF_RESERVED");
                }
            });
            if (canonicalNifs.existsById(normalizedNif)
                    || accounts.existsByNormalizedPersistedNif(normalizedNif)) {
                throw new ConflictException("NIF_ALREADY_EXISTS");
            }
        }
        User owner = resolvePrincipal(normalizedRequest.responsavelPrincipal());

        BusinessAccount account = new BusinessAccount();
        account.setNome(normalizedRequest.nome());
        account.setSlug(slug);
        account.setNif(normalizedNif);
        account.setEmail(normalizedRequest.email());
        account.setTelefone(normalizedRequest.telefone());
        account.setEstado(BusinessAccountEstado.RASCUNHO);
        account.setMaxTenants(normalizedRequest.maxTenants() == null ? 1 : normalizedRequest.maxTenants());
        account.setResponsavel(owner);
        account.setProvisionedAt(LocalDateTime.now());
        account.setProvisionedBy(String.valueOf(commands.actor(http).userId()));
        account = accounts.saveAndFlush(account);
        if (normalizedNif != null) {
            CanonicalBusinessAccountNif nif = new CanonicalBusinessAccountNif();
            nif.setNormalizedNif(normalizedNif);
            nif.setBusinessAccount(account);
            canonicalNifs.saveAndFlush(nif);
        }
        BusinessAccountMember ownerMember = upsert(account, owner, BusinessAccountRole.OWNER,
                BusinessAccountMemberEstado.ATIVO);
        saveEvent(account, scope, "ACCOUNT_CREATED", key, fp, null,
                commands.json(Map.of("accountId", account.getId(), "ownerUserId", owner.getId(), "estado", "RASCUNHO")),
                account.getId(), ownerMember.getId(), http);
        return accountViews.buscarPorId(account.getId());
    }

    @Transactional
    public BusinessAccountResponse replaceOwner(Long accountId, ReplaceOwnerRequest request, String key,
                                                HttpServletRequest http) {
        tenantGuard.assertPlatformAdmin();
        commands.requireKey(key);
        commands.actor(http); // valida headers inclusive em replay
        BusinessAccount account = lockAccount(accountId);
        String scope = "BUSINESS_ACCOUNT:" + accountId;
        String fp = commands.fingerprint(Map.of("contract", "OWNER_REPLACEMENT_V1", "accountId", accountId,
                "payload", request));
        BusinessAccountGovernanceEvent replay = events
                .findByScopeKeyAndActionAndIdempotencyKey(scope, "OWNER_REPLACED", key).orElse(null);
        if (replay != null) {
            assertSameFingerprint(replay, fp);
            return accountViews.buscarPorId(accountId);
        }
        checkVersion(account, request.accountVersion());
        User previous = account.getResponsavel();
        User next = resolvePrincipal(request.novoResponsavel());
        for (BusinessAccountMember member : members.findByBusinessAccountIdOrderByIdAsc(accountId)) {
            if (member.getRole() == BusinessAccountRole.OWNER && member.getEstado() == BusinessAccountMemberEstado.ATIVO
                    && !member.getUser().getId().equals(next.getId())) {
                member.setRole(BusinessAccountRole.ADMIN);
                members.save(member);
            }
        }
        BusinessAccountMember ownerMember = upsert(account, next, BusinessAccountRole.OWNER,
                BusinessAccountMemberEstado.ATIVO);
        account.setResponsavel(next);
        accounts.saveAndFlush(account);
        String before = commands.json(Map.of("ownerUserId", previous == null ? -1L : previous.getId()));
        String after = commands.json(Map.of("ownerUserId", next.getId(), "reason", request.reason()));
        saveEvent(account, scope, "OWNER_REPLACED", key, fp, before, after,
                account.getId(), ownerMember.getId(), http);
        return accountViews.buscarPorId(accountId);
    }

    @Transactional
    public BusinessAccountMemberResponse upsertManager(Long accountId, ManagerCommandRequest request,
                                                       String key, HttpServletRequest http) {
        tenantGuard.assertPlatformAdmin();
        commands.requireKey(key);
        commands.actor(http); // valida headers inclusive em replay
        if (request.role() == BusinessAccountRole.OWNER) {
            throw new BusinessException("Gestor não pode substituir o responsável principal; use owner replacement.");
        }
        BusinessAccount account = lockAccount(accountId);
        String scope = "BUSINESS_ACCOUNT:" + accountId;
        String fp = commands.fingerprint(Map.of("contract", "ACCOUNT_MANAGER_V1", "accountId", accountId,
                "payload", request));
        BusinessAccountGovernanceEvent replay = events
                .findByScopeKeyAndActionAndIdempotencyKey(scope, "MANAGER_UPSERTED", key).orElse(null);
        if (replay != null) {
            assertSameFingerprint(replay, fp);
            return memberView(accountId, replay.getResultMemberId());
        }
        checkVersion(account, request.accountVersion());
        User user = users.findById(request.userId()).filter(u -> Boolean.TRUE.equals(u.getAtivo()))
                .orElseThrow(() -> new BusinessException("Utilizador activo não encontrado."));
        if (account.getResponsavel() != null && account.getResponsavel().getId().equals(user.getId())) {
            throw new BusinessException("Responsável principal não pode ser alterado pelo comando de gestores.");
        }
        BusinessAccountMemberEstado state = request.estado() == null
                ? BusinessAccountMemberEstado.ATIVO : request.estado();
        BusinessAccountMember member = upsert(account, user, request.role(), state);
        account.setUpdatedAt(LocalDateTime.now());
        accounts.saveAndFlush(account); // incrementa version para concorrência governada
        saveEvent(account, scope, "MANAGER_UPSERTED", key, fp, null,
                commands.json(Map.of("userId", user.getId(), "role", request.role().name(),
                        "estado", state.name(), "reason", request.reason())),
                accountId, member.getId(), http);
        return memberView(accountId, member.getId());
    }

    @Transactional
    public BusinessAccountResponse activate(Long accountId, AccountActivationRequest request, String key,
                                            HttpServletRequest http) {
        tenantGuard.assertPlatformAdmin();
        commands.requireKey(key);
        commands.actor(http); // valida headers inclusive em replay
        BusinessAccount account = lockAccount(accountId);
        String reason = commands.requireReason(request.reason());
        String scope = "BUSINESS_ACCOUNT:" + accountId;
        String fp = commands.fingerprint(Map.of(
                "contract", "ACCOUNT_ACTIVATION_V1",
                "accountId", accountId,
                "accountVersion", request.accountVersion(),
                "reason", reason));
        BusinessAccountGovernanceEvent replay = events
                .findByScopeKeyAndActionAndIdempotencyKey(scope, "ACCOUNT_ACTIVATED", key).orElse(null);
        if (replay != null) {
            assertSameFingerprint(replay, fp);
            return accountViews.buscarPorId(accountId);
        }
        checkVersion(account, request.accountVersion());
        assertOwnerInvariant(account);
        if (account.getEstado() != BusinessAccountEstado.RASCUNHO) {
            throw new ConflictException("ACCOUNT_STATE_INVALID_FOR_ACTIVATION");
        }
        String before = account.getEstado().name();
        Long beforeVersion = account.getVersion();
        account.setEstado(BusinessAccountEstado.ATIVA);
        accounts.saveAndFlush(account);
        saveEvent(account, scope, "ACCOUNT_ACTIVATED", key, fp,
                commands.json(Map.of(
                        "accountId", accountId,
                        "accountVersion", beforeVersion,
                        "estado", before)),
                commands.json(Map.of(
                        "accountId", accountId,
                        "accountVersion", account.getVersion(),
                        "estado", "ATIVA",
                        "reason", reason)),
                accountId, null, http);
        return accountViews.buscarPorId(accountId);
    }

    @Transactional
    public OnboardingAssignment governOnboardingAssignment(Long accountId, Long tenantId, Long onboardingId,
                                                            String key, String fingerprint,
                                                            HttpServletRequest http) {
        tenantGuard.assertPlatformAdmin();
        commands.requireKey(key);
        commands.actor(http);
        BusinessAccount account = lockAccount(accountId);
        if (account.getEstado() != BusinessAccountEstado.RASCUNHO
                && account.getEstado() != BusinessAccountEstado.ATIVA) {
            throw new ConflictException("BUSINESS_ACCOUNT_STATE_BLOCKS_ONBOARDING");
        }
        assertOwnerInvariant(account);
        if (tenantId == null) {
            return new OnboardingAssignment(account, null);
        }

        Tenant tenant = tenants.findByIdForUpdate(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado para onboarding."));
        if (tenant.getBusinessAccount() != null
                && !Objects.equals(tenant.getBusinessAccount().getId(), accountId)) {
            throw new ConflictException("TENANT_ALREADY_LINKED_TO_ANOTHER_BUSINESS_ACCOUNT");
        }
        boolean alreadyLinked = tenant.getBusinessAccount() != null;
        long tenantCount = tenants.countByBusinessAccountId(accountId);
        if (!alreadyLinked && account.getMaxTenants() != null && tenantCount >= account.getMaxTenants()) {
            throw new ConflictException("BUSINESS_ACCOUNT_MAX_TENANTS_REACHED");
        }
        if (alreadyLinked) {
            return new OnboardingAssignment(account, tenant);
        }

        String before = commands.json(Map.of(
                "accountId", accountId,
                "accountVersion", account.getVersion(),
                "tenantId", tenantId,
                "tenantCount", tenantCount,
                "tenantBusinessAccountId", -1L));
        tenant.setBusinessAccount(account);
        tenants.saveAndFlush(tenant);
        account.setUpdatedAt(LocalDateTime.now());
        accounts.saveAndFlush(account);
        String after = commands.json(Map.of(
                "accountId", accountId,
                "accountVersion", account.getVersion(),
                "tenantId", tenantId,
                "tenantCount", tenantCount + 1,
                "tenantBusinessAccountId", accountId));
        saveEvent(account, "ONBOARDING:" + onboardingId, "ONBOARDING_TENANT_ATTACHED", key, fingerprint,
                before, after, accountId, null, http);
        return new OnboardingAssignment(account, tenant);
    }

    public void assertOwnerInvariant(BusinessAccount account) {
        if (account.getResponsavel() == null || !Boolean.TRUE.equals(account.getResponsavel().getAtivo())) {
            throw new ConflictException("BUSINESS_ACCOUNT_OWNER_REQUIRED");
        }
        long owners = members.countByBusinessAccountIdAndRoleAndEstado(account.getId(),
                BusinessAccountRole.OWNER, BusinessAccountMemberEstado.ATIVO);
        boolean pointerMember = members.existsByBusinessAccountIdAndUserIdAndRoleInAndEstado(
                account.getId(), account.getResponsavel().getId(), java.util.List.of(BusinessAccountRole.OWNER),
                BusinessAccountMemberEstado.ATIVO);
        if (owners != 1 || !pointerMember) throw new ConflictException("BUSINESS_ACCOUNT_OWNER_INCONSISTENT");
    }

    private BusinessAccount lockAccount(Long id) {
        return accounts.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("BusinessAccount", "id", id));
    }

    private User resolvePrincipal(PrincipalOwner owner) {
        if (owner.strategy() == PrincipalOwnerStrategy.ASSOCIATE_EXISTING) {
            if (owner.userId() == null || !Boolean.TRUE.equals(owner.confirmExistingUser())) {
                throw new BusinessException("Associação exige userId e confirmação explícita da identidade.");
            }
            return users.findById(owner.userId()).filter(u -> Boolean.TRUE.equals(u.getAtivo()))
                    .orElseThrow(() -> new BusinessException("Responsável existente activo não encontrado."));
        }
        if (owner.userId() != null) throw new BusinessException("CREATE_NEW não aceita userId.");
        if (blank(owner.username()) || blank(owner.temporaryPassword()) || blank(owner.nome()) || blank(owner.telefone())) {
            throw new BusinessException("Novo responsável exige username, temporaryPassword, nome e telefone.");
        }
        if (users.existsByUsername(owner.username().trim())) throw new ConflictException("USERNAME_ALREADY_EXISTS");
        if (!blank(owner.email()) && users.existsByEmail(owner.email().trim())) throw new ConflictException("EMAIL_ALREADY_EXISTS");
        if (users.existsByTelefone(owner.telefone().trim())) throw new ConflictException("PHONE_ALREADY_EXISTS");
        User user = new User();
        user.setUsername(owner.username().trim());
        user.setPassword(passwordEncoder.encode(owner.temporaryPassword()));
        user.setNomeCompleto(owner.nome().trim());
        user.setEmail(trim(owner.email()));
        user.setTelefone(owner.telefone().trim());
        user.setAtivo(true);
        user.setRoles(new HashSet<>()); // account/tenant/global roles são conceitos distintos
        return users.saveAndFlush(user);
    }

    private BusinessAccountMember upsert(BusinessAccount account, User user, BusinessAccountRole role,
                                         BusinessAccountMemberEstado state) {
        BusinessAccountMember member = members.findByBusinessAccountIdAndUserId(account.getId(), user.getId())
                .orElseGet(BusinessAccountMember::new);
        member.setBusinessAccount(account);
        member.setUser(user);
        member.setRole(role);
        member.setEstado(state);
        return members.saveAndFlush(member);
    }

    private BusinessAccountMemberResponse memberView(Long accountId, Long memberId) {
        return accountViews.listarMembros(accountId).stream().filter(v -> Objects.equals(v.getId(), memberId))
                .findFirst().orElseThrow(() -> new BusinessException("Membro não encontrado."));
    }

    private void saveEvent(BusinessAccount account, String scope, String action, String key, String fp,
                           String before, String after, Long resultAccountId, Long resultMemberId,
                           HttpServletRequest http) {
        CanonicalCommandSupport.Actor actor = commands.actor(http);
        BusinessAccountGovernanceEvent event = new BusinessAccountGovernanceEvent();
        event.setBusinessAccount(account);
        event.setScopeKey(scope);
        event.setAction(action);
        event.setIdempotencyKey(key);
        event.setRequestFingerprint(fp);
        event.setActorUserId(actor.userId());
        event.setActorRoles(actor.roles());
        event.setCorrelationId(actor.correlationId());
        event.setIpAddress(actor.ip());
        event.setUserAgent(actor.userAgent());
        event.setBeforeState(before);
        event.setAfterState(after);
        event.setResultAccountId(resultAccountId);
        event.setResultMemberId(resultMemberId);
        events.saveAndFlush(event);
    }

    private void checkVersion(BusinessAccount account, Long expected) {
        if (expected == null || !Objects.equals(account.getVersion(), expected)) {
            throw new ConflictException("OPTIMISTIC_VERSION_CONFLICT");
        }
    }

    private void assertSameFingerprint(BusinessAccountGovernanceEvent event, String fingerprint) {
        if (!Objects.equals(event.getRequestFingerprint(), fingerprint)) {
            throw new ConflictException("IDEMPOTENCY_CONFLICT");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String trim(String value) { return value == null ? null : value.trim(); }

    public record OnboardingAssignment(BusinessAccount account, Tenant tenant) {}
}
