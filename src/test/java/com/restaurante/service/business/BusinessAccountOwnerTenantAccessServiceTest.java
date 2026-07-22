package com.restaurante.service.business;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessAccountOwnerTenantAccessServiceTest {

    @Mock TenantRepository tenants;
    @Mock TenantUserRepository tenantUsers;
    @Mock TenantUserAccessVersionService accessVersions;
    BusinessAccountOwnerTenantAccessService service;

    @BeforeEach
    void setUp() {
        service = new BusinessAccountOwnerTenantAccessService(tenants, tenantUsers, accessVersions);
    }

    @Test
    void synchronizesDerivedOwnerAndInvalidatesBothUsers() {
        User oldOwner = user(1L);
        User newOwner = user(2L);
        BusinessAccount account = account(10L, newOwner);
        Tenant tenant = tenant(20L, account);
        TenantUser oldDerived = row(tenant, oldOwner, TenantUserEstado.ATIVO);
        when(tenants.findByBusinessAccountIdOrderByIdAsc(10L)).thenReturn(List.of(tenant));
        when(tenantUsers.findAllByTenantIdAndRoleAndAccessOrigin(
                20L, TenantUserRole.TENANT_OWNER, TenantUserAccessOrigin.BUSINESS_ACCOUNT_OWNER))
                .thenReturn(List.of(oldDerived));
        when(tenantUsers.save(any(TenantUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.synchronize(account);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.revoked()).isEqualTo(1);
        assertThat(oldDerived.getEstado()).isEqualTo(TenantUserEstado.REMOVIDO);
        verify(accessVersions).increment(20L, 1L);
        verify(accessVersions).increment(20L, 2L);
    }

    @Test
    void replayWithoutMutationDoesNotIncrementAccessVersion() {
        User owner = user(2L);
        BusinessAccount account = account(10L, owner);
        Tenant tenant = tenant(20L, account);
        TenantUser current = row(tenant, owner, TenantUserEstado.ATIVO);
        when(tenants.findByBusinessAccountIdOrderByIdAsc(10L)).thenReturn(List.of(tenant));
        when(tenantUsers.findAllByTenantIdAndRoleAndAccessOrigin(
                20L, TenantUserRole.TENANT_OWNER, TenantUserAccessOrigin.BUSINESS_ACCOUNT_OWNER))
                .thenReturn(List.of(current));

        var result = service.synchronize(account);

        assertThat(result.mutations()).isZero();
        verify(accessVersions, never()).increment(any(), any());
    }

    @Test
    void reactivatesHistoricalDerivedOwnerWithoutDuplicate() {
        User owner = user(2L);
        BusinessAccount account = account(10L, owner);
        Tenant tenant = tenant(20L, account);
        TenantUser removed = row(tenant, owner, TenantUserEstado.REMOVIDO);
        when(tenants.findByBusinessAccountIdOrderByIdAsc(10L)).thenReturn(List.of(tenant));
        when(tenantUsers.findAllByTenantIdAndRoleAndAccessOrigin(
                20L, TenantUserRole.TENANT_OWNER, TenantUserAccessOrigin.BUSINESS_ACCOUNT_OWNER))
                .thenReturn(List.of(removed));

        var result = service.synchronize(account);

        assertThat(result.created()).isZero();
        assertThat(result.reactivated()).isEqualTo(1);
        assertThat(removed.getEstado()).isEqualTo(TenantUserEstado.ATIVO);
        verify(tenantUsers, never()).save(any(TenantUser.class));
        verify(accessVersions).increment(20L, 2L);
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setAtivo(true);
        return user;
    }

    private BusinessAccount account(Long id, User owner) {
        BusinessAccount account = new BusinessAccount();
        account.setId(id);
        account.setResponsavel(owner);
        return account;
    }

    private Tenant tenant(Long id, BusinessAccount account) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setBusinessAccount(account);
        return tenant;
    }

    private TenantUser row(Tenant tenant, User user, TenantUserEstado state) {
        TenantUser row = new TenantUser();
        row.setTenant(tenant);
        row.setUser(user);
        row.setRole(TenantUserRole.TENANT_OWNER);
        row.setEstado(state);
        row.setAccessOrigin(TenantUserAccessOrigin.BUSINESS_ACCOUNT_OWNER);
        return row;
    }
}
