package com.restaurante.exception;

import org.springframework.http.HttpStatus;

/**
 * Exceção lançada quando a solicitação de backfill é inconsistente ou inválida.
 *
 * Código de erro: TENANT_OWNERSHIP_BACKFILL_INVALID
 * HTTP: 400
 */
public class TenantOwnershipBackfillInvalidException extends ProvisioningException {

    public TenantOwnershipBackfillInvalidException(String message) {
        super(
                HttpStatus.BAD_REQUEST,
                "TENANT_OWNERSHIP_BACKFILL_INVALID",
                "ownerUserId",
                message,
                "A tentativa de backfill não cumpre as regras de integridade ou é inconsistente.",
                "Corrija os parâmetros do request e certifique-se de que os dados do owner ou da BusinessAccount estão corretos.",
                null
        );
    }
}
