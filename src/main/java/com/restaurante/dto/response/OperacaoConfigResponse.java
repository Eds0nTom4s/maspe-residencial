package com.restaurante.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperacaoConfigResponse {

    private boolean turnoObrigatorio;
    private String pedidosEscopo;
    private boolean extratoTurnoEnabled;
}
