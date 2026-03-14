package com.restaurante.model.entity;

import com.restaurante.model.enums.StatusSubPedido;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity SubPedido
 * 
 * NÚCLEO OPERACIONAL DO SISTEMA
 * Unidade operacional de execução do pedido
 * 
 * Um Pedido é dividido em SubPedidos por Cozinha/Unidade
 * Exemplo: Pedido com Pizza + Cerveja + Sobremesa
 *  → SubPedido 1 (Cozinha Central): Pizza
 *  → SubPedido 2 (Bar): Cerveja
 *  → SubPedido 3 (Confeitaria): Sobremesa
 * 
 * Cada SubPedido tem status INDEPENDENTE
 * Permite trabalho paralelo e entrega parcial
 */
@Entity
@Table(name = "sub_pedidos", indexes = {
    @Index(name = "idx_subpedido_pedido", columnList = "pedido_id"),
    @Index(name = "idx_subpedido_cozinha", columnList = "cozinha_id"),
    @Index(name = "idx_subpedido_status", columnList = "status"),
    @Index(name = "idx_subpedido_unidade", columnList = "unidade_atendimento_id")
})
public class SubPedido extends BaseEntity {

    /**
     * Número sequencial do SubPedido
     * Formato: PED-001-1, PED-001-2 (pedido-subpedido)
     */
    @Column(nullable = false, unique = true, length = 50)
    private String numero;

    /**
     * Pedido pai (agregador lógico)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    @NotNull
    private Pedido pedido;

    /**
     * Unidade de Atendimento de origem
     * Herdado do Pedido/Mesa
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_atendimento_id", nullable = false)
    @NotNull
    private UnidadeAtendimento unidadeAtendimento;

    /**
     * Cozinha responsável pelo preparo
     * Determinado automaticamente pela categoria dos produtos
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cozinha_id", nullable = false)
    @NotNull
    private Cozinha cozinha;

    /**
     * Status do SubPedido (independente)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusSubPedido status = StatusSubPedido.PENDENTE;

    /**
     * Observações específicas deste SubPedido
     */
    @Column(length = 500)
    private String observacoes;

