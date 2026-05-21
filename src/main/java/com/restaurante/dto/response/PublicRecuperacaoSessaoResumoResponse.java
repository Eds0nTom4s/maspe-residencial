package com.restaurante.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicRecuperacaoSessaoResumoResponse {
    private Long sessaoId;
    private String codigoConsumo;
    private LocalDateTime abertaEm;
    private String unidade;
    private String saldoFundo;
    private String status;
}

