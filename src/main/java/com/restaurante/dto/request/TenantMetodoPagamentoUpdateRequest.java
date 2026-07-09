package com.restaurante.dto.request;

import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class TenantMetodoPagamentoUpdateRequest {

    @NotNull
    private PaymentMethodCode codigo;

    @Size(max = 100)
    private String nome;

    @Size(max = 255)
    private String instrucoesPublicas;

    private PaymentMethodStatus status;
    private Boolean disponivelPublico;
    private Boolean disponivelPOS;
    private Boolean disponivelPedido;
    private Boolean disponivelFundoConsumo;
    private BigDecimal valorMinimo;
    private BigDecimal valorMaximo;
    private Integer ordem;
    private String icone;
    private Map<String, Object> metadata;
}
