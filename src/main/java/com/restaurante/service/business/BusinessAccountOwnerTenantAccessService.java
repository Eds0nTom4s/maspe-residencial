package com.restaurante.service.business;

import com.restaurante.exception.ConflictException;
import com.restaurante.model.entity.BusinessAccount;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.TenantUserAccessOrigin;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.service.security.TenantUserAccessVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Mantém a role operacional derivada do principal Owner da BusinessAccount. */
@Service
@RequiredArgsConstructor
public class BusinessAccountOwnerTenantAccessService {

    private final TenantRepository tenants;
    private final TenantUserRepository tenantUsers;
    private final TenantUserAccessVersionService accessVersions;

    /**
     * O caller deve manter lock pessimista da BusinessAccount. Provisioning usa
     * a mesma ordem global (Account antes de Tenant), serializando os dois fluxos.
     */
    @Transactional
    public SynchronizationResult synchronize(BusinessAccount account) {
        User currentOwner = account.getResponsavel();
        if (currentOwner == null || !Boolean.TRUE.equals(currentOwner.getAtivo())) {
            throw new ConflictException("BUSINESS_ACCOUNT_OWNER_REQUIRED");
        }

        int created = 0;
        int reactivated = 0;
        int revoked = 0;
        for (Tenant tenant : tenants.findByBusinessAccountIdOrderByIdAsc(account.getId())) {
            List<TenantUser> derivedOwners = tenantUsers.findAllByTenantIdAndRoleAndAccessOrigin(
                    tenant.getId(),
                    TenantUserRole.TENANT_OWNER,
                    TenantUserAccessOrigin.BUSINESS_ACCOUNT_OWNER
            );
            Set<Long> changedUsers = new HashSet<>();
            TenantUser current = null;
            for (TenantUser row : derivedOwners) {
                if (Objects.equals(row.getUser().getId(), currentOwner.getId())) {
                    current = row;
                } else if (row.getEstado() != TenantUserEstado.REMOVIDO) {
                    row.setEstado(TenantUserEstado.REMOVIDO);
                    changedUsers.add(row.getUser().getId());
                    revoked++;
                }
            }

            if (current == null) {
                current = new TenantUser();
                current.setTenant(tenant);
                current.setUser(currentOwner);
                current.setRole(TenantUserRole.TENANT_OWNER);
                current.setEstado(TenantUserEstado.ATIVO);
                current.setAccessOrigin(TenantUserAccessOrigin.BUSINESS_ACCOUNT_OWNER);
                tenantUsers.save(current);
                changedUsers.add(currentOwner.getId());
                created++;
            } else if (current.getEstado() != TenantUserEstado.ATIVO) {
                current.setEstado(TenantUserEstado.ATIVO);
                changedUsers.add(currentOwner.getId());
                reactivated++;
            }

            tenantUsers.saveAll(derivedOwners);
            tenantUsers.flush();
            changedUsers.forEach(userId -> accessVersions.increment(tenant.getId(), userId));
        }
        return new SynchronizationResult(created, reactivated, revoked);
    }

    public record SynchronizationResult(int created, int reactivated, int revoked) {
        public int mutations() {
            return created + reactivated + revoked;
        }
    }
}
