package com.restaurante.dto.request;

import com.restaurante.model.enums.MetodoPagamentoManual;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TenantPdvCreatePedidoRequest {
    @NotBlank
    @Size(max = 160)
    private String clientRequestId;

    @NotNull
    private Long instituicaoId;

    @NotNull
    private Long unidadeAtendimentoId;

    @NotNull
    private MetodoPagamentoManual metodoPagamento;

    @Size(max = 500)
    private String observacao;

    @Valid
    @NotEmpty
    @Size(max = 100)
    private List<TenantPdvCreatePedidoItemRequest> itens;
}
