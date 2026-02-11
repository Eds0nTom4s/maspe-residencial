package com.restaurante.model.entity;

import com.restaurante.model.enums.StatusSubPedido;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

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
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
    @Builder.Default
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
    @Builder.Default
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
}
