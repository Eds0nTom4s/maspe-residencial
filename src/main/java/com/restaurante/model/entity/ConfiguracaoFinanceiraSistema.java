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
public class ConfiguracaoFinanceiraSistema extends BaseEntity {

    /**
     * Pós-pago está ativo no sistema?
     * false = POS-PAGO bloqueado globalmente
     */
    @Column(name = "pos_pago_ativo", nullable = false)
    private Boolean posPagoAtivo = true;

    /**
     * Limite máximo de pós-pago aberto por unidade de consumo
     * Valor em AOA (Kwanzas Angolanos)
     * Configurável via admin - NÃO hardcoded
     */
    @Column(name = "limite_pos_pago", nullable = false, precision = 10, scale = 2)
    private java.math.BigDecimal limitePosPago = new java.math.BigDecimal("500.00");

    /**
     * Valor mínimo para operações financeiras
     * Aplica-se a: recarga de fundo, débito, estorno
     * Carregado de application.properties na inicialização
     * Após primeira execução, BANCO é fonte de verdade
     */
    @Column(name = "valor_minimo_operacao", nullable = false, precision = 10, scale = 2)
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

    // Manual Getters and Setters since Lombok @Data / @Getter / @Setter is failing in this specific environment

    public Boolean getPosPagoAtivo() {
        return posPagoAtivo;
    }

    public void setPosPagoAtivo(Boolean posPagoAtivo) {
        this.posPagoAtivo = posPagoAtivo;
    }

    public java.math.BigDecimal getLimitePosPago() {
        return limitePosPago;
    }

    public void setLimitePosPago(java.math.BigDecimal limitePosPago) {
        this.limitePosPago = limitePosPago;
    }

    public java.math.BigDecimal getValorMinimoOperacao() {
        return valorMinimoOperacao;
    }

    public void setValorMinimoOperacao(java.math.BigDecimal valorMinimoOperacao) {
        this.valorMinimoOperacao = valorMinimoOperacao;
    }

    public String getAtualizadoPorNome() {
        return atualizadoPorNome;
    }

    public void setAtualizadoPorNome(String atualizadoPorNome) {
        this.atualizadoPorNome = atualizadoPorNome;
    }

    public String getAtualizadoPorRole() {
        return atualizadoPorRole;
    }

    public void setAtualizadoPorRole(String atualizadoPorRole) {
        this.atualizadoPorRole = atualizadoPorRole;
    }

    public String getMotivoUltimaAlteracao() {
        return motivoUltimaAlteracao;
    }

    public void setMotivoUltimaAlteracao(String motivoUltimaAlteracao) {
        this.motivoUltimaAlteracao = motivoUltimaAlteracao;
    }

    // --- Construtores ---
    public ConfiguracaoFinanceiraSistema() {}

    public ConfiguracaoFinanceiraSistema(Boolean posPagoAtivo, java.math.BigDecimal limitePosPago,
                                         java.math.BigDecimal valorMinimoOperacao, String atualizadoPorNome,
                                         String atualizadoPorRole, String motivoUltimaAlteracao) {
        this.posPagoAtivo = posPagoAtivo != null ? posPagoAtivo : true;
        this.limitePosPago = limitePosPago != null ? limitePosPago : new java.math.BigDecimal("500.00");
        this.valorMinimoOperacao = valorMinimoOperacao != null ? valorMinimoOperacao : new java.math.BigDecimal("10.00");
        this.atualizadoPorNome = atualizadoPorNome;
        this.atualizadoPorRole = atualizadoPorRole;
        this.motivoUltimaAlteracao = motivoUltimaAlteracao;
    }

    // --- Equals & HashCode ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ConfiguracaoFinanceiraSistema that = (ConfiguracaoFinanceiraSistema) o;
        return java.util.Objects.equals(posPagoAtivo, that.posPagoAtivo) &&
               java.util.Objects.equals(limitePosPago, that.limitePosPago) &&
               java.util.Objects.equals(valorMinimoOperacao, that.valorMinimoOperacao) &&
               java.util.Objects.equals(atualizadoPorNome, that.atualizadoPorNome) &&
               java.util.Objects.equals(atualizadoPorRole, that.atualizadoPorRole) &&
               java.util.Objects.equals(motivoUltimaAlteracao, that.motivoUltimaAlteracao);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), posPagoAtivo, limitePosPago, valorMinimoOperacao, atualizadoPorNome, atualizadoPorRole, motivoUltimaAlteracao);
    }

    // --- Builder ---
    public static ConfiguracaoFinanceiraSistemaBuilder builder() {
        return new ConfiguracaoFinanceiraSistemaBuilder();
    }

    public static class ConfiguracaoFinanceiraSistemaBuilder {
        private Boolean posPagoAtivo;
        private java.math.BigDecimal limitePosPago;
        private java.math.BigDecimal valorMinimoOperacao;
        private String atualizadoPorNome;
        private String atualizadoPorRole;
        private String motivoUltimaAlteracao;

        ConfiguracaoFinanceiraSistemaBuilder() {}

        public ConfiguracaoFinanceiraSistemaBuilder posPagoAtivo(Boolean posPagoAtivo) {
            this.posPagoAtivo = posPagoAtivo;
            return this;
        }

        public ConfiguracaoFinanceiraSistemaBuilder limitePosPago(java.math.BigDecimal limitePosPago) {
            this.limitePosPago = limitePosPago;
            return this;
        }

        public ConfiguracaoFinanceiraSistemaBuilder valorMinimoOperacao(java.math.BigDecimal valorMinimoOperacao) {
            this.valorMinimoOperacao = valorMinimoOperacao;
            return this;
        }

        public ConfiguracaoFinanceiraSistemaBuilder atualizadoPorNome(String atualizadoPorNome) {
            this.atualizadoPorNome = atualizadoPorNome;
            return this;
        }

        public ConfiguracaoFinanceiraSistemaBuilder atualizadoPorRole(String atualizadoPorRole) {
            this.atualizadoPorRole = atualizadoPorRole;
            return this;
        }

        public ConfiguracaoFinanceiraSistemaBuilder motivoUltimaAlteracao(String motivoUltimaAlteracao) {
            this.motivoUltimaAlteracao = motivoUltimaAlteracao;
            return this;
        }

        public ConfiguracaoFinanceiraSistema build() {
            return new ConfiguracaoFinanceiraSistema(posPagoAtivo, limitePosPago, valorMinimoOperacao,
                                                     atualizadoPorNome, atualizadoPorRole, motivoUltimaAlteracao);
        }
    }
}
