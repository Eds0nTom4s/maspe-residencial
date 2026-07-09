package com.restaurante.exception;

/**
 * Indica que o token tenant-scoped está stale (roles/membership mudaram após emissão).
 * Cliente deve executar novamente /api/auth/tenant/select.
 */
public class TenantTokenStaleException extends RuntimeException {
    public TenantTokenStaleException(String message) {
        super(message);
    }
}

