package com.restaurante.dto.response;

import com.restaurante.model.enums.StatusSubPedido;
import java.time.LocalDateTime;

public class SubPedidoEventLogResponse {

    private Long id;
    private Long subPedidoId;
    private Long pedidoId;
    private String numeroPedido;
    private Long cozinhaId;
    private String nomeCozinha;
    private StatusSubPedido statusAnterior;
    private StatusSubPedido statusNovo;
    private String usuario;
    private LocalDateTime timestamp;
    private String observacoes;
    private Long tempoTransacaoMs;
    private String descricao;
    private Boolean transicaoCritica;

    public SubPedidoEventLogResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSubPedidoId() { return subPedidoId; }
    public void setSubPedidoId(Long subPedidoId) { this.subPedidoId = subPedidoId; }
    public Long getPedidoId() { return pedidoId; }
    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }
    public String getNumeroPedido() { return numeroPedido; }
    public void setNumeroPedido(String numeroPedido) { this.numeroPedido = numeroPedido; }
    public Long getCozinhaId() { return cozinhaId; }
    public void setCozinhaId(Long cozinhaId) { this.cozinhaId = cozinhaId; }
    public String getNomeCozinha() { return nomeCozinha; }
    public void setNomeCozinha(String nomeCozinha) { this.nomeCozinha = nomeCozinha; }
    public StatusSubPedido getStatusAnterior() { return statusAnterior; }
    public void setStatusAnterior(StatusSubPedido statusAnterior) { this.statusAnterior = statusAnterior; }
    public StatusSubPedido getStatusNovo() { return statusNovo; }
    public void setStatusNovo(StatusSubPedido statusNovo) { this.statusNovo = statusNovo; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    public Long getTempoTransacaoMs() { return tempoTransacaoMs; }
    public void setTempoTransacaoMs(Long tempoTransacaoMs) { this.tempoTransacaoMs = tempoTransacaoMs; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public Boolean getTransicaoCritica() { return transicaoCritica; }
    public void setTransicaoCritica(Boolean transicaoCritica) { this.transicaoCritica = transicaoCritica; }
}
