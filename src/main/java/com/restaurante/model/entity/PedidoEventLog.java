package com.restaurante.model.entity;

import com.restaurante.model.enums.StatusPedido;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Event Log imutável para auditoria de mudanças em Pedido
 * Registra todas as transições de status para compliance e debug
 */
@Entity
@Table(name = "pedido_event_log", indexes = {
    @Index(name = "idx_pedido_event_pedido", columnList = "pedido_id"),
    @Index(name = "idx_pedido_event_timestamp", columnList = "timestamp"),
    @Index(name = "idx_pedido_event_usuario", columnList = "usuario")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PedidoEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior")
    private StatusPedido statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", nullable = false)
    private StatusPedido statusNovo;

    @Column(name = "usuario", length = 100)
    private String usuario;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "observacoes", length = 500)
    private String observacoes;

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
            return String.format("Pedido criado com status %s", statusNovo);
        }
        return String.format("Status alterado de %s para %s", statusAnterior, statusNovo);
    }

    // Getters and Setters manually added

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public void setPedido(Pedido pedido) {
        this.pedido = pedido;
    }

    public StatusPedido getStatusAnterior() {
        return statusAnterior;
    }

    public void setStatusAnterior(StatusPedido statusAnterior) {
        this.statusAnterior = statusAnterior;
    }

    public StatusPedido getStatusNovo() {
        return statusNovo;
    }

    public void setStatusNovo(StatusPedido statusNovo) {
        this.statusNovo = statusNovo;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public String getIpOrigem() {
        return ipOrigem;
    }

    public void setIpOrigem(String ipOrigem) {
        this.ipOrigem = ipOrigem;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
