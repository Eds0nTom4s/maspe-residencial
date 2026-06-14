package com.restaurante.exception;

import org.springframework.http.HttpStatus;

/**
 * Exceção lançada quando se tenta usar um Platform Admin como owner ou administrador
 * operacional de um tenant/BusinessAccount.
 *
 * Código de erro: PLATFORM_ADMIN_CANNOT_OWN_TENANT
 * HTTP: 409
 *
 * O Platform Admin é operador da plataforma, não proprietário de negócio.
 */
public class PlatformAdminCannotOwnTenantException extends ProvisioningException {

    public PlatformAdminCannotOwnTenantException() {
        super(
                HttpStatus.CONFLICT,
                "PLATFORM_ADMIN_CANNOT_OWN_TENANT",
                "ownerUserId",
                "O operador da plataforma não pode ser proprietário ou administrador operacional do negócio.",
                "Platform Admin não pode possuir tenant operacional. Crie ou selecione um administrador real do negócio.",
                "Forneça dados de um usuário real que não seja Platform Admin como owner do tenant.",
                null
        );
    }
}
