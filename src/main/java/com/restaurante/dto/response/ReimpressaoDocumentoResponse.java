package com.restaurante.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReimpressaoDocumentoResponse {
    private String tipoDocumento;
    private String codigo;
    private String conteudoImprimivel;
    private String qrCodePayload;
    private LocalDateTime emitidoEm;
}

