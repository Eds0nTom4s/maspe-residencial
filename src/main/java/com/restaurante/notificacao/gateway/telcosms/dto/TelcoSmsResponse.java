package com.restaurante.notificacao.gateway.telcosms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response do envio de SMS via TelcoSMS
 */
public class TelcoSmsResponse {
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("message_id")
    private String messageId;
    
    @JsonProperty("error_code")
    private String errorCode;
    
    public TelcoSmsResponse() {
    }
    
    public TelcoSmsResponse(String status, String message) {
        this.status = status;
        this.message = message;
    }
    
    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status) || "sent".equalsIgnoreCase(status);
    }
    
    // Getters e Setters
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
}
