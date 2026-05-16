package com.restaurante.financeiro.monitoramento.dto;

import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.model.enums.StatusFinanceiroPedido;

import java.time.LocalDateTime;

public class PagamentoMonitoramentoFiltro {

    private Long tenantId; // apenas PLATFORM_ADMIN
    private StatusPagamentoGateway statusPagamento;
    private StatusFinanceiroPedido statusFinanceiroPedido;
    private String externalReference;
    private String pedidoNumero;
    private LocalDateTime de;
    private LocalDateTime ate;
    private Integer pendenteHaMaisDeMinutos;
    private Boolean somenteDivergentes;
    private Boolean somenteComCallbackInvalido;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public StatusPagamentoGateway getStatusPagamento() { return statusPagamento; }
    public void setStatusPagamento(StatusPagamentoGateway statusPagamento) { this.statusPagamento = statusPagamento; }

    public StatusFinanceiroPedido getStatusFinanceiroPedido() { return statusFinanceiroPedido; }
    public void setStatusFinanceiroPedido(StatusFinanceiroPedido statusFinanceiroPedido) { this.statusFinanceiroPedido = statusFinanceiroPedido; }

    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String externalReference) { this.externalReference = externalReference; }

    public String getPedidoNumero() { return pedidoNumero; }
    public void setPedidoNumero(String pedidoNumero) { this.pedidoNumero = pedidoNumero; }

    public LocalDateTime getDe() { return de; }
    public void setDe(LocalDateTime de) { this.de = de; }

    public LocalDateTime getAte() { return ate; }
    public void setAte(LocalDateTime ate) { this.ate = ate; }

    public Integer getPendenteHaMaisDeMinutos() { return pendenteHaMaisDeMinutos; }
    public void setPendenteHaMaisDeMinutos(Integer pendenteHaMaisDeMinutos) { this.pendenteHaMaisDeMinutos = pendenteHaMaisDeMinutos; }

    public Boolean getSomenteDivergentes() { return somenteDivergentes; }
    public void setSomenteDivergentes(Boolean somenteDivergentes) { this.somenteDivergentes = somenteDivergentes; }

    public Boolean getSomenteComCallbackInvalido() { return somenteComCallbackInvalido; }
    public void setSomenteComCallbackInvalido(Boolean somenteComCallbackInvalido) { this.somenteComCallbackInvalido = somenteComCallbackInvalido; }
}

