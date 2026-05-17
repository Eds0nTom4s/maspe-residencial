package com.restaurante.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record DeviceCatalogSyncResponse(
        LocalDateTime syncGeneratedAt,
        List<DeviceCategoriaSyncItem> categorias,
        List<DeviceProdutoSyncItem> produtos
) {
    public record DeviceCategoriaSyncItem(
            Long id,
            String nome,
            String slug,
            Boolean ativo,
            LocalDateTime updatedAt
    ) {}

    public record DeviceProdutoSyncItem(
            Long id,
            String codigo,
            String nome,
            String descricao,
            Long categoriaProdutoId,
            String categoriaProdutoNome,
            String preco,
            Boolean ativo,
            Boolean disponivel,
            String imagemUrl,
            LocalDateTime updatedAt
    ) {}
}

