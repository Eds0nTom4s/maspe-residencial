package com.restaurante.provisioning.preview.dto;

import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvisioningTemplatePreviewDTO {
    private String codigo;
    private String nome;
    private TenantTipo tipoTenant;
    private boolean criarMesas;
    private int quantidadeMesas;
    private boolean criarQrPorMesa;
    private String prefixoMesa;
    private String unidadeAtendimentoDefaultNome;
    private TipoUnidadeAtendimento unidadeAtendimentoDefaultTipo;
    private QrCodeOperacionalTipo qrPrincipalTipo;
}

