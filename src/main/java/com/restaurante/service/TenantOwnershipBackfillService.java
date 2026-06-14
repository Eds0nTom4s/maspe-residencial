package com.restaurante.service;

import com.restaurante.dto.request.TenantOwnershipBackfillRequest;
import com.restaurante.dto.response.TenantOwnershipBackfillDiagnosticResponse;
import com.restaurante.dto.response.TenantOwnershipBackfillResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.PlatformAdminCannotOwnTenantException;
import com.restaurante.exception.ProvisioningException;
import com.restaurante.exception.TenantOwnerRequiredException;
import com.restaurante.exception.TenantOwnershipAlreadyCompleteException;
import com.restaurante.exception.TenantOwnershipBackfillInvalidException;
import com.restaurante.model.entity.BusinessAccount;
import com.restaurante.model.entity.BusinessAccountMember;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.BusinessAccountMemberEstado;
import com.restaurante.model.enums.BusinessAccountRole;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.BusinessAccountMemberRepository;
import com.restaurante.repository.BusinessAccountRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.provisioning.ProvisioningPlanCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantOwnershipBackfillService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final BusinessAccountRepository businessAccountRepository;
    private final BusinessAccountMemberRepository businessAccountMemberRepository;
    private final TenantGuard tenantGuard;
    private final UserPasswordManagementService passwordManagementService;

    /**
     * Gera um diagnóstico de integridade de ownership para um tenant específico.
     */
    @Transactional(readOnly = true)
    public TenantOwnershipBackfillDiagnosticResponse diagnose(Long tenantId) {
        tenantGuard.assertPlatformAdmin();

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ProvisioningException(
                        HttpStatus.NOT_FOUND,
                        "TENANT_NOT_FOUND",
                        "tenantId",
                        "Tenant não encontrado para ID: " + tenantId
                ));

        boolean hasBusinessAccount = tenant.getBusinessAccount() != null;
        Long businessAccountId = hasBusinessAccount ? tenant.getBusinessAccount().getId() : null;
        String businessAccountName = hasBusinessAccount ? tenant.getBusinessAccount().getNome() : null;
        boolean isBusinessAccountActive = hasBusinessAccount &&
                tenant.getBusinessAccount().getEstado() == BusinessAccountEstado.ATIVA;

        List<TenantUser> tenantUsers = tenantUserRepository.findByTenantId(tenantId);

        // Find tenant owners (role TENANT_OWNER and state ATIVO)
        List<TenantUser> activeOwners = tenantUsers.stream()
                .filter(tu -> tu.getRole() == TenantUserRole.TENANT_OWNER && tu.getEstado() == TenantUserEstado.ATIVO)
                .toList();

        boolean hasTenantOwner = !activeOwners.isEmpty();
        boolean hasPlatformAdminAsTenantOwner = false;
        User tenantOwnerUser = null;

        if (hasTenantOwner) {
            for (TenantUser tu : activeOwners) {
                if (isPlatformAdmin(tu.getUser())) {
                    hasPlatformAdminAsTenantOwner = true;
                }
            }
            // Prefer non-platform admin if any
            TenantUser primaryOwner = activeOwners.stream()
                    .filter(tu -> !isPlatformAdmin(tu.getUser()))
                    .findFirst()
                    .orElse(activeOwners.get(0));
            tenantOwnerUser = primaryOwner.getUser();
        }

        boolean hasBusinessAccountOwner = false;
        boolean hasPlatformAdminAsBusinessAccountOwner = false;

        if (hasBusinessAccount) {
            List<BusinessAccountMember> members = businessAccountMemberRepository
                    .findByBusinessAccountIdOrderByIdAsc(businessAccountId);
            List<BusinessAccountMember> activeBAOwners = members.stream()
                    .filter(m -> (m.getRole() == BusinessAccountRole.OWNER || m.getRole() == BusinessAccountRole.ADMIN)
                            && m.getEstado() == BusinessAccountMemberEstado.ATIVO)
                    .toList();
            hasBusinessAccountOwner = !activeBAOwners.isEmpty();
            if (hasBusinessAccountOwner) {
                hasPlatformAdminAsBusinessAccountOwner = activeBAOwners.stream()
                        .allMatch(m -> isPlatformAdmin(m.getUser()));
            }
        }

        List<String> blockingReasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!hasBusinessAccount) {
            blockingReasons.add("BUSINESS_ACCOUNT_MISSING");
        } else if (!isBusinessAccountActive) {
            blockingReasons.add("BUSINESS_ACCOUNT_INACTIVE");
        }

        if (!hasTenantOwner) {
            blockingReasons.add("TENANT_OWNER_MISSING");
        } else {
            if (hasPlatformAdminAsTenantOwner) {
                blockingReasons.add("PLATFORM_ADMIN_IS_TENANT_OWNER");
            }
            if (tenantOwnerUser != null) {
                if (tenantOwnerUser.getEmail() == null || tenantOwnerUser.getEmail().isBlank()) {
                    blockingReasons.add("OWNER_EMAIL_MISSING");
                }
                if (tenantOwnerUser.getRoles() == null || !tenantOwnerUser.getRoles().contains(Role.ROLE_GERENTE)) {
                    blockingReasons.add("OWNER_ACCESS_INCOMPLETE");
                }
            }
        }

        if (hasBusinessAccount) {
            if (!hasBusinessAccountOwner) {
                blockingReasons.add("BUSINESS_ACCOUNT_OWNER_MISSING");
            } else if (hasPlatformAdminAsBusinessAccountOwner) {
                blockingReasons.add("PLATFORM_ADMIN_IS_BUSINESS_ACCOUNT_OWNER");
            }
        }

        if (activeOwners.size() > 1) {
            blockingReasons.add("TENANT_ACCESS_INCONSISTENT");
            warnings.add("Existem múltiplos usuários com papel TENANT_OWNER ativos no tenant.");
        }

        boolean requiresBackfill = !blockingReasons.isEmpty();

        return TenantOwnershipBackfillDiagnosticResponse.builder()
                .tenantId(tenant.getId())
                .tenantName(tenant.getNome())
                .tenantCode(tenant.getTenantCode())
                .tenantStatus(tenant.getEstado() != null ? tenant.getEstado().name() : null)
                .businessAccountId(businessAccountId)
                .businessAccountName(businessAccountName)
                .hasBusinessAccount(hasBusinessAccount)
                .hasTenantOwner(hasTenantOwner)
                .hasBusinessAccountOwner(hasBusinessAccountOwner)
                .hasPlatformAdminAsTenantOwner(hasPlatformAdminAsTenantOwner)
                .hasPlatformAdminAsBusinessAccountOwner(hasPlatformAdminAsBusinessAccountOwner)
                .ownerUserId(tenantOwnerUser != null ? tenantOwnerUser.getId() : null)
                .ownerName(tenantOwnerUser != null ? tenantOwnerUser.getNomeCompleto() : null)
                .ownerEmail(tenantOwnerUser != null ? tenantOwnerUser.getEmail() : null)
                .ownerPhone(tenantOwnerUser != null ? tenantOwnerUser.getTelefone() : null)
                .requiresBackfill(requiresBackfill)
                .blockingReasons(blockingReasons)
                .warnings(warnings)
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }

    /**
     * Executa a correção de ownership e associação de business account para um tenant.
     */
    @Transactional
    public TenantOwnershipBackfillResponse executeBackfill(Long tenantId, TenantOwnershipBackfillRequest request) {
        tenantGuard.assertPlatformAdmin();

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ProvisioningException(
                        HttpStatus.NOT_FOUND,
                        "TENANT_NOT_FOUND",
                        "tenantId",
                        "Tenant não encontrado para ID: " + tenantId
                ));

        TenantOwnershipBackfillDiagnosticResponse diagnostic = diagnose(tenantId);
        if (!diagnostic.isRequiresBackfill()) {
            // Check if user specifically requested a different owner or business account, or check idempotency.
            boolean sameOwner = request.getOwnerUserId() == null || request.getOwnerUserId().equals(diagnostic.getOwnerUserId());
            boolean sameBusiness = request.getBusinessAccountId() == null || request.getBusinessAccountId().equals(diagnostic.getBusinessAccountId());

            if (sameOwner && sameBusiness) {
                log.info("Tenant {} ownership is already complete. Backfill is idempotent.", tenantId);
                return TenantOwnershipBackfillResponse.builder()
                        .tenantId(tenantId)
                        .businessAccountId(diagnostic.getBusinessAccountId())
                        .ownerUserId(diagnostic.getOwnerUserId())
                        .ownerEmail(diagnostic.getOwnerEmail())
                        .tenantUserId(
                                tenantUserRepository.findByTenantIdAndUserId(tenantId, diagnostic.getOwnerUserId())
                                        .map(com.restaurante.model.entity.BaseEntity::getId).orElse(null)
                        )
                        .businessAccountMemberId(
                                businessAccountMemberRepository.findByBusinessAccountIdAndUserId(diagnostic.getBusinessAccountId(), diagnostic.getOwnerUserId())
                                        .map(com.restaurante.model.entity.BaseEntity::getId).orElse(null)
                        )
                        .temporaryPassword(null)
                        .temporaryPasswordExpiresAt(null)
                        .requiresBackfill(false)
                        .status("COMPLETED")
                        .build();
            } else {
                throw new TenantOwnershipAlreadyCompleteException(tenantId);
            }
        }

        // 1. Resolve or Create Business Account
        BusinessAccount businessAccount = resolveOrCreateBusinessAccount(tenant, request);

        // 2. Resolve or Create Owner User
        User owner = resolveOrCreateOwnerUser(request);

        // 3. Grant ROLE_GERENTE and ensure active user
        owner.setAtivo(true);
        Set<Role> roles = owner.getRoles() == null || owner.getRoles().isEmpty()
                ? new java.util.HashSet<>()
                : new java.util.HashSet<>(owner.getRoles());
        roles.add(Role.ROLE_GERENTE);
        owner.setRoles(roles);
        
        // If name, phone, or email is blank on the resolved user, populate them
        if ((owner.getNomeCompleto() == null || owner.getNomeCompleto().isBlank()) && request.getOwnerName() != null) {
            owner.setNomeCompleto(request.getOwnerName().trim());
        }
        if ((owner.getEmail() == null || owner.getEmail().isBlank()) && request.getOwnerEmail() != null) {
            owner.setEmail(request.getOwnerEmail().trim().toLowerCase(Locale.ROOT));
        }
        if ((owner.getTelefone() == null || owner.getTelefone().isBlank()) && request.getOwnerPhone() != null) {
            owner.setTelefone(request.getOwnerPhone().trim());
        }

        // Generate temporary password if requested or if user has no password yet (new user)
        boolean isNew = owner.getId() == null;
        String temporaryPassword = null;
        LocalDateTime tempExpires = null;
        if (isNew || Boolean.TRUE.equals(request.getGenerateTemporaryPassword()) || owner.getPassword() == null || owner.getPassword().isEmpty()) {
            temporaryPassword = passwordManagementService.generateTemporaryPassword();
            passwordManagementService.applyTemporaryPassword(owner, temporaryPassword);
            tempExpires = owner.getTemporaryPasswordExpiresAt();
        }

        owner = userRepository.saveAndFlush(owner);

        // 4. Revoke/Remove Platform Admin owners from TenantUser & BusinessAccountMember
        revokePlatformAdminOwners(tenant, businessAccount);

        // 5. Ensure TenantUser OWNER membership
        TenantUser tenantUser = tenantUserRepository.findByTenantIdAndUserId(tenant.getId(), owner.getId())
                .orElseGet(TenantUser::new);
        tenantUser.setTenant(tenant);
        tenantUser.setUser(owner);
        tenantUser.setRole(TenantUserRole.TENANT_OWNER);
        tenantUser.setEstado(TenantUserEstado.ATIVO);
        tenantUser = tenantUserRepository.saveAndFlush(tenantUser);

        // 6. Ensure BusinessAccountMember OWNER membership
        BusinessAccountMember member = businessAccountMemberRepository
                .findByBusinessAccountIdAndUserId(businessAccount.getId(), owner.getId())
                .orElseGet(BusinessAccountMember::new);
        member.setBusinessAccount(businessAccount);
        member.setUser(owner);
        member.setRole(BusinessAccountRole.OWNER);
        member.setEstado(BusinessAccountMemberEstado.ATIVO);
        member = businessAccountMemberRepository.saveAndFlush(member);

        // 7. Ensure Owner is the responsavel of the BusinessAccount
        if (businessAccount.getResponsavel() == null || !businessAccount.getResponsavel().getId().equals(owner.getId())) {
            businessAccount.setResponsavel(owner);
            businessAccount = businessAccountRepository.saveAndFlush(businessAccount);
        }

        // 8. Link business account to tenant
        if (tenant.getBusinessAccount() == null || !tenant.getBusinessAccount().getId().equals(businessAccount.getId())) {
            tenant.setBusinessAccount(businessAccount);
            tenant = tenantRepository.saveAndFlush(tenant);
        }

        log.info("Backfill executed successfully for tenant {}. Owner user ID: {}. Business account ID: {}.",
                tenant.getId(), owner.getId(), businessAccount.getId());

        return TenantOwnershipBackfillResponse.builder()
                .tenantId(tenant.getId())
                .businessAccountId(businessAccount.getId())
                .ownerUserId(owner.getId())
                .ownerEmail(owner.getEmail())
                .tenantUserId(tenantUser.getId())
                .businessAccountMemberId(member.getId())
                .temporaryPassword(temporaryPassword)
                .temporaryPasswordExpiresAt(tempExpires)
                .requiresBackfill(false)
                .status("COMPLETED")
                .build();
    }

    private BusinessAccount resolveOrCreateBusinessAccount(Tenant tenant, TenantOwnershipBackfillRequest request) {
        if (request.getBusinessAccountId() != null) {
            BusinessAccount account = businessAccountRepository.findById(request.getBusinessAccountId())
                    .orElseThrow(() -> new ProvisioningException(
                            HttpStatus.NOT_FOUND,
                            "BUSINESS_ACCOUNT_NOT_FOUND",
                            "businessAccountId",
                            "BusinessAccount não encontrada para ID: " + request.getBusinessAccountId()
                    ));
            if (account.getEstado() != BusinessAccountEstado.ATIVA) {
                account.setEstado(BusinessAccountEstado.ATIVA);
                account = businessAccountRepository.saveAndFlush(account);
            }
            return account;
        }

        if (tenant.getBusinessAccount() != null) {
            BusinessAccount account = tenant.getBusinessAccount();
            if (account.getEstado() != BusinessAccountEstado.ATIVA) {
                account.setEstado(BusinessAccountEstado.ATIVA);
                account = businessAccountRepository.saveAndFlush(account);
            }
            return account;
        }

        // Create new business account
        String accountName = tenant.getNome();
        if (accountName == null || accountName.isBlank()) {
            accountName = "Empresa Legada " + tenant.getTenantCode();
        }
        BusinessAccount account = new BusinessAccount();
        account.setNome(accountName);
        account.setSlug(nextBusinessAccountSlug(accountName));
        account.setEstado(BusinessAccountEstado.ATIVA);
        account.setMaxTenants(5);
        account.setProvisionedAt(LocalDateTime.now());
        account.setProvisionedBy("BACKFILL-SERVICE");
        return businessAccountRepository.saveAndFlush(account);
    }

    private User resolveOrCreateOwnerUser(TenantOwnershipBackfillRequest request) {
        if (request.getOwnerUserId() != null) {
            User user = userRepository.findById(request.getOwnerUserId())
                    .orElseThrow(() -> new TenantOwnershipBackfillInvalidException(
                            "O proprietário especificado em ownerUserId (" + request.getOwnerUserId() + ") não existe."
                    ));
            if (isPlatformAdmin(user)) {
                throw new PlatformAdminCannotOwnTenantException();
            }
            return user;
        }

        // Search for existing user based on fields in request
        Map<Long, User> matches = new LinkedHashMap<>();
        if (request.getOwnerUsername() != null && !request.getOwnerUsername().isBlank()) {
            userRepository.findByUsername(request.getOwnerUsername().trim()).ifPresent(user -> matches.put(user.getId(), user));
        }
        if (request.getOwnerEmail() != null && !request.getOwnerEmail().isBlank()) {
            userRepository.findByEmail(request.getOwnerEmail().trim().toLowerCase(Locale.ROOT)).ifPresent(user -> matches.put(user.getId(), user));
        }
        if (request.getOwnerPhone() != null && !request.getOwnerPhone().isBlank()) {
            userRepository.findByTelefone(request.getOwnerPhone().trim()).ifPresent(user -> matches.put(user.getId(), user));
        }

        if (matches.size() > 1) {
            throw new TenantOwnershipBackfillInvalidException("Os dados do owner fornecidos apontam para usuários diferentes no banco de dados.");
        }

        User existing = matches.values().stream().findFirst().orElse(null);
        if (existing != null) {
            if (isPlatformAdmin(existing)) {
                throw new PlatformAdminCannotOwnTenantException();
            }
            return existing;
        }

        // Must create new user. Contact info is mandatory.
        if (request.getOwnerName() == null || request.getOwnerName().isBlank()) {
            throw new TenantOwnerRequiredException();
        }
        boolean hasContact = (request.getOwnerPhone() != null && !request.getOwnerPhone().isBlank())
                || (request.getOwnerEmail() != null && !request.getOwnerEmail().isBlank())
                || (request.getOwnerUsername() != null && !request.getOwnerUsername().isBlank());
        if (!hasContact) {
            throw new TenantOwnerRequiredException();
        }

        String username = resolveUniqueUsername(request);

        User newUser = new User();
        newUser.setUsername(username);
        newUser.setNomeCompleto(request.getOwnerName().trim());
        if (request.getOwnerEmail() != null && !request.getOwnerEmail().isBlank()) {
            newUser.setEmail(request.getOwnerEmail().trim().toLowerCase(Locale.ROOT));
        }
        if (request.getOwnerPhone() != null && !request.getOwnerPhone().isBlank()) {
            newUser.setTelefone(request.getOwnerPhone().trim());
        } else {
            // Generate temporary unique phone if completely empty to satisfy DB constraint
            newUser.setTelefone("999" + randomSuffix(6));
        }
        newUser.setAtivo(true);
        return newUser;
    }

    private void revokePlatformAdminOwners(Tenant tenant, BusinessAccount businessAccount) {
        // Find tenant users that are Platform Admins and remove/demote them
        List<TenantUser> tenantUsers = tenantUserRepository.findByTenantId(tenant.getId());
        for (TenantUser tu : tenantUsers) {
            if (tu.getRole() == TenantUserRole.TENANT_OWNER && isPlatformAdmin(tu.getUser())) {
                log.info("Revoking TenantUser role for Platform Admin user: {}", tu.getUser().getUsername());
                // Delete or demote. Deleting is cleaner because Platform Admins shouldn't have memberships.
                tenantUserRepository.delete(tu);
            }
        }

        // Also check BusinessAccountMember
        List<BusinessAccountMember> members = businessAccountMemberRepository
                .findByBusinessAccountIdOrderByIdAsc(businessAccount.getId());
        for (BusinessAccountMember m : members) {
            if ((m.getRole() == BusinessAccountRole.OWNER || m.getRole() == BusinessAccountRole.ADMIN)
                    && isPlatformAdmin(m.getUser())) {
                log.info("Revoking BusinessAccountMember role for Platform Admin user: {}", m.getUser().getUsername());
                businessAccountMemberRepository.delete(m);
            }
        }
    }

    private boolean isPlatformAdmin(User user) {
        if (user == null) return false;
        Set<Role> roles = user.getRoles();
        return roles != null && !roles.isEmpty()
                && roles.stream().allMatch(r -> r == Role.ROLE_ADMIN);
    }

    private String resolveUniqueUsername(TenantOwnershipBackfillRequest request) {
        String base = request.getOwnerUsername();
        if (base == null || base.isBlank()) {
            if (request.getOwnerEmail() != null && !request.getOwnerEmail().isBlank()) {
                base = request.getOwnerEmail().trim().toLowerCase(Locale.ROOT);
            } else if (request.getOwnerPhone() != null && !request.getOwnerPhone().isBlank()) {
                base = "u" + request.getOwnerPhone().replaceAll("[^0-9]", "");
            } else {
                base = "user";
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

    private String normalizeUsername(String username) {
        if (username == null) {
            return "";
        }
        return username.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9@._-]", "");
    }

    private String randomSuffix(int len) {
        String normalized = java.util.UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
        return normalized.substring(0, Math.min(len, normalized.length()));
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
}
