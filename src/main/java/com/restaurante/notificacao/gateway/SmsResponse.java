package com.restaurante.notificacao.gateway;

/**
 * Response genérico de envio de SMS
 * Agnóstico de provedor (TelcoSMS, Twilio, etc)
 */
public class SmsResponse {
    
    private final boolean success;
    private final String message;
    private final String messageId;
    private final String errorCode;
    
    public SmsResponse(boolean success, String message) {
        this(success, message, null, null);
    }
    
    public SmsResponse(boolean success, String message, String messageId, String errorCode) {
        this.success = success;
        this.message = message;
        this.messageId = messageId;
        this.errorCode = errorCode;
    }
    
    // Factory methods
    
    public static SmsResponse success(String messageId) {
        return new SmsResponse(true, "SMS enviado com sucesso", messageId, null);
    }
    
    public static SmsResponse success(String message, String messageId) {
        return new SmsResponse(true, message, messageId, null);
    }
    
    public static SmsResponse error(String message) {
        return new SmsResponse(false, message, null, null);
    }
    
    public static SmsResponse error(String message, String errorCode) {
        return new SmsResponse(false, message, null, errorCode);
    }
    
    // Getters
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    @Override
    public String toString() {
        return String.format("SmsResponse{success=%s, message='%s', messageId='%s', errorCode='%s'}", 
            success, message, messageId, errorCode);
    }
}
