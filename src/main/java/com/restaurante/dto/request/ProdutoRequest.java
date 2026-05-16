package com.restaurante.dto.request;

import com.restaurante.model.enums.CategoriaProdutoLegacy;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO para criação/atualização de produto
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProdutoRequest {

    @NotBlank(message = "Código é obrigatório")
    @Size(max = 50)
    private String codigo;

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 3, max = 150)
    private String nome;

    @Size(max = 500)
    private String descricao;

    @NotNull(message = "Preço é obrigatório")
    @DecimalMin(value = "0.01", message = "Preço deve ser maior que zero")
    private BigDecimal preco;

    private CategoriaProdutoLegacy categoria;

    /**
     * Nova referência tenant-aware: categoria_produto_id (CategoriaProduto entidade).
     * Preferida nos endpoints tenant-aware.
     */
    private Long categoriaProdutoId;

    private String urlImagem;

    private Integer tempoPreparoMinutos;

    private Boolean disponivel;

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }
    
    public String getDescricao() {
        return descricao;
    }
    
    public BigDecimal getPreco() {
        return preco;
    }
    
    public CategoriaProdutoLegacy getCategoria() {
        return categoria;
    }

    public Long getCategoriaProdutoId() {
        return categoriaProdutoId;
    }
    
    public String getUrlImagem() {
        return urlImagem;
    }
    
    public Integer getTempoPreparoMinutos() {
        return tempoPreparoMinutos;
    }
    
    public Boolean getDisponivel() {
        return disponivel;
    }
}
