package com.restaurante.exception;

import org.springframework.http.HttpStatus;

/**
 * Exceção lançada quando tenta-se fazer backfill em um tenant que já possui ownership completo.
 *
 * Código de erro: TENANT_OWNERSHIP_ALREADY_COMPLETE
 * HTTP: 409
 */
public class TenantOwnershipAlreadyCompleteException extends ProvisioningException {

    public TenantOwnershipAlreadyCompleteException(Long tenantId) {
        super(
                HttpStatus.CONFLICT,
                "TENANT_OWNERSHIP_ALREADY_COMPLETE",
                "tenantId",
                "O tenant com ID " + tenantId + " já possui ownership completo e não precisa de backfill.",
                "Este tenant já possui uma BusinessAccount válida e um Owner/Tenant Admin ativo.",
                "Não é necessário executar o backfill para este tenant.",
                null
        );
    }
}
