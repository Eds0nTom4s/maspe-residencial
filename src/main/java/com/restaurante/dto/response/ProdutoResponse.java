package com.restaurante.dto.response;

import com.restaurante.model.enums.CategoriaProduto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de resposta para Produto
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProdutoResponse {

    private Long id;
    private String codigo;
    private String nome;
    private String descricao;
    private BigDecimal preco;
    private CategoriaProduto categoria;
    private String urlImagem;
    private Integer tempoPreparoMinutos;
    private Boolean disponivel;
    private Boolean ativo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
