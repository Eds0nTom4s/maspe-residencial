package com.restaurante.financeiro.monitoramento.dto;

import com.restaurante.model.enums.CallbackProcessingStatus;

import java.time.LocalDateTime;

public class CallbackLogDetalheDTO {

    private Long id;
    private Long tenantId;
    private Long pagamentoId;
    private String externalReference;
    private String provider;
    private Boolean signatureValid;
    private CallbackProcessingStatus processingStatus;
    private String processingError;
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;
    private String statusRecebido;
    private String gatewayChargeId;
    private String headersJson;
    private String payloadJson;
    private String rawBody;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getPagamentoId() { return pagamentoId; }
    public void setPagamentoId(Long pagamentoId) { this.pagamentoId = pagamentoId; }

    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String externalReference) { this.externalReference = externalReference; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public Boolean getSignatureValid() { return signatureValid; }
    public void setSignatureValid(Boolean signatureValid) { this.signatureValid = signatureValid; }

    public CallbackProcessingStatus getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(CallbackProcessingStatus processingStatus) { this.processingStatus = processingStatus; }

    public String getProcessingError() { return processingError; }
    public void setProcessingError(String processingError) { this.processingError = processingError; }

    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    public String getStatusRecebido() { return statusRecebido; }
    public void setStatusRecebido(String statusRecebido) { this.statusRecebido = statusRecebido; }

    public String getGatewayChargeId() { return gatewayChargeId; }
    public void setGatewayChargeId(String gatewayChargeId) { this.gatewayChargeId = gatewayChargeId; }

    public String getHeadersJson() { return headersJson; }
    public void setHeadersJson(String headersJson) { this.headersJson = headersJson; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public String getRawBody() { return rawBody; }
    public void setRawBody(String rawBody) { this.rawBody = rawBody; }
}

