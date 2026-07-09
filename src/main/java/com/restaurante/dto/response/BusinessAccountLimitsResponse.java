package com.restaurante.dto.response;

import com.restaurante.model.enums.BusinessAccountLimitOrigin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessAccountLimitsResponse {

    private Long businessAccountId;
    private String businessAccountNome;
    private BusinessAccountLimitOrigin origin;
    private String basePlanoCodigo;
    private String basePlanoNome;
    private Integer maxTenants;
    private Integer maxInstituicoes;
    private Integer maxUnidadesAtendimento;
    private Integer maxProdutos;
    private Integer maxCategorias;
    private Integer maxUsuarios;
    private Integer maxQrCodes;
    private Integer maxDispositivos;
    private Integer maxPedidosMes;
    private Boolean overrideAtivo;
    private String overrideObservacao;
    private LocalDateTime updatedAt;
}
