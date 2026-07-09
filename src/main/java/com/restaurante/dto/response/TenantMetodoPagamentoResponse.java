package com.restaurante.dto.response;

import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodProvider;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TenantMetodoPagamentoResponse {
    private PaymentMethodCode codigo;
    private String nome;
    private boolean ativo;
    private boolean disponivelPublico;
    private boolean disponivelPOS;
    private boolean exigeConfirmacaoManual;
    private PaymentMethodProvider gateway;
    private String instrucoesPublicas;
    private Integer ordem;
    private LocalDateTime configuradoEm;
    private String configuradoPor;
}
