package com.restaurante.dto.response;

import com.restaurante.model.enums.StatusQrCode;
import com.restaurante.model.enums.TipoQrCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrCodeResponse {

    private Long id;
    private String token;
    private TipoQrCode tipo;
    private StatusQrCode status;
    private LocalDateTime expiraEm;
    private Long unidadeDeConsumoId;
    private String unidadeDeConsumoNome;
    private Long pedidoId;
    private LocalDateTime usadoEm;
    private String usadoPor;
    private String metadados;
    private String url;
    private Boolean valido;
    private Boolean expirado;
    private LocalDateTime criadoEm;
    private String criadoPor;
}
