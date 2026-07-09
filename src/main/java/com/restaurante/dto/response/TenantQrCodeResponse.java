package com.restaurante.dto.response;

import com.restaurante.model.enums.QrCodeOperacionalTipo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantQrCodeResponse {
    private Long id;
    private String token;
    private QrCodeOperacionalTipo tipo;
    private String nome;

    private Long instituicaoId;
    private Long unidadeAtendimentoId;
    private Long mesaId;

    private boolean ativo;
    private boolean revogado;
    private LocalDateTime criadoEm;
    private LocalDateTime revogadoEm;

    private String qrUrlPublica;
}

