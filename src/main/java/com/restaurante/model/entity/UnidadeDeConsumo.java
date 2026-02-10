package com.restaurante.model.entity;

import com.restaurante.model.enums.StatusUnidadeConsumo;
import com.restaurante.model.enums.TipoUnidadeConsumo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity UnidadeDeConsumo
 * 
 * Conceito genérico que representa um ponto de consumo no sistema
 * 
 * Pode ser:
 * - Mesa física de restaurante
 * - Quarto de hotel (room service)
 * - Área de evento
 * - Espaço lounge
 * - Virtual (delivery)
 * 
 * Uma unidade só existe enquanto estiver associada a um cliente
 * Criada quando cliente escaneia QR Code ou atendente cria manualmente
 * 
 * Vinculada obrigatoriamente a uma UnidadeDeAtendimento
 */
@Entity
@Table(name = "unidades_consumo", indexes = {
    @Index(name = "idx_unidade_consumo_referencia", columnList = "referencia"),
    @Index(name = "idx_unidade_consumo_status", columnList = "status"),
    @Index(name = "idx_unidade_consumo_qr_code", columnList = "qr_code", unique = true),
    @Index(name = "idx_unidade_consumo_cliente", columnList = "cliente_id"),
    @Index(name = "idx_unidade_consumo_tipo", columnList = "tipo")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnidadeDeConsumo extends BaseEntity {

    /**
     * Referência/Identificador da unidade
     * Exemplos: "Mesa 15", "Quarto 205", "Área VIP"
     */
    @NotBlank(message = "Referência da unidade é obrigatória")
    @Column(nullable = false, length = 100)
    private String referencia;

    /**
     * Tipo da unidade de consumo
     */
    @NotNull(message = "Tipo da unidade é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private TipoUnidadeConsumo tipo = TipoUnidadeConsumo.MESA_FISICA;

    /**
     * Número (quando aplicável - ex: mesa 15, quarto 205)
     */
    @Column(name = "numero")
    private Integer numero;

    /**
     * QR Code único da unidade
     */
    @Column(name = "qr_code", unique = true, length = 100)
    private String qrCode;

    /**
     * Status atual da unidade
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private StatusUnidadeConsumo status = StatusUnidadeConsumo.DISPONIVEL;

    /**
     * Capacidade (número de pessoas)
     */
    @Column(name = "capacidade")
    private Integer capacidade;

    /**
     * Relacionamento OBRIGATÓRIO com Cliente
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    /**
     * Relacionamento OPCIONAL com Atendente (se foi criada manualmente)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendente_id")
    private Atendente atendente;

    /**
     * Relacionamento OBRIGATÓRIO com UnidadeDeAtendimento
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unidade_atendimento_id", nullable = false)
    private UnidadeAtendimento unidadeAtendimento;

    /**
     * Timestamp de abertura
     */
    @Column(name = "aberta_em", nullable = false)
    @Builder.Default
    private LocalDateTime abertaEm = LocalDateTime.now();

    /**
     * Timestamp de fechamento
     */
    @Column(name = "fechada_em")
    private LocalDateTime fechadaEm;

    /**
     * Relacionamento com pedidos
     */
    @OneToMany(mappedBy = "unidadeConsumo", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Pedido> pedidos = new ArrayList<>();

    /**
     * Relacionamento com pagamento
     */
    @OneToOne(mappedBy = "unidadeConsumo", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Pagamento pagamento;

    /**
     * Calcula o total da unidade somando todos os pedidos
     */
    public BigDecimal calcularTotal() {
        return pedidos.stream()
            .map(Pedido::calcularTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Verifica se a unidade pode receber novos pedidos
     */
    public boolean podeReceberPedidos() {
        return status == StatusUnidadeConsumo.OCUPADA;
    }

    /**
     * Fecha a unidade
     */
    public void fechar() {
        this.status = StatusUnidadeConsumo.FINALIZADA;
        this.fechadaEm = LocalDateTime.now();
    }

    /**
     * Atualiza o status da unidade baseado nos pedidos
     */
    public void atualizarStatus() {
        if (pedidos.isEmpty()) {
            this.status = StatusUnidadeConsumo.OCUPADA;
        } else {
            boolean todosPedidosEntregues = pedidos.stream()
                .allMatch(p -> p.getStatus() == com.restaurante.model.enums.StatusPedido.ENTREGUE);
            
            if (todosPedidosEntregues && pagamento == null) {
                this.status = StatusUnidadeConsumo.AGUARDANDO_PAGAMENTO;
            } else if (pagamento != null && pagamento.getStatus() == com.restaurante.model.enums.StatusPagamento.APROVADO) {
                this.status = StatusUnidadeConsumo.FINALIZADA;
            } else {
                this.status = StatusUnidadeConsumo.OCUPADA;
            }
        }
    }

    /**
     * Helper para compatibilidade - retorna número ou referência
     */
    public String getIdentificador() {
        return numero != null ? numero.toString() : referencia;
    }
}
