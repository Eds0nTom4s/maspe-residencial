package com.restaurante.exception;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Payload de erro padronizado para endpoints platform de provisionamento.
 */
public class ProvisioningErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String code;
    private String field;
    private String detail;
    private String recommendedAction;
    private String path;
    private String correlationId;
    private Map<String, Object> extra;

    public ProvisioningErrorResponse() {}

    public ProvisioningErrorResponse(
            LocalDateTime timestamp,
            int status,
            String error,
            String message,
            String code,
            String field,
            String detail,
            String recommendedAction,
            String path,
            String correlationId,
            Map<String, Object> extra
    ) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.code = code;
        this.field = field;
        this.detail = detail;
        this.recommendedAction = recommendedAction;
        this.path = path;
        this.correlationId = correlationId;
        this.extra = extra;
    }

    public LocalDateTime getTimestamp() { return timestamp; }
    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMessage() { return message; }
    public String getCode() { return code; }
    public String getField() { return field; }
    public String getDetail() { return detail; }
    public String getRecommendedAction() { return recommendedAction; }
    public String getPath() { return path; }
    public String getCorrelationId() { return correlationId; }
    public Map<String, Object> getExtra() { return extra; }
}

