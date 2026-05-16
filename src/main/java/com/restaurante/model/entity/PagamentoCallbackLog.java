package com.restaurante.model.entity;

import com.restaurante.model.enums.CallbackProcessingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "pagamento_callback_logs", indexes = {
        @Index(name = "idx_callback_log_received_at", columnList = "received_at"),
        @Index(name = "idx_callback_log_external_ref", columnList = "external_reference"),
        @Index(name = "idx_callback_log_pagamento", columnList = "pagamento_id"),
        @Index(name = "idx_callback_log_tenant", columnList = "tenant_id")
})
public class PagamentoCallbackLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pagamento_id")
    private Pagamento pagamento;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    @Column(name = "external_reference", length = 15)
    private String externalReference;

    @Column(name = "gateway_charge_id", length = 100)
    private String gatewayChargeId;

    @Column(name = "status_recebido", length = 40)
    private String statusRecebido;

    @Column(name = "headers_json", columnDefinition = "TEXT")
    private String headersJson;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "raw_body", columnDefinition = "TEXT")
    private String rawBody;

    @Column(name = "signature_valid")
    private Boolean signatureValid;

    @Column(name = "processed", nullable = false)
    private Boolean processed = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 30)
    private CallbackProcessingStatus processingStatus = CallbackProcessingStatus.RECEIVED;

    @Column(name = "processing_error", length = 500)
    private String processingError;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt = LocalDateTime.now();

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public PagamentoCallbackLog() {}

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public Pagamento getPagamento() { return pagamento; }
    public void setPagamento(Pagamento pagamento) { this.pagamento = pagamento; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String externalReference) { this.externalReference = externalReference; }

    public String getGatewayChargeId() { return gatewayChargeId; }
    public void setGatewayChargeId(String gatewayChargeId) { this.gatewayChargeId = gatewayChargeId; }

    public String getStatusRecebido() { return statusRecebido; }
    public void setStatusRecebido(String statusRecebido) { this.statusRecebido = statusRecebido; }

    public String getHeadersJson() { return headersJson; }
    public void setHeadersJson(String headersJson) { this.headersJson = headersJson; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public String getRawBody() { return rawBody; }
    public void setRawBody(String rawBody) { this.rawBody = rawBody; }

    public Boolean getSignatureValid() { return signatureValid; }
    public void setSignatureValid(Boolean signatureValid) { this.signatureValid = signatureValid; }

    public Boolean getProcessed() { return processed; }
    public void setProcessed(Boolean processed) { this.processed = processed; }

    public CallbackProcessingStatus getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(CallbackProcessingStatus processingStatus) { this.processingStatus = processingStatus; }

    public String getProcessingError() { return processingError; }
    public void setProcessingError(String processingError) { this.processingError = processingError; }

    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}

