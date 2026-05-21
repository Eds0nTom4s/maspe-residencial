package com.restaurante.dto.response;

import com.restaurante.model.enums.SessaoIdentificacaoStatus;
import lombok.Data;

@Data
public class PublicIdentificacaoOtpVerifyResponse {
    private Long clienteConsumoId;
    private Long sessaoConsumoId;
    private String telefoneMascarado;
    private SessaoIdentificacaoStatus identificacaoStatus;
}

