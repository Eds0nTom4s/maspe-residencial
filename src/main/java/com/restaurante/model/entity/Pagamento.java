package com.restaurante.model.entity;

import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.TipoPagamentoFinanceiro;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidade Pagamento - DOMÍNIO FINANCEIRO
 * 
 * RESPONSABILIDADE:
 * - Rastrear transações financeiras (pré-pago e pós-pago)
 * - Integrar com gateway AppyPay (GPO/REF)
 * - Vincular pagamento a Pedido OU Fundo de Consumo
 * - NÃO controla fluxo operacional (SubPedido)
 * - NÃO altera status operacional do pedido
 * 
 * SEPARAÇÃO DE CONCEITOS:
 * - Pagamento é do eixo FINANCEIRO
 * - Status operacional (SubPedido, Pedido) é independente
 * - StatusFinanceiroPedido é atualizado após confirmação
 * 
 * BASEADO EM ARENATICKET (VALIDADO EM PRODUÇÃO)
 */
@Entity
@Table(name = "pagamentos_gateway", indexes = {
    @Index(name = "idx_pagamento_pedido", columnList = "pedido_id"),
    @Index(name = "idx_pagamento_fundo", columnList = "fundo_consumo_id"),
    @Index(name = "idx_pagamento_status", columnList = "status"),
    @Index(name = "idx_pagamento_external_ref", columnList = "external_reference"),
    @Index(name = "idx_pagamento_gateway_charge", columnList = "gateway_charge_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pagamento extends BaseEntity {

    /**
     * Pedido relacionado (nullable)
     * Usado em pagamentos pós-pago de pedidos
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;
    
    /**
     * Fundo de Consumo relacionado (nullable)
     * Usado em recargas de fundo (pré-pago)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fundo_consumo_id")
    private FundoConsumo fundoConsumo;
    
    /**
     * Tipo de pagamento
     * PRE_PAGO: Recarga de fundo
     * POS_PAGO: Pagamento de pedido
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pagamento", nullable = false, length = 20)
    @NotNull
    private TipoPagamentoFinanceiro tipoPagamento;
    
    /**
     * Método de pagamento (gateway)
     * GPO: AppyPay instantâneo
     * REF: Referência bancária
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "metodo", length = 20)
    private MetodoPagamentoAppyPay metodo;

    @NotNull(message = "Valor é obrigatório")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /**
     * Status do pagamento no gateway
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusPagamentoGateway status = StatusPagamentoGateway.PENDENTE;

    /**
     * Referência externa CURTA (<= 15 chars)
     * merchantTransactionId no AppyPay
     * DEVE SER ÚNICO
     */
    @Column(name = "external_reference", length = 15, unique = true)
    private String externalReference;
    
    /**
     * ID da cobrança no gateway
     * chargeId da AppyPay
     */
    @Column(name = "gateway_charge_id", length = 100)
    private String gatewayChargeId;
    
    /**
     * Entidade bancária (apenas REF)
     * Exemplo: "10100" (BAI)
     */
    @Column(name = "entidade", length = 10)
    private String entidade;
    
    /**
     * Referência de pagamento (apenas REF)
     * Exemplo: "999 123 456"
     */
    @Column(name = "referencia", length = 20)
    private String referencia;
    
    /**
     * Data de confirmação do pagamento
     */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "gateway_response", columnDefinition = "TEXT")
    private String gatewayResponse; // Resposta completa do gateway (JSON)

    @Column(length = 500)
    private String observacoes;

    /**
     * Confirma o pagamento (chamado pelo callback ou GPO imediato)
     * IDEMPOTENTE: se já confirmado, não faz nada
     */
    public void confirmar() {
        if (this.status == StatusPagamentoGateway.CONFIRMADO) {
            // Idempotência: já confirmado
            return;
        }
        
        if (!this.status.podeEstornar() && this.status != StatusPagamentoGateway.PENDENTE) {
            throw new IllegalStateException("Pagamento não pode ser confirmado no status: " + this.status);
        }
        
        this.status = StatusPagamentoGateway.CONFIRMADO;
        this.confirmedAt = LocalDateTime.now();
    }

    /**
     * Marca pagamento como falho
     */
    public void marcarComoFalho(String motivo) {
        this.status = StatusPagamentoGateway.FALHOU;
        this.observacoes = motivo;
    }
    
    /**
     * Estorna pagamento
     * Apenas CONFIRMADO pode ser estornado
     */
    public void estornar(String motivo) {
        if (!this.status.podeEstornar()) {
            throw new IllegalStateException("Pagamento não pode ser estornado no status: " + this.status);
        }
        
        this.status = StatusPagamentoGateway.ESTORNADO;
        this.observacoes = motivo;
    }

    /**
     * Verifica se o pagamento foi confirmado
     */
    public boolean isConfirmado() {
        return status == StatusPagamentoGateway.CONFIRMADO;
    }
    
    /**
     * Verifica se é pré-pago (recarga de fundo)
     */
    public boolean isPrePago() {
        return tipoPagamento == TipoPagamentoFinanceiro.PRE_PAGO;
    }
    
    /**
     * Verifica se é pós-pago (pagamento de pedido)
     */
    public boolean isPosPago() {
        return tipoPagamento == TipoPagamentoFinanceiro.POS_PAGO;
    }
}
