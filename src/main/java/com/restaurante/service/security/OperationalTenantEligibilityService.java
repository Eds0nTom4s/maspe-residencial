package com.restaurante.service.security;

import com.restaurante.exception.TenantAccessDeniedException;
import com.restaurante.model.entity.Subscricao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.repository.SubscricaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Política única de elegibilidade para descoberta, selecção e resolução do
 * contexto operacional de um business user.
 */
@Service
@RequiredArgsConstructor
public class OperationalTenantEligibilityService {

    private final SubscricaoRepository subscricoes;

    public Eligibility evaluate(Tenant tenant, List<TenantUser> memberships) {
        boolean activeMembership = memberships != null && memberships.stream()
                .anyMatch(row -> row.getEstado() == TenantUserEstado.ATIVO);
        if (!activeMembership) {
            return Eligibility.denied("MEMBERSHIP_NOT_ACTIVE", "Membership activa obrigatória.");
        }
        if (tenant == null || tenant.getEstado() != TenantEstado.ATIVO) {
            return Eligibility.denied("TENANT_NOT_OPERATIONAL", "Tenant não está activo.");
        }

        Subscricao activeSubscription = subscricoes
                .findByTenantIdAndEstado(tenant.getId(), SubscricaoEstado.ATIVA)
                .orElse(null);

        // Compatibilidade deliberada: tenants legacy sem BusinessAccount
        // preservam o contrato anterior de Tenant + membership activos.
        if (tenant.getBusinessAccount() == null) {
            return Eligibility.allowed(activeSubscription);
        }
        if (tenant.getBusinessAccount().getEstado() != BusinessAccountEstado.ATIVA) {
            return Eligibility.denied(
                    "BUSINESS_ACCOUNT_NOT_OPERATIONAL",
                    "BusinessAccount não está activa para operação."
            );
        }
        if (activeSubscription == null) {
            return Eligibility.denied(
                    "SUBSCRIPTION_NOT_OPERATIONAL",
                    "Subscrição activa obrigatória para Tenant canónico."
            );
        }
        return Eligibility.allowed(activeSubscription);
    }

    public Eligibility requireEligible(Tenant tenant, List<TenantUser> memberships) {
        Eligibility eligibility = evaluate(tenant, memberships);
        if (!eligibility.eligible()) {
            throw new TenantAccessDeniedException(eligibility.code(), eligibility.reason());
        }
        return eligibility;
    }

    public record Eligibility(boolean eligible, String code, String reason, Subscricao activeSubscription) {
        static Eligibility allowed(Subscricao subscription) {
            return new Eligibility(true, null, null, subscription);
        }

        static Eligibility denied(String code, String reason) {
            return new Eligibility(false, code, reason, null);
        }
    }
}
