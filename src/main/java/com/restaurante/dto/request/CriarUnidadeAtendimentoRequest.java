package com.restaurante.dto.request;

import com.restaurante.model.enums.TipoUnidadeAtendimento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriarUnidadeAtendimentoRequest {

    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    @NotNull(message = "Tipo é obrigatório")
    private TipoUnidadeAtendimento tipo;

    private String descricao;
}
