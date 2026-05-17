package com.restaurante.dto.response;

import com.restaurante.model.enums.UnidadeProducaoTipo;

import java.util.List;

public record MinhaUnidadeProducaoResponse(
        Long tenantId,
        Long unidadeProducaoId,
        String nome,
        String codigo,
        UnidadeProducaoTipo tipo,
        Long instituicaoId,
        Long unidadeAtendimentoId,
        ModoResolucao modoResolucao,
        List<Opcao> opcoes
) {
    public enum ModoResolucao {
        USER_SINGLE_ACTIVE,
        USER_DEFAULT,
        DEVICE_UNIT,
        DEVICE_INSTITUICAO_SINGLE_ACTIVE_FALLBACK,
        DEVICE_TENANT_SINGLE_ACTIVE_FALLBACK,
        EXPLICIT_REQUIRED
    }

    public record Opcao(
            Long unidadeProducaoId,
            String nome,
            String codigo,
            UnidadeProducaoTipo tipo,
            Long instituicaoId,
            Long unidadeAtendimentoId
    ) {}
}
