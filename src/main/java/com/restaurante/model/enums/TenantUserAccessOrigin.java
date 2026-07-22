package com.restaurante.model.enums;

/**
 * Proveniência de uma role operacional.
 *
 * <p>A origem empresarial é reservada à relação derivada entre o principal
 * Owner da BusinessAccount e os seus Tenants. Todas as concessões feitas pela
 * administração tenant ou por fluxos legacy permanecem explícitas.</p>
 */
public enum TenantUserAccessOrigin {
    EXPLICIT,
    BUSINESS_ACCOUNT_OWNER
}
