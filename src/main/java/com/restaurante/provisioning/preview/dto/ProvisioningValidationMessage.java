package com.restaurante.provisioning.preview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvisioningValidationMessage {

    public enum Severidade {
        INFO,
        WARNING,
        BLOCKING
    }

    private String codigo;
    private Severidade severidade;
    private String campo;
    private String mensagem;
    private String detalhe;
    private String acaoRecomendada;
}

