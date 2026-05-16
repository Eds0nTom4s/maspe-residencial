package com.restaurante.financeiro.monitoramento.dto;

import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.model.enums.StatusFinanceiroPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PagamentoResumoDTO {

    private Long pagamentoId;
    private Long tenantId; // apenas PLATFORM_ADMIN
    private String tenantNome; // apenas PLATFORM_ADMIN
    private Long pedidoId;
    private String pedidoNumero;
    private String externalReference;
    private String gatewayChargeId;
    private MetodoPagamentoAppyPay metodoPagamento;
    private StatusPagamentoGateway statusPagamento;
    private StatusFinanceiroPedido statusFinanceiroPedido;
    private BigDecimal valor;
    private String moeda; // opcional (ex.: "AOA")
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private LocalDateTime confirmadoEm;
    private Long idadeMinutos;
    private Boolean pendenteHaMuitoTempo;
    private Boolean possuiCallback;
    private String ultimaCallbackStatus;
    private Boolean divergente;

    public Long getPagamentoId() { return pagamentoId; }
    public void setPagamentoId(Long pagamentoId) { this.pagamentoId = pagamentoId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getTenantNome() { return tenantNome; }
    public void setTenantNome(String tenantNome) { this.tenantNome = tenantNome; }

    public Long getPedidoId() { return pedidoId; }
    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }

    public String getPedidoNumero() { return pedidoNumero; }
    public void setPedidoNumero(String pedidoNumero) { this.pedidoNumero = pedidoNumero; }

    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String externalReference) { this.externalReference = externalReference; }

    public String getGatewayChargeId() { return gatewayChargeId; }
    public void setGatewayChargeId(String gatewayChargeId) { this.gatewayChargeId = gatewayChargeId; }

    public MetodoPagamentoAppyPay getMetodoPagamento() { return metodoPagamento; }
    public void setMetodoPagamento(MetodoPagamentoAppyPay metodoPagamento) { this.metodoPagamento = metodoPagamento; }

    public StatusPagamentoGateway getStatusPagamento() { return statusPagamento; }
    public void setStatusPagamento(StatusPagamentoGateway statusPagamento) { this.statusPagamento = statusPagamento; }

    public StatusFinanceiroPedido getStatusFinanceiroPedido() { return statusFinanceiroPedido; }
    public void setStatusFinanceiroPedido(StatusFinanceiroPedido statusFinanceiroPedido) { this.statusFinanceiroPedido = statusFinanceiroPedido; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    public String getMoeda() { return moeda; }
    public void setMoeda(String moeda) { this.moeda = moeda; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }

    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(LocalDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }

    public LocalDateTime getConfirmadoEm() { return confirmadoEm; }
    public void setConfirmadoEm(LocalDateTime confirmadoEm) { this.confirmadoEm = confirmadoEm; }

    public Long getIdadeMinutos() { return idadeMinutos; }
    public void setIdadeMinutos(Long idadeMinutos) { this.idadeMinutos = idadeMinutos; }

    public Boolean getPendenteHaMuitoTempo() { return pendenteHaMuitoTempo; }
    public void setPendenteHaMuitoTempo(Boolean pendenteHaMuitoTempo) { this.pendenteHaMuitoTempo = pendenteHaMuitoTempo; }

    public Boolean getPossuiCallback() { return possuiCallback; }
    public void setPossuiCallback(Boolean possuiCallback) { this.possuiCallback = possuiCallback; }

    public String getUltimaCallbackStatus() { return ultimaCallbackStatus; }
    public void setUltimaCallbackStatus(String ultimaCallbackStatus) { this.ultimaCallbackStatus = ultimaCallbackStatus; }

    public Boolean getDivergente() { return divergente; }
    public void setDivergente(Boolean divergente) { this.divergente = divergente; }
}

