package com.restaurante.dto.response;

import com.restaurante.model.enums.StatusPedido;
import java.time.LocalDateTime;

public class PedidoEventLogResponse {

    private Long id;
    private Long pedidoId;
    private String numeroPedido;
    private StatusPedido statusAnterior;
    private StatusPedido statusNovo;
    private String usuario;
    private LocalDateTime timestamp;
    private String observacoes;
    private String descricao;

    public PedidoEventLogResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPedidoId() { return pedidoId; }
    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }
    public String getNumeroPedido() { return numeroPedido; }
    public void setNumeroPedido(String numeroPedido) { this.numeroPedido = numeroPedido; }
    public StatusPedido getStatusAnterior() { return statusAnterior; }
    public void setStatusAnterior(StatusPedido statusAnterior) { this.statusAnterior = statusAnterior; }
    public StatusPedido getStatusNovo() { return statusNovo; }
    public void setStatusNovo(StatusPedido statusNovo) { this.statusNovo = statusNovo; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
}
