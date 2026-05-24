package com.restaurante.model.entity;

import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import com.restaurante.financeiro.enums.PagamentoPollingStatus;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.TipoPagamentoFinanceiro;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

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
    @Index(name = "idx_pagamento_tenant", columnList = "tenant_id"),
    @Index(name = "idx_pagamento_pedido", columnList = "pedido_id"),
    @Index(name = "idx_pagamento_fundo", columnList = "fundo_consumo_id"),
    @Index(name = "idx_pagamento_status", columnList = "status"),
    @Index(name = "idx_pagamento_external_ref", columnList = "external_reference"),
    @Index(name = "idx_pagamento_gateway_charge", columnList = "gateway_charge_id")
})
public class Pagamento extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

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
     * OrdemPagamento relacionada (opcional)
     * Usado principalmente para pagamentos manuais (CASH/TPA) criados a partir de uma OrdemPagamento.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ordem_pagamento_id")
    private OrdemPagamento ordemPagamento;

    /**
     * Cliente relacionado (opcional)
     * Usado quando o cliente solicita recarga sem ter uma sessão ativa
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

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

    // ── Polling (rede de segurança do callback) ───────────────────────────────
    @Column(name = "polling_enabled", nullable = false)
    private boolean pollingEnabled = true;

    @Column(name = "polling_attempts", nullable = false)
    private int pollingAttempts = 0;

    @Column(name = "last_polling_attempt_at")
    private LocalDateTime lastPollingAttemptAt;

    @Column(name = "next_polling_attempt_at")
    private LocalDateTime nextPollingAttemptAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "polling_status", length = 50)
    private PagamentoPollingStatus pollingStatus;

    @Column(name = "polling_last_error_code", length = 100)
    private String pollingLastErrorCode;

    @Column(name = "polling_last_error_message", columnDefinition = "TEXT")
    private String pollingLastErrorMessage;

    @Column(name = "gateway_status_last_checked_at")
    private LocalDateTime gatewayStatusLastCheckedAt;

    @Column(name = "gateway_status_raw", columnDefinition = "TEXT")
    private String gatewayStatusRaw;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // ── Constructors ──────────────────────────────────────────────────────────
    public Pagamento() {}

    public Pagamento(Pedido pedido, FundoConsumo fundoConsumo, Cliente cliente,
                     TipoPagamentoFinanceiro tipoPagamento, MetodoPagamentoAppyPay metodo,
                     BigDecimal amount, StatusPagamentoGateway status,
                     String externalReference, String gatewayChargeId,
                     String entidade, String referencia, LocalDateTime confirmedAt,
                     String gatewayResponse, String observacoes) {
        this.pedido = pedido;
        this.fundoConsumo = fundoConsumo;
        this.cliente = cliente;
        this.tipoPagamento = tipoPagamento;
        this.metodo = metodo;
        this.amount = amount;
        this.status = status != null ? status : StatusPagamentoGateway.PENDENTE;
        this.externalReference = externalReference;
        this.gatewayChargeId = gatewayChargeId;
        this.entidade = entidade;
        this.referencia = referencia;
        this.confirmedAt = confirmedAt;
        this.gatewayResponse = gatewayResponse;
        this.observacoes = observacoes;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public Pedido getPedido() { return pedido; }
    public FundoConsumo getFundoConsumo() { return fundoConsumo; }
    public OrdemPagamento getOrdemPagamento() { return ordemPagamento; }
    public Cliente getCliente() { return cliente; }
    public Tenant getTenant() { return tenant; }
    public TipoPagamentoFinanceiro getTipoPagamento() { return tipoPagamento; }
    public MetodoPagamentoAppyPay getMetodo() { return metodo; }
    public BigDecimal getAmount() { return amount; }
    public StatusPagamentoGateway getStatus() { return status; }
    public String getExternalReference() { return externalReference; }
    public String getGatewayChargeId() { return gatewayChargeId; }
    public String getEntidade() { return entidade; }
    public String getReferencia() { return referencia; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public String getGatewayResponse() { return gatewayResponse; }
    public String getObservacoes() { return observacoes; }

    // ── Setters ───────────────────────────────────────────────────────────────
    public void setPedido(Pedido pedido) { this.pedido = pedido; }
    public void setFundoConsumo(FundoConsumo fundoConsumo) { this.fundoConsumo = fundoConsumo; }
    public void setOrdemPagamento(OrdemPagamento ordemPagamento) { this.ordemPagamento = ordemPagamento; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }
    public void setTipoPagamento(TipoPagamentoFinanceiro tipoPagamento) { this.tipoPagamento = tipoPagamento; }
    public void setMetodo(MetodoPagamentoAppyPay metodo) { this.metodo = metodo; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setStatus(StatusPagamentoGateway status) { this.status = status; }
    public void setExternalReference(String externalReference) { this.externalReference = externalReference; }
    public void setGatewayChargeId(String gatewayChargeId) { this.gatewayChargeId = gatewayChargeId; }
    public void setEntidade(String entidade) { this.entidade = entidade; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public void setGatewayResponse(String gatewayResponse) { this.gatewayResponse = gatewayResponse; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    public boolean isPollingEnabled() { return pollingEnabled; }
    public void setPollingEnabled(boolean pollingEnabled) { this.pollingEnabled = pollingEnabled; }
    public int getPollingAttempts() { return pollingAttempts; }
    public void setPollingAttempts(int pollingAttempts) { this.pollingAttempts = pollingAttempts; }
    public LocalDateTime getLastPollingAttemptAt() { return lastPollingAttemptAt; }
    public void setLastPollingAttemptAt(LocalDateTime lastPollingAttemptAt) { this.lastPollingAttemptAt = lastPollingAttemptAt; }
    public LocalDateTime getNextPollingAttemptAt() { return nextPollingAttemptAt; }
    public void setNextPollingAttemptAt(LocalDateTime nextPollingAttemptAt) { this.nextPollingAttemptAt = nextPollingAttemptAt; }
    public PagamentoPollingStatus getPollingStatus() { return pollingStatus; }
    public void setPollingStatus(PagamentoPollingStatus pollingStatus) { this.pollingStatus = pollingStatus; }
    public String getPollingLastErrorCode() { return pollingLastErrorCode; }
    public void setPollingLastErrorCode(String pollingLastErrorCode) { this.pollingLastErrorCode = pollingLastErrorCode; }
    public String getPollingLastErrorMessage() { return pollingLastErrorMessage; }
    public void setPollingLastErrorMessage(String pollingLastErrorMessage) { this.pollingLastErrorMessage = pollingLastErrorMessage; }
    public LocalDateTime getGatewayStatusLastCheckedAt() { return gatewayStatusLastCheckedAt; }
    public void setGatewayStatusLastCheckedAt(LocalDateTime gatewayStatusLastCheckedAt) { this.gatewayStatusLastCheckedAt = gatewayStatusLastCheckedAt; }
    public String getGatewayStatusRaw() { return gatewayStatusRaw; }
    public void setGatewayStatusRaw(String gatewayStatusRaw) { this.gatewayStatusRaw = gatewayStatusRaw; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    // ── equals / hashCode ─────────────────────────────────────────────────────
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Pagamento pagamento = (Pagamento) o;
        return Objects.equals(externalReference, pagamento.externalReference) &&
               Objects.equals(gatewayChargeId, pagamento.gatewayChargeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), externalReference, gatewayChargeId);
    }

    // ── Builder ───────────────────────────────────────────────────────────────
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Tenant tenant;
        private Pedido pedido;
        private FundoConsumo fundoConsumo;
        private OrdemPagamento ordemPagamento;
        private Cliente cliente;
        private TipoPagamentoFinanceiro tipoPagamento;
        private MetodoPagamentoAppyPay metodo;
        private BigDecimal amount;
        private StatusPagamentoGateway status = StatusPagamentoGateway.PENDENTE;
        private String externalReference;
        private String gatewayChargeId;
        private String entidade;
        private String referencia;
        private LocalDateTime confirmedAt;
        private String gatewayResponse;
        private String observacoes;

        public Builder tenant(Tenant tenant) { this.tenant = tenant; return this; }
        public Builder pedido(Pedido pedido) { this.pedido = pedido; return this; }
        public Builder fundoConsumo(FundoConsumo fundoConsumo) { this.fundoConsumo = fundoConsumo; return this; }
        public Builder ordemPagamento(OrdemPagamento ordemPagamento) { this.ordemPagamento = ordemPagamento; return this; }
        public Builder cliente(Cliente cliente) { this.cliente = cliente; return this; }
        public Builder tipoPagamento(TipoPagamentoFinanceiro tipoPagamento) { this.tipoPagamento = tipoPagamento; return this; }
        public Builder metodo(MetodoPagamentoAppyPay metodo) { this.metodo = metodo; return this; }
        public Builder amount(BigDecimal amount) { this.amount = amount; return this; }
        public Builder status(StatusPagamentoGateway status) { this.status = status; return this; }
        public Builder externalReference(String externalReference) { this.externalReference = externalReference; return this; }
        public Builder gatewayChargeId(String gatewayChargeId) { this.gatewayChargeId = gatewayChargeId; return this; }
        public Builder entidade(String entidade) { this.entidade = entidade; return this; }
        public Builder referencia(String referencia) { this.referencia = referencia; return this; }
        public Builder confirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; return this; }
        public Builder gatewayResponse(String gatewayResponse) { this.gatewayResponse = gatewayResponse; return this; }
        public Builder observacoes(String observacoes) { this.observacoes = observacoes; return this; }

        public Pagamento build() {
            Pagamento p = new Pagamento(pedido, fundoConsumo, cliente, tipoPagamento, metodo, amount, status,
                    externalReference, gatewayChargeId, entidade, referencia,
                    confirmedAt, gatewayResponse, observacoes);
            p.setTenant(tenant);
            p.setOrdemPagamento(ordemPagamento);
            return p;
        }
    }

    // ── Comportamentos de domínio ─────────────────────────────────────────────

    /**
     * Confirma o pagamento (chamado pelo callback ou GPO imediato)
     * IDEMPOTENTE: se já confirmado, não faz nada
     */
    public void confirmar() {
        if (this.status == StatusPagamentoGateway.CONFIRMADO) {
            return; // Idempotência: já confirmado
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
