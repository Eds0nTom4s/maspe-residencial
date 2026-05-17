package com.restaurante.dto.response;

import com.restaurante.model.enums.UnidadeProducaoTipo;

import java.time.LocalDateTime;
import java.util.List;

public record DeviceProducaoSyncResponse(
        LocalDateTime syncGeneratedAt,
        List<DeviceUnidadeProducaoSyncItem> unidadesProducao,
        List<DeviceRotaProducaoSyncItem> rotas
) {
    public record DeviceUnidadeProducaoSyncItem(
            Long id,
            String nome,
            String codigo,
            UnidadeProducaoTipo tipo,
            Long instituicaoId,
            Long unidadeAtendimentoId,
            Boolean ativo,
            LocalDateTime updatedAt
    ) {}

    public record DeviceRotaProducaoSyncItem(
            Long id,
            Long categoriaProdutoId,
            Long unidadeProducaoId,
            Boolean ativo,
            Integer prioridade,
            LocalDateTime updatedAt
    ) {}
}

