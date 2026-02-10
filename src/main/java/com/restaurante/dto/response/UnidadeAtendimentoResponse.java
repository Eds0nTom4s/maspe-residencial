package com.restaurante.dto.response;

import com.restaurante.model.enums.TipoUnidadeAtendimento;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnidadeAtendimentoResponse {

    private Long id;
    private String nome;
    private TipoUnidadeAtendimento tipo;
    private String descricao;
    private Boolean ativa;
    private Boolean operacional;
    private List<CozinhaResponse> cozinhas;
    private Long unidadesConsumoAtivas;
}