    /**
     * Relacionamento com itens do SubPedido
     */
    @OneToMany(mappedBy = "subPedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ItemPedido> itens = new ArrayList<>();

    /**
     * Total do SubPedido
     */
    @Column(name = "total", precision = 10, scale = 2)
    private BigDecimal total;

    /**
     * Timestamps de controle operacional
     */
    @Column(name = "recebido_em")
    private LocalDateTime recebidoEm;

    @Column(name = "iniciado_em")
    private LocalDateTime iniciadoEm;

    @Column(name = "pronto_em")
    private LocalDateTime prontoEm;

    @Column(name = "entregue_em")
    private LocalDateTime entregueEm;

    /**
     * Responsável pelo preparo (usuário da cozinha)
     */
    @Column(name = "responsavel_preparo", length = 100)
    private String responsavelPreparo;

    /**
     * Garçom que confirmou a entrega
     */
    @Column(name = "responsavel_entrega", length = 100)
    private String responsavelEntrega;

    /**
     * Calcula o total do SubPedido
     */
    public BigDecimal calcularTotal() {
        BigDecimal totalCalculado = itens.stream()
            .map(ItemPedido::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        this.total = totalCalculado;
        return totalCalculado;
    }

    /**
     * Adiciona item ao SubPedido
     */
    public void adicionarItem(ItemPedido item) {
        itens.add(item);
        item.setSubPedido(this);
        calcularTotal();
    }

    /**
     * Valida se pode transicionar para novo status
     * DELEGADO PARA ENUM (StatusSubPedido)
     */
    public boolean podeTransicionarPara(StatusSubPedido novoStatus) {
        return status.podeTransicionarPara(novoStatus);
    }

    /**
     * Verifica se o SubPedido está finalizado
     */
    public boolean isFinalizado() {
        return status == StatusSubPedido.ENTREGUE || status == StatusSubPedido.CANCELADO;
    }

    /**
     * Verifica se SubPedido está em estado terminal (não pode mais mudar)
     */
    public boolean isTerminal() {
        return status.isTerminal();
    }

    /**
     * Calcula tempo total desde criação até conclusão (em minutos)
     */
    public Long calcularTempoTotal() {
        if (entregueEm != null && getCreatedAt() != null) {
            return java.time.Duration.between(getCreatedAt(), entregueEm).toMinutes();
        }
        return null;
    }

    /**
     * Calcula tempo de preparação (em minutos)
     */
    public Long calcularTempoPreparacao() {
        if (iniciadoEm != null && prontoEm != null) {
            return java.time.Duration.between(iniciadoEm, prontoEm).toMinutes();
        }
        return null;
    }

    // ========== Construtores ==========

    public SubPedido() {}

    public SubPedido(String numero, Pedido pedido, UnidadeAtendimento unidadeAtendimento,
                     Cozinha cozinha, StatusSubPedido status, String observacoes,
                     List<ItemPedido> itens, BigDecimal total,
                     LocalDateTime recebidoEm, LocalDateTime iniciadoEm,
                     LocalDateTime prontoEm, LocalDateTime entregueEm,
                     String responsavelPreparo, String responsavelEntrega) {
        this.numero = numero;
        this.pedido = pedido;
        this.unidadeAtendimento = unidadeAtendimento;
        this.cozinha = cozinha;
        this.status = status != null ? status : StatusSubPedido.PENDENTE;
        this.observacoes = observacoes;
        this.itens = itens != null ? itens : new ArrayList<>();
        this.total = total;
        this.recebidoEm = recebidoEm;
        this.iniciadoEm = iniciadoEm;
        this.prontoEm = prontoEm;
        this.entregueEm = entregueEm;
        this.responsavelPreparo = responsavelPreparo;
        this.responsavelEntrega = responsavelEntrega;
    }

    // ========== Getters / Setters ==========

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public Pedido getPedido() { return pedido; }
    public void setPedido(Pedido pedido) { this.pedido = pedido; }

    public UnidadeAtendimento getUnidadeAtendimento() { return unidadeAtendimento; }
    public void setUnidadeAtendimento(UnidadeAtendimento u) { this.unidadeAtendimento = u; }

    public Cozinha getCozinha() { return cozinha; }
    public void setCozinha(Cozinha cozinha) { this.cozinha = cozinha; }

    public StatusSubPedido getStatus() { return status; }
    public void setStatus(StatusSubPedido status) { this.status = status; }

    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }

    public List<ItemPedido> getItens() { return itens; }
    public void setItens(List<ItemPedido> itens) { this.itens = itens; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public LocalDateTime getRecebidoEm() { return recebidoEm; }
    public void setRecebidoEm(LocalDateTime recebidoEm) { this.recebidoEm = recebidoEm; }

    public LocalDateTime getIniciadoEm() { return iniciadoEm; }
    public void setIniciadoEm(LocalDateTime iniciadoEm) { this.iniciadoEm = iniciadoEm; }

    public LocalDateTime getProntoEm() { return prontoEm; }
    public void setProntoEm(LocalDateTime prontoEm) { this.prontoEm = prontoEm; }

    public LocalDateTime getEntregueEm() { return entregueEm; }
    public void setEntregueEm(LocalDateTime entregueEm) { this.entregueEm = entregueEm; }

    public String getResponsavelPreparo() { return responsavelPreparo; }
    public void setResponsavelPreparo(String responsavelPreparo) { this.responsavelPreparo = responsavelPreparo; }

    public String getResponsavelEntrega() { return responsavelEntrega; }
    public void setResponsavelEntrega(String responsavelEntrega) { this.responsavelEntrega = responsavelEntrega; }

    // ========== equals / hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SubPedido)) return false;
        SubPedido other = (SubPedido) o;
        return Objects.equals(getId(), other.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    // ========== Builder estático ==========

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String numero;
        private Pedido pedido;
        private UnidadeAtendimento unidadeAtendimento;
        private Cozinha cozinha;
        private StatusSubPedido status = StatusSubPedido.PENDENTE;
        private String observacoes;
        private List<ItemPedido> itens = new ArrayList<>();
        private BigDecimal total;
        private LocalDateTime recebidoEm;
        private LocalDateTime iniciadoEm;
        private LocalDateTime prontoEm;
        private LocalDateTime entregueEm;
        private String responsavelPreparo;
        private String responsavelEntrega;

        public Builder numero(String numero) { this.numero = numero; return this; }
        public Builder pedido(Pedido pedido) { this.pedido = pedido; return this; }
        public Builder unidadeAtendimento(UnidadeAtendimento u) { this.unidadeAtendimento = u; return this; }
        public Builder cozinha(Cozinha cozinha) { this.cozinha = cozinha; return this; }
        public Builder status(StatusSubPedido status) { this.status = status; return this; }
        public Builder observacoes(String observacoes) { this.observacoes = observacoes; return this; }
        public Builder itens(List<ItemPedido> itens) { this.itens = itens; return this; }
        public Builder total(BigDecimal total) { this.total = total; return this; }
        public Builder recebidoEm(LocalDateTime v) { this.recebidoEm = v; return this; }
        public Builder iniciadoEm(LocalDateTime v) { this.iniciadoEm = v; return this; }
        public Builder prontoEm(LocalDateTime v) { this.prontoEm = v; return this; }
        public Builder entregueEm(LocalDateTime v) { this.entregueEm = v; return this; }
        public Builder responsavelPreparo(String v) { this.responsavelPreparo = v; return this; }
        public Builder responsavelEntrega(String v) { this.responsavelEntrega = v; return this; }

        public SubPedido build() {
            return new SubPedido(numero, pedido, unidadeAtendimento, cozinha, status,
                    observacoes, itens, total, recebidoEm, iniciadoEm,
                    prontoEm, entregueEm, responsavelPreparo, responsavelEntrega);
        }
    }
}
