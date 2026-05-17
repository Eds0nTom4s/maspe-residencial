package com.restaurante.dto.response;

import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrarDispositivoResponse {
    private Long dispositivoId;
    private String nome;
    private String codigo;
    private DispositivoTipo tipo;
    private DispositivoStatus status;
    private String activationCode;
    private LocalDateTime activationCodeExpiresAt;
}

