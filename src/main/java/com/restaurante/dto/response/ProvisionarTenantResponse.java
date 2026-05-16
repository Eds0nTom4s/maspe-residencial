package com.restaurante.dto.response;

import com.restaurante.model.enums.TenantEstado;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvisionarTenantResponse {

    private Long tenantId;
    private String tenantNome;
    private String tenantSlug;
    private String tenantCode;
    private TenantEstado tenantEstado;

    private String planoCodigo;
    private Long subscricaoId;

    private Long instituicaoId;
    private String instituicaoNome;

    private Long unidadeAtendimentoId;
    private String unidadeAtendimentoNome;

    private Long ownerUserId;
    private String ownerEmail;

    private Long qrCodeId;
    private String qrToken;
    private String qrUrlPublica;

    private List<String> categoriasCriadas;
    private List<String> mensagens;
}

