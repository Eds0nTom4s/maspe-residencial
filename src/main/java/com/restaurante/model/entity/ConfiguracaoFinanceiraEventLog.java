package com.restaurante.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Auditoria de alterações na configuração financeira do sistema.
 *
 * <p>Toda alteração crítica de política financeira (ativar/desativar pós-pago,
 * mudar limite, mudar valor mínimo) gera um registro imutável aqui.
 * Este log NÃO pode ser apagado por regra de negócio.
 *
 * <p>Campos mínimos obrigatórios (conforme modelo de domínio):
 * <ul>
 *   <li>tipoEvento  – ação executada (ex: ATIVOU_POS_PAGO, ALTEROU_LIMITE)</li>
 *   <li>entidadeId  – ID da configuração alterada</li>
 *   <li>userId / role – quem executou</li>
 *   <li>timestamp   – quando executou</li>
 *   <li>motivo      – contexto da alteração (opcional, mas recomendado)</li>
 * </ul>
 */
@Entity
@Table(name = "configuracao_financeira_event_log", indexes = {
    @Index(name = "idx_cfg_audit_tipo",      columnList = "tipo_evento"),
    @Index(name = "idx_cfg_audit_usuario",   columnList = "usuario_nome"),
    @Index(name = "idx_cfg_audit_timestamp", columnList = "timestamp")
})
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracaoFinanceiraEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tipo do evento auditado.
     * Valores possíveis (mantidos como String para extensibilidade):
     *   ATIVOU_POS_PAGO, DESATIVOU_POS_PAGO,
     *   ALTEROU_LIMITE_POS_PAGO, ALTEROU_VALOR_MINIMO,
     *   CONFIRMOU_PAGAMENTO_POS_PAGO, ESTORNOU_PEDIDO
     */
    @Column(name = "tipo_evento", nullable = false, length = 60)
    private String tipoEvento;

    /**
     * ID da configuração financeira (ConfiguracaoFinanceiraSistema) que foi alterada.
     * Para eventos de pedido (CONFIRMOU_PAGAMENTO, ESTORNOU), guarda o ID do Pedido.
     */
    @Column(name = "entidade_id")
    private Long entidadeId;

    /**
     * Descrição da entidade afetada (ex: "ConfiguracaoFinanceiraSistema", "Pedido").
     */
    @Column(name = "entidade_tipo", length = 60)
    private String entidadeTipo;

    /** Nome/login do usuário que executou a ação. */
    @Column(name = "usuario_nome", nullable = false, length = 100)
    private String usuarioNome;

    /** Role do usuário no momento da ação. */
    @Column(name = "usuario_role", nullable = false, length = 50)
    private String usuarioRole;

    /** Instante da alteração. */
    @Column(name = "timestamp", nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /** Valor anterior (para campos numéricos alterados; nulo caso não se aplique). */
    @Column(name = "valor_anterior", precision = 14, scale = 2)
    private BigDecimal valorAnterior;

    /** Valor novo (para campos numéricos alterados; nulo caso não se aplique). */
    @Column(name = "valor_novo", precision = 14, scale = 2)
    private BigDecimal valorNovo;

    /** Estado booleano anterior (para flags tipo posPagoAtivo). */
    @Column(name = "flag_anterior")
    private Boolean flagAnterior;

    /** Estado booleano novo. */
    @Column(name = "flag_novo")
    private Boolean flagNovo;

    /**
     * Motivo declarado pelo operador para a alteração.
     * Opcional, mas recomendado para compliance.
     */
    @Column(name = "motivo", length = 500)
    private String motivo;

    /**
     * Informação adicional livre (número de pedido, referência externa, etc.).
     */
    @Column(name = "detalhe", length = 500)
    private String detalhe;
}
