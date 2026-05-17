package com.restaurante.dto.response;

import com.restaurante.model.enums.TipoUnidadeAtendimento;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantUnidadeAtendimentoResponse {
    private Long id;
    private Long instituicaoId;
    private String instituicaoNome;
    private String nome;
    private TipoUnidadeAtendimento tipo;
    private boolean ativa;
}

