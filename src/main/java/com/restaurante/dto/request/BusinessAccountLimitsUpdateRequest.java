package com.restaurante.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessAccountLimitsUpdateRequest {

    @Positive
    private Integer maxTenants;

    @Positive
    private Integer maxInstituicoes;

    @Positive
    private Integer maxUnidadesAtendimento;

    @Positive
    private Integer maxProdutos;

    @Positive
    private Integer maxCategorias;

    @Positive
    private Integer maxUsuarios;

    @Positive
    private Integer maxQrCodes;

    @Positive
    private Integer maxDispositivos;

    @Positive
    private Integer maxPedidosMes;

    private Boolean ativo;
    private String observacao;
}
