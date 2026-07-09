package com.restaurante.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Exceção padronizada para erros operacionais de provisionamento (PLATFORM_ADMIN).
 * Permite retornar códigos/fields estáveis sem vazar detalhes internos.
 */
public class ProvisioningException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String code;
    private final String field;
    private final String detail;
    private final String recommendedAction;
    private final Map<String, Object> extra;

    public ProvisioningException(
            HttpStatus httpStatus,
            String code,
            String field,
            String message,
            String detail,
            String recommendedAction,
            Map<String, Object> extra
    ) {
        super(message);
        this.httpStatus = httpStatus != null ? httpStatus : HttpStatus.BAD_REQUEST;
        this.code = code;
        this.field = field;
        this.detail = detail;
        this.recommendedAction = recommendedAction;
        this.extra = extra;
    }

    public ProvisioningException(HttpStatus httpStatus, String code, String field, String message) {
        this(httpStatus, code, field, message, null, null, null);
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getCode() { return code; }
    public String getField() { return field; }
    public String getDetail() { return detail; }
    public String getRecommendedAction() { return recommendedAction; }
    public Map<String, Object> getExtra() { return extra; }
}

