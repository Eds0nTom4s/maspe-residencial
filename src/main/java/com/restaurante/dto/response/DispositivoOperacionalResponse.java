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
public class DispositivoOperacionalResponse {
    private Long id;
    private String nome;
    private String codigo;
    private DispositivoTipo tipo;
    private DispositivoStatus status;
    private Long instituicaoId;
    private Long unidadeAtendimentoId;
    private Long unidadeProducaoId;
    private LocalDateTime ultimoHeartbeatEm;
    private String appVersion;
    private String platform;
    private LocalDateTime ativadoEm;
    private LocalDateTime revogadoEm;
}
