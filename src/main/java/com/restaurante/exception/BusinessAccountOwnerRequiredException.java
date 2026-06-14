package com.restaurante.exception;

import org.springframework.http.HttpStatus;

/**
 * Exceção lançada quando uma BusinessAccount não possui membro com role OWNER/ADMIN real.
 *
 * Código de erro: BUSINESS_ACCOUNT_OWNER_REQUIRED
 * HTTP: 400
 */
public class BusinessAccountOwnerRequiredException extends ProvisioningException {

    public BusinessAccountOwnerRequiredException() {
        super(
                HttpStatus.BAD_REQUEST,
                "BUSINESS_ACCOUNT_OWNER_REQUIRED",
                "businessAccountId",
                "A Conta Empresarial selecionada não possui proprietário real. Associe um Owner antes de provisionar.",
                "Toda Conta Empresarial operacional deve ter pelo menos um membro com role OWNER ou ADMIN.",
                "Forneça ownerNome + ownerTelefone no request para criar ou associar um owner à conta empresarial.",
                null
        );
    }
}
