package com.restaurante.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrCodeValidacaoResponse {

    private Boolean valido;
    private String mensagem;
    private QrCodeResponse qrCode;
    private String motivoInvalido; // EXPIRADO, USADO, CANCELADO, NAO_ENCONTRADO
}
