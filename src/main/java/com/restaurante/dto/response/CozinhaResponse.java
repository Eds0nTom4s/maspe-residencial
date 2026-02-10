package com.restaurante.dto.response;

import com.restaurante.model.enums.TipoCozinha;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CozinhaResponse {

    private Long id;
    private String nome;
    private TipoCozinha tipo;
    private String impressoraId;
    private Boolean ativa;
    private Long subPedidosAtivos;
}
