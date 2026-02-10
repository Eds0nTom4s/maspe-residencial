package com.restaurante.model.entity;

import com.restaurante.model.enums.CategoriaProduto;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidade Produto
 * Representa um item do cardápio disponível para pedidos
 */
@Entity
@Table(name = "produtos", indexes = {
    @Index(name = "idx_produto_codigo", columnList = "codigo", unique = true),
    @Index(name = "idx_produto_categoria", columnList = "categoria"),
    @Index(name = "idx_produto_ativo", columnList = "ativo")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Produto extends BaseEntity {

    @NotBlank(message = "Código é obrigatório")
    @Size(max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 3, max = 150, message = "Nome deve ter entre 3 e 150 caracteres")
    @Column(nullable = false, length = 150)
    private String nome;

    @Size(max = 500)
    @Column(length = 500)
    private String descricao;

    @NotNull(message = "Preço é obrigatório")
    @DecimalMin(value = "0.01", message = "Preço deve ser maior que zero")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Categoria é obrigatória")
    @Column(nullable = false, length = 30)
    private CategoriaProduto categoria;

    @Column(name = "url_imagem", length = 500)
    private String urlImagem;

    @Column(name = "tempo_preparo_minutos")
    private Integer tempoPreparoMinutos;

    @Column(name = "disponivel", nullable = false)
    @Builder.Default
    private Boolean disponivel = true;

    @Column(name = "ativo", nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    // Relacionamento com itens de pedido
    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ItemPedido> itensPedido = new ArrayList<>();

    /**
     * Calcula o subtotal baseado na quantidade
     */
    public BigDecimal calcularSubtotal(Integer quantidade) {
        return preco.multiply(BigDecimal.valueOf(quantidade));
    }
}
