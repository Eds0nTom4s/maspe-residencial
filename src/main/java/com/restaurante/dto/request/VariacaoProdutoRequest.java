package com.restaurante.dto.request;

import com.restaurante.model.entity.VariacaoProduto.TipoVariacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VariacaoProdutoRequest {

    @NotNull(message = "Tipo da variação é obrigatório")
    private TipoVariacao tipo;

    @NotBlank(message = "Valor da variação é obrigatório")
    private String valor;

    private String tamanho;

    private String cor;

    private String sku;

    private BigDecimal preco;

    private Integer stock;

    private Boolean ativo;
}
