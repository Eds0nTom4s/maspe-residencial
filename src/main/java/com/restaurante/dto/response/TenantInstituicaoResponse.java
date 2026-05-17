package com.restaurante.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantInstituicaoResponse {
    private Long id;
    private String nome;
    private String sigla;
    private String telefone;
    private String email;
    private String endereco;
    private String provincia;
    private String municipio;
    private boolean ativa;
}

