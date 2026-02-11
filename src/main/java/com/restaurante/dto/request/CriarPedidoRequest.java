package com.restaurante.dto.request;

import com.restaurante.model.enums.TipoPagamentoPedido;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO para criação de pedido
 * Cliente cria pedido em uma mesa existente
 * 
 * TIPO DE PAGAMENTO:
 * - PRE_PAGO (default): Débito automático do Fundo de Consumo
 * - POS_PAGO: Pagamento posterior (apenas GERENTE/ADMIN)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CriarPedidoRequest {

    @NotNull(message = "ID da unidade de consumo é obrigatório")
    private Long unidadeConsumoId;

    @NotEmpty(message = "Pedido deve conter ao menos um item")
    @Valid
    private List<ItemPedidoRequest> itens;

    /**
     * Tipo de pagamento (opcional, default: PRE_PAGO)
     */
    private TipoPagamentoPedido tipoPagamento;

    private String observacoes;
}
