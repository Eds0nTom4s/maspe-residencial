package com.restaurante.dto.response;

import com.restaurante.model.enums.StatusPedido;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de resposta resumida para Pedido (usado em listas)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PedidoResumoResponse {

    private Long id;
    private String numero;
    private StatusPedido status;
    private BigDecimal total;
    private Integer quantidadeItens;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public StatusPedido getStatus() { return status; }
    public void setStatus(StatusPedido status) { this.status = status; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public Integer getQuantidadeItens() { return quantidadeItens; }
    public void setQuantidadeItens(Integer quantidadeItens) { this.quantidadeItens = quantidadeItens; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
