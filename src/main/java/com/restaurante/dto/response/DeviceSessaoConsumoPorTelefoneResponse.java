package com.restaurante.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class DeviceSessaoConsumoPorTelefoneResponse {
    private String telefoneMascarado;
    private List<PublicRecuperacaoSessaoResumoResponse> sessoesAtivas;
}

