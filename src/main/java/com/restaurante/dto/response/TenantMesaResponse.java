package com.restaurante.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantMesaResponse {
    private Long id;
    private Long instituicaoId;
    private Long unidadeAtendimentoId;
    private Integer numero;
    private String referencia;
    private boolean ativa;
    private boolean ocupada;

    private boolean possuiQr;
    private Long qrCodeId;
    private String qrToken;
    private String qrUrlPublica;
}

