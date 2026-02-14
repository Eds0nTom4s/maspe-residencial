package com.restaurante.notificacao.gateway.telcosms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request para envio de SMS via TelcoSMS
 */
public class TelcoSmsRequest {
    
    @JsonProperty("message")
    private MessageData message;
    
    public TelcoSmsRequest() {
    }
    
    public TelcoSmsRequest(String apiKey, String phoneNumber, String messageBody) {
        this.message = new MessageData(apiKey, phoneNumber, messageBody);
    }
    
    public MessageData getMessage() {
        return message;
    }
    
    public void setMessage(MessageData message) {
        this.message = message;
    }
    
    /**
     * Dados da mensagem
     */
    public static class MessageData {
        
        @JsonProperty("api_key_app")
        private String apiKeyApp;
        
        @JsonProperty("phone_number")
        private String phoneNumber;
        
        @JsonProperty("message_body")
        private String messageBody;
        
        public MessageData() {
        }
        
        public MessageData(String apiKeyApp, String phoneNumber, String messageBody) {
            this.apiKeyApp = apiKeyApp;
            this.phoneNumber = phoneNumber;
            this.messageBody = messageBody;
        }
        
        public String getApiKeyApp() {
            return apiKeyApp;
        }
        
        public void setApiKeyApp(String apiKeyApp) {
            this.apiKeyApp = apiKeyApp;
        }
        
        public String getPhoneNumber() {
            return phoneNumber;
        }
        
        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }
        
        public String getMessageBody() {
            return messageBody;
        }
        
        public void setMessageBody(String messageBody) {
            this.messageBody = messageBody;
        }
    }
}
