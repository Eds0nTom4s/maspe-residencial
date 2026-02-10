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
}
