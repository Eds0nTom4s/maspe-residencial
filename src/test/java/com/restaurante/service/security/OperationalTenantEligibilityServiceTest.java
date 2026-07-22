package com.restaurante.service.security;

import com.restaurante.exception.TenantAccessDeniedException;
import com.restaurante.model.entity.BusinessAccount;
import com.restaurante.model.entity.Subscricao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.repository.SubscricaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalTenantEligibilityServiceTest {

    @Mock SubscricaoRepository subscriptions;
    OperationalTenantEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new OperationalTenantEligibilityService(subscriptions);
    }

    @Test
    void legacyTenantRequiresOnlyActiveTenantAndMembership() {
        Tenant tenant = tenant(TenantEstado.ATIVO, null);
        when(subscriptions.findByTenantIdAndEstado(10L, SubscricaoEstado.ATIVA))
                .thenReturn(Optional.empty());

        assertThat(service.evaluate(tenant, List.of(membership(TenantUserEstado.ATIVO))).eligible())
                .isTrue();
    }

    @Test
    void canonicalTenantRequiresActiveAccountAndSubscription() {
        BusinessAccount account = account(BusinessAccountEstado.ATIVA);
        Tenant tenant = tenant(TenantEstado.ATIVO, account);
        Subscricao subscription = new Subscricao();
        when(subscriptions.findByTenantIdAndEstado(10L, SubscricaoEstado.ATIVA))
                .thenReturn(Optional.of(subscription));

        assertThat(service.evaluate(tenant, List.of(membership(TenantUserEstado.ATIVO))).eligible())
                .isTrue();
    }

    @Test
    void inactiveAccountHasStableCode() {
        Tenant tenant = tenant(TenantEstado.ATIVO, account(BusinessAccountEstado.RASCUNHO));
        when(subscriptions.findByTenantIdAndEstado(10L, SubscricaoEstado.ATIVA))
                .thenReturn(Optional.of(new Subscricao()));

        assertThatThrownBy(() -> service.requireEligible(
                tenant, List.of(membership(TenantUserEstado.ATIVO))))
                .isInstanceOf(TenantAccessDeniedException.class)
                .extracting("code")
                .isEqualTo("BUSINESS_ACCOUNT_NOT_OPERATIONAL");
    }

    @Test
    void inactiveSubscriptionHasStableCode() {
        Tenant tenant = tenant(TenantEstado.ATIVO, account(BusinessAccountEstado.ATIVA));
        when(subscriptions.findByTenantIdAndEstado(10L, SubscricaoEstado.ATIVA))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireEligible(
                tenant, List.of(membership(TenantUserEstado.ATIVO))))
                .isInstanceOf(TenantAccessDeniedException.class)
                .extracting("code")
                .isEqualTo("SUBSCRIPTION_NOT_OPERATIONAL");
    }

    @Test
    void inactiveMembershipIsRejectedBeforeTenantMetadata() {
        Tenant tenant = tenant(TenantEstado.ATIVO, account(BusinessAccountEstado.ATIVA));

        assertThatThrownBy(() -> service.requireEligible(
                tenant, List.of(membership(TenantUserEstado.SUSPENSO))))
                .isInstanceOf(TenantAccessDeniedException.class)
                .extracting("code")
                .isEqualTo("MEMBERSHIP_NOT_ACTIVE");
    }

    private Tenant tenant(TenantEstado estado, BusinessAccount account) {
        Tenant tenant = new Tenant();
        tenant.setId(10L);
        tenant.setEstado(estado);
        tenant.setBusinessAccount(account);
        return tenant;
    }

    private BusinessAccount account(BusinessAccountEstado estado) {
        BusinessAccount account = new BusinessAccount();
        account.setId(20L);
        account.setEstado(estado);
        return account;
    }

    private TenantUser membership(TenantUserEstado estado) {
        TenantUser membership = new TenantUser();
        membership.setEstado(estado);
        return membership;
    }
}
