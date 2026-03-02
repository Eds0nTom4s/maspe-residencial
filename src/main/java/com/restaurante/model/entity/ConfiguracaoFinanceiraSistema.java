package com.restaurante.model.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Configuração global do sistema financeiro
 * 
 * CONTROLE DE POS-PAGO:
 * - posPagoAtivo controla se sistema aceita pedidos pós-pago
 * - ADMIN pode ativar/desativar em tempo real
 * - Toda alteração gera EventLog para auditoria
 */
@Entity
@Table(name = "configuracao_financeira_sistema")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracaoFinanceiraSistema extends BaseEntity {

    /**
     * Pós-pago está ativo no sistema?
     * false = POS-PAGO bloqueado globalmente
     */
    @Column(name = "pos_pago_ativo", nullable = false)
    @Builder.Default
    private Boolean posPagoAtivo = true;

    /**
     * Limite máximo de pós-pago aberto por unidade de consumo
     * Valor em AOA (Kwanzas Angolanos)
     * Configurável via admin - NÃO hardcoded
     */
    @Column(name = "limite_pos_pago", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private java.math.BigDecimal limitePosPago = new java.math.BigDecimal("500.00");

    /**
     * Valor mínimo para operações financeiras
     * Aplica-se a: recarga de fundo, débito, estorno
     * Carregado de application.properties na inicialização
     * Após primeira execução, BANCO é fonte de verdade
     */
    @Column(name = "valor_minimo_operacao", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private java.math.BigDecimal valorMinimoOperacao = new java.math.BigDecimal("10.00");

    /**
     * Usuário que fez a última alteração
     */
    @Column(name = "atualizado_por_nome", length = 100)
    private String atualizadoPorNome;

    /**
     * Role do usuário que alterou
     */
    @Column(name = "atualizado_por_role", length = 50)
    private String atualizadoPorRole;

    /**
     * Motivo declarado para a última alteração de configuração.
     * Opcional, mas fortemente recomendado para compliance e auditoria.
     * Armazenado apenas para contexto humano; o registro formal fica em
     * ConfiguracaoFinanceiraEventLog.
     */
    @Column(name = "motivo_ultima_alteracao", length = 500)
    private String motivoUltimaAlteracao;
}
