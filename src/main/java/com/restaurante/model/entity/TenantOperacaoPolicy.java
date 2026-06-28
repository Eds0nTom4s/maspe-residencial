package com.restaurante.model.entity;

import com.restaurante.model.enums.ComportamentoPedidoNaoPago;
import com.restaurante.model.enums.LogisticsMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenant_operacao_policies", indexes = {
        @Index(name = "uq_tenant_operacao_policy_tenant", columnList = "tenant_id", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TenantOperacaoPolicy extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "require_open_turno_for_orders", nullable = false)
    private boolean requireOpenTurnoForOrders = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "logistics_mode", nullable = false, length = 40)
    private LogisticsMode logisticsMode = LogisticsMode.NONE;

    @Column(name = "allow_pickup", nullable = false)
    private boolean allowPickup = true;

    @Column(name = "allow_manual_payment", nullable = false)
    private boolean allowManualPayment = true;

    @Column(name = "allow_digital_payment", nullable = false)
    private boolean allowDigitalPayment = true;

    /**
     * SIMPLE / OPTIONAL / NONE (string para permitir evolução sem migração nesta fase).
     */
    @Column(name = "stock_mode", length = 20)
    private String stockMode;

    @Column(name = "production_enabled", nullable = false)
    private boolean productionEnabled = false;

    @Column(name = "pos_enabled", nullable = false)
    private boolean posEnabled = false;

    @Column(name = "kds_enabled", nullable = false)
    private boolean kdsEnabled = false;

    @Column(name = "allow_table_qr", nullable = false)
    private boolean allowTableQr = false;

    @Column(name = "snapshot_financeiro_enabled", nullable = false)
    private boolean snapshotFinanceiroEnabled = false;

    @Column(name = "pre_fecho_enabled", nullable = false)
    private boolean preFechoEnabled = false;

    @Column(name = "pagamento_obrigatorio_antes_do_pedido", nullable = false)
    private boolean pagamentoObrigatorioAntesDoPedido = true;

    @Column(name = "permitir_pedido_sem_pagamento", nullable = false)
    private boolean permitirPedidoSemPagamento = false;

    @Column(name = "permitir_pos_pago", nullable = false)
    private boolean permitirPosPago = false;

    @Column(name = "permitir_cash", nullable = false)
    private boolean permitirCash = false;

    @Column(name = "permitir_pagamento_na_entrega", nullable = false)
    private boolean permitirPagamentoNaEntrega = false;

    @Column(name = "tempo_expiracao_pedido_pendente_pagamento_minutos", nullable = false)
    private Integer tempoExpiracaoPedidoPendentePagamentoMinutos = 15;

    @Enumerated(EnumType.STRING)
    @Column(name = "comportamento_pedido_nao_pago", nullable = false, length = 80)
    private ComportamentoPedidoNaoPago comportamentoPedidoNaoPago = ComportamentoPedidoNaoPago.CRIAR_PENDENTE;
}
