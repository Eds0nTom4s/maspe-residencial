package com.restaurante.financeiro.gateway.appypay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response da criação de cobrança na AppyPay
 * 
 * BASEADO EM ARENATICKET (VALIDADO EM PRODUÇÃO)
 */
public class AppyPayChargeResponse {
    
    @JsonProperty("chargeId")
    private String chargeId;
    
    @JsonProperty("merchantTransactionId")
    private String merchantTransactionId;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("amount")
    private Long amount;
    
    @JsonProperty("paymentMethod")
    private String paymentMethod;
    
    @JsonProperty("entity")
    private String entity;
    
    @JsonProperty("reference")
    private String reference;
    
    @JsonProperty("expiresAt")
    private String expiresAt;
    
    @JsonProperty("paymentUrl")
    private String paymentUrl;
    
    @JsonProperty("createdAt")
    private String createdAt;
    
    @JsonProperty("errorMessage")
    private String errorMessage;

    public AppyPayChargeResponse() {}

    public AppyPayChargeResponse(String chargeId, String merchantTransactionId, String status,
            Long amount, String paymentMethod, String entity, String reference,
            String expiresAt, String paymentUrl, String createdAt, String errorMessage) {
        this.chargeId = chargeId;
        this.merchantTransactionId = merchantTransactionId;
        this.status = status;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.entity = entity;
        this.reference = reference;
        this.expiresAt = expiresAt;
        this.paymentUrl = paymentUrl;
        this.createdAt = createdAt;
        this.errorMessage = errorMessage;
    }

    public String getChargeId() { return chargeId; }
    public void setChargeId(String chargeId) { this.chargeId = chargeId; }

    public String getMerchantTransactionId() { return merchantTransactionId; }
    public void setMerchantTransactionId(String merchantTransactionId) { this.merchantTransactionId = merchantTransactionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getEntity() { return entity; }
    public void setEntity(String entity) { this.entity = entity; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }

    public String getPaymentUrl() { return paymentUrl; }
    public void setPaymentUrl(String paymentUrl) { this.paymentUrl = paymentUrl; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String chargeId;
        private String merchantTransactionId;
        private String status;
        private Long amount;
        private String paymentMethod;
        private String entity;
        private String reference;
        private String expiresAt;
        private String paymentUrl;
        private String createdAt;
        private String errorMessage;

        public Builder chargeId(String chargeId) { this.chargeId = chargeId; return this; }
        public Builder merchantTransactionId(String merchantTransactionId) { this.merchantTransactionId = merchantTransactionId; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder amount(Long amount) { this.amount = amount; return this; }
        public Builder paymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; return this; }
        public Builder entity(String entity) { this.entity = entity; return this; }
        public Builder reference(String reference) { this.reference = reference; return this; }
        public Builder expiresAt(String expiresAt) { this.expiresAt = expiresAt; return this; }
        public Builder paymentUrl(String paymentUrl) { this.paymentUrl = paymentUrl; return this; }
        public Builder createdAt(String createdAt) { this.createdAt = createdAt; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

        public AppyPayChargeResponse build() {
            return new AppyPayChargeResponse(chargeId, merchantTransactionId, status, amount,
                    paymentMethod, entity, reference, expiresAt, paymentUrl, createdAt, errorMessage);
        }
    }
}
