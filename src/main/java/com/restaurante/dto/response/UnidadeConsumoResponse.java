package com.restaurante.dto.response;

import com.restaurante.model.enums.StatusUnidadeConsumo;
import com.restaurante.model.enums.TipoUnidadeConsumo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnidadeConsumoResponse {

    private Long id;
    private String referencia;
    private Boolean modoAnonimo;
    private TipoUnidadeConsumo tipo;
    private Integer numero;
    private String qrCode;
    private StatusUnidadeConsumo status;
    private Integer capacidade;
    private ClienteResponse cliente;
    private List<PedidoResumoResponse> pedidos;
    private BigDecimal total;
    private LocalDateTime abertaEm;
    private LocalDateTime fechadaEm;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public Boolean getModoAnonimo() { return modoAnonimo; }
    public void setModoAnonimo(Boolean modoAnonimo) { this.modoAnonimo = modoAnonimo; }

    public TipoUnidadeConsumo getTipo() { return tipo; }
    public void setTipo(TipoUnidadeConsumo tipo) { this.tipo = tipo; }

    public Integer getNumero() { return numero; }
    public void setNumero(Integer numero) { this.numero = numero; }

    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }

    public StatusUnidadeConsumo getStatus() { return status; }
    public void setStatus(StatusUnidadeConsumo status) { this.status = status; }

    public Integer getCapacidade() { return capacidade; }
    public void setCapacidade(Integer capacidade) { this.capacidade = capacidade; }

    public ClienteResponse getCliente() { return cliente; }
    public void setCliente(ClienteResponse cliente) { this.cliente = cliente; }

    public List<PedidoResumoResponse> getPedidos() { return pedidos; }
    public void setPedidos(List<PedidoResumoResponse> pedidos) { this.pedidos = pedidos; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public LocalDateTime getAbertaEm() { return abertaEm; }
    public void setAbertaEm(LocalDateTime abertaEm) { this.abertaEm = abertaEm; }

    public LocalDateTime getFechadaEm() { return fechadaEm; }
    public void setFechadaEm(LocalDateTime fechadaEm) { this.fechadaEm = fechadaEm; }
}
