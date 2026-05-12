package com.restaurante.dto.request;

import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request para iniciar um pagamento AppyPay (recarga de Fundo de Consumo).
 *
 * <h2>Métodos suportados</h2>
 * <ul>
 *   <li><b>GPO</b> — pagamento imediato via app AppyPay. Saldo creditado instantaneamente.</li>
 *   <li><b>REF</b> — gera entidade + referência bancária (Multicaixa).
 *       O saldo é creditado após callback de confirmação.</li>
 * </ul>
 */
@Data
public class IniciarPagamentoRequest {

    /**
     * ID do Fundo de Consumo a recarregar.
     * Obter via GET /api/fundos/cliente/{clienteId} ou GET /api/fundos/{token}.
     */
    @NotNull(message = "fundoId é obrigatório")
    private Long fundoId;

    /**
     * Valor da recarga em AOA (Kwanzas). Mínimo: 1.00 AOA.
     */
    @NotNull(message = "valor é obrigatório")
    @DecimalMin(value = "1.00", message = "Valor mínimo é 1.00 AOA")
    private BigDecimal valor;

    /**
     * Método de pagamento: GPO (instantâneo) ou REF (referência bancária).
     */
    @NotNull(message = "metodo é obrigatório")
    private MetodoPagamentoAppyPay metodo;
}
