package com.restaurante.store.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Item do carrinho / ordem de compra da Loja Oficial GDSE.
 */
public class StoreCarrinhoItemRequest {

    @NotNull(message = "ID do produto é obrigatório")
    private Long produtoId;

    @NotNull(message = "Quantidade é obrigatória")
    @Min(value = 1, message = "Quantidade mínima é 1")
    private Integer quantidade;

    /**
     * ID da variação escolhida (tamanho/cor). Opcional para produtos sem variações.
     */
    private Long variacaoId;

    /**
     * Observações adicionais do comprador (ex: "sem embalagem de plástico").
     */
    private String observacoes;

    private String personalizedName;
    private Boolean qrIdentityEnabled = false;
    private Boolean premiumPackaging = false;

    public StoreCarrinhoItemRequest() {}

    public StoreCarrinhoItemRequest(Long produtoId, Integer quantidade, Long variacaoId, String observacoes) {
        this.produtoId = produtoId;
        this.quantidade = quantidade;
        this.variacaoId = variacaoId;
        this.observacoes = observacoes;
    }

    public Long getProdutoId() { return produtoId; }
    public void setProdutoId(Long produtoId) { this.produtoId = produtoId; }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }

    public Long getVariacaoId() { return variacaoId; }
    public void setVariacaoId(Long variacaoId) { this.variacaoId = variacaoId; }

    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }

    public String getPersonalizedName() { return personalizedName; }
    public void setPersonalizedName(String personalizedName) { this.personalizedName = personalizedName; }

    public Boolean getQrIdentityEnabled() { return qrIdentityEnabled; }
    public void setQrIdentityEnabled(Boolean qrIdentityEnabled) { this.qrIdentityEnabled = qrIdentityEnabled; }

    public Boolean getPremiumPackaging() { return premiumPackaging; }
    public void setPremiumPackaging(Boolean premiumPackaging) { this.premiumPackaging = premiumPackaging; }
}
