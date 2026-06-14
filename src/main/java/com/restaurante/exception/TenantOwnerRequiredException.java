package com.restaurante.exception;

import org.springframework.http.HttpStatus;

/**
 * Exceção lançada quando um tenant é provisionado sem Owner/Tenant Admin associado.
 *
 * Código de erro: TENANT_OWNER_REQUIRED
 * HTTP: 400
 */
public class TenantOwnerRequiredException extends ProvisioningException {

    public TenantOwnerRequiredException() {
        super(
                HttpStatus.BAD_REQUEST,
                "TENANT_OWNER_REQUIRED",
                "ownerNome",
                "Associe um administrador do negócio antes de concluir o provisionamento.",
                "Todo tenant operacional deve ter um Owner/Tenant Admin real associado.",
                "Forneça ownerNome + ownerTelefone (ou ownerUserId) no request de provisionamento.",
                null
        );
    }
}
