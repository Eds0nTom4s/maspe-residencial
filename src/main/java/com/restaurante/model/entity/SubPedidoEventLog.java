package com.restaurante.model.entity;

import com.restaurante.model.enums.StatusSubPedido;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Event Log imutável para auditoria de mudanças em SubPedido
 * Registra todas as transições de status na cozinha
 */
@Entity
@Table(name = "subpedido_event_log", indexes = {
    @Index(name = "idx_subpedido_event_subpedido", columnList = "subpedido_id"),
    @Index(name = "idx_subpedido_event_cozinha", columnList = "cozinha_id"),
    @Index(name = "idx_subpedido_event_timestamp", columnList = "timestamp"),
    @Index(name = "idx_subpedido_event_usuario", columnList = "usuario")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubPedidoEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subpedido_id", nullable = false)
    private SubPedido subPedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cozinha_id", nullable = false)
    private Cozinha cozinha;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior")
    private StatusSubPedido statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", nullable = false)
    private StatusSubPedido statusNovo;

    @Column(name = "usuario", length = 100)
    private String usuario;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "observacoes", length = 500)
    private String observacoes;

    @Column(name = "tempo_transacao_ms")
    private Long tempoTransacaoMs;

    @Column(name = "ip_origem", length = 50)
    private String ipOrigem;

    /**
     * Timestamp de criação (imutável)
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }

    /**
     * Descrição legível do evento
     */
    public String getDescricao() {
        if (statusAnterior == null) {
            return String.format("SubPedido criado na %s com status %s", 
                cozinha.getNome(), statusNovo);
        }
        return String.format("Status na %s alterado de %s para %s", 
            cozinha.getNome(), statusAnterior, statusNovo);
    }

    /**
     * Verifica se é uma transição crítica (pronto para entrega)
     */
    public boolean isTransicaoCritica() {
        return statusNovo == StatusSubPedido.PRONTO || statusNovo == StatusSubPedido.ENTREGUE;
    }
}
