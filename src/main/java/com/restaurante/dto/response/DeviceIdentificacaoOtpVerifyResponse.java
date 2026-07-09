package com.restaurante.dto.response;

import com.restaurante.model.enums.SessaoIdentificacaoStatus;
import lombok.Data;

import java.time.Instant;

@Data
public class DeviceIdentificacaoOtpVerifyResponse {
    private Long clienteConsumoId;
    private Long sessaoConsumoId;
    private String telefoneMascarado;
    private SessaoIdentificacaoStatus identificacaoStatus;
    private boolean identificadoPorOtp;
    private Instant identificadoEm;
    private String statusMensagem;
}

