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
     * Usuário que fez a última alteração
     */
    @Column(name = "atualizado_por_nome", length = 100)
    private String atualizadoPorNome;

    /**
     * Role do usuário que alterou
     */
    @Column(name = "atualizado_por_role", length = 50)
    private String atualizadoPorRole;
}
