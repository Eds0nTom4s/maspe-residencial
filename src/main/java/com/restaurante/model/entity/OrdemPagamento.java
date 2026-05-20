package com.restaurante.model.entity;

import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.OrdemPagamentoTipo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ordens_pagamento", indexes = {
        @Index(name = "idx_ordem_pg_tenant", columnList = "tenant_id"),
        @Index(name = "idx_ordem_pg_status", columnList = "tenant_id, status"),
        @Index(name = "idx_ordem_pg_tipo", columnList = "tenant_id, tipo"),
        @Index(name = "idx_ordem_pg_turno", columnList = "tenant_id, turno_operacional_id"),
        @Index(name = "idx_ordem_pg_token", columnList = "token_qr", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class OrdemPagamento extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instituicao_id", nullable = false)
    private Instituicao instituicao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidade_atendimento_id", nullable = false)
    private UnidadeAtendimento unidadeAtendimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_operacional_id")
    private TurnoOperacional turnoOperacional;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private OrdemPagamentoTipo tipo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrdemPagamentoStatus status = OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_solicitado", nullable = false, length = 20)
    private MetodoPagamentoManual metodoSolicitado;

    @NotNull
    @Column(name = "valor", nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @NotNull
    @Column(name = "moeda", nullable = false, length = 3)
    private String moeda = "AOA";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_consumo_id")
    private SessaoConsumo sessaoConsumo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fundo_consumo_id")
    private FundoConsumo fundoConsumo;

    @Column(name = "token_qr", nullable = false, length = 80, unique = true)
    private String tokenQr;

    @Column(name = "codigo_curto", length = 20)
    private String codigoCurto;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "criado_por_origem", nullable = false, length = 40)
    private OperationalOrigem criadoPorOrigem = OperationalOrigem.QR_PUBLICO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmado_por_device_id")
    private DispositivoOperacional confirmadoPorDispositivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmado_por_user_id")
    private User confirmadoPorUser;

    @Column(name = "confirmado_em")
    private LocalDateTime confirmadoEm;

    @Column(name = "referencia_operador", length = 200)
    private String referenciaOperador;

    @Column(name = "observacao", length = 500)
    private String observacao;

    public boolean isExpirada(LocalDateTime now) {
        if (expiresAt == null) return false;
        return now != null ? expiresAt.isBefore(now) : expiresAt.isBefore(LocalDateTime.now());
    }
}

