package com.restaurante.exception;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exceção de acesso negado com semântica HTTP (403).
 *
 * Mantém compatibilidade com handlers que capturam {@link AccessDeniedException},
 * mas garante que, mesmo sem {@code @ControllerAdvice}, o Spring MVC converta em 403.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class TenantAccessDeniedException extends AccessDeniedException {

    public TenantAccessDeniedException(String msg) {
        super(msg);
    }
}

