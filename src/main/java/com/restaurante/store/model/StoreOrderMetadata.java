package com.restaurante.store.model;

import com.restaurante.model.entity.BaseEntity;
import com.restaurante.model.entity.Pedido;
import jakarta.persistence.*;

/**
 * Metadados da ordem da loja sem poluir o agregado Pedido do motor.
 */
@Entity
@Table(name = "store_order_metadata", indexes = {
        @Index(name = "idx_store_order_pedido", columnList = "pedido_id", unique = true),
        @Index(name = "idx_store_order_socio", columnList = "socio_id"),
        @Index(name = "idx_store_order_idempotency", columnList = "idempotency_key", unique = true)
})
public class StoreOrderMetadata extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false, unique = true)
    private Pedido pedido;

    @Column(name = "socio_id", nullable = false, length = 100)
    private String socioId;

    @Column(name = "idempotency_key", unique = true, length = 120)
    private String idempotencyKey;

    @Column(name = "metodo_pagamento", length = 30)
    private String metodoPagamento;

    @Column(name = "payment_url", length = 500)
    private String paymentUrl;

    @Column(name = "entidade", length = 20)
    private String entidade;

    @Column(name = "referencia", length = 40)
    private String referencia;

    @Column(name = "endereco_entrega", length = 500)
    private String enderecoEntrega;

    @Column(name = "notas", length = 500)
    private String notas;

    public StoreOrderMetadata() {}

    public Pedido getPedido() { return pedido; }
    public void setPedido(Pedido pedido) { this.pedido = pedido; }

    public String getSocioId() { return socioId; }
    public void setSocioId(String socioId) { this.socioId = socioId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getMetodoPagamento() { return metodoPagamento; }
    public void setMetodoPagamento(String metodoPagamento) { this.metodoPagamento = metodoPagamento; }

    public String getPaymentUrl() { return paymentUrl; }
    public void setPaymentUrl(String paymentUrl) { this.paymentUrl = paymentUrl; }

    public String getEntidade() { return entidade; }
    public void setEntidade(String entidade) { this.entidade = entidade; }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public String getEnderecoEntrega() { return enderecoEntrega; }
    public void setEnderecoEntrega(String enderecoEntrega) { this.enderecoEntrega = enderecoEntrega; }

    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
}
