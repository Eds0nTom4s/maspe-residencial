package com.restaurante.dto.response;

import com.restaurante.model.entity.VariacaoProduto.TipoVariacao;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VariacaoProdutoResponse {

    private Long id;
    private Long produtoId;
    private TipoVariacao tipo;
    private String valor;
    private String tamanho;
    private String cor;
    private String sku;
    private BigDecimal preco;
    private Integer stock;
    private Boolean ativo;
    private Long versao;
}
