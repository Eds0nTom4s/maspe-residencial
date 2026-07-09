package com.restaurante.model.entity;

import com.restaurante.model.enums.PublicQrOrderRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "public_qr_order_requests", indexes = {
        @Index(name = "idx_public_qr_order_tenant_created_at", columnList = "tenant_id, created_at"),
        @Index(name = "idx_public_qr_order_qr", columnList = "qr_code_operacional_id"),
        @Index(name = "idx_public_qr_order_pedido", columnList = "pedido_id")
})
public class PublicQrOrderRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "qr_code_operacional_id", nullable = false)
    private QrCodeOperacional qrCodeOperacional;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PublicQrOrderRequestStatus status;

    public PublicQrOrderRequest() {}

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public QrCodeOperacional getQrCodeOperacional() { return qrCodeOperacional; }
    public void setQrCodeOperacional(QrCodeOperacional qrCodeOperacional) { this.qrCodeOperacional = qrCodeOperacional; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }

    public Pedido getPedido() { return pedido; }
    public void setPedido(Pedido pedido) { this.pedido = pedido; }

    public PublicQrOrderRequestStatus getStatus() { return status; }
    public void setStatus(PublicQrOrderRequestStatus status) { this.status = status; }
}

