package com.restaurante.store;

import com.restaurante.model.entity.ItemPedido;
import com.restaurante.model.entity.VariacaoProduto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class StoreEntityMappingTest {

    @Test
    void camposDeMigracaoExistemNasEntidades() {
        assertDoesNotThrow(() -> ItemPedido.class.getDeclaredField("variacaoProduto"));
        assertDoesNotThrow(() -> ItemPedido.class.getDeclaredField("personalizedName"));
        assertDoesNotThrow(() -> ItemPedido.class.getDeclaredField("qrIdentityEnabled"));
        assertDoesNotThrow(() -> ItemPedido.class.getDeclaredField("premiumPackaging"));
        assertDoesNotThrow(() -> ItemPedido.class.getDeclaredField("qrIdentityTokenHash"));

        assertDoesNotThrow(() -> VariacaoProduto.class.getDeclaredField("tamanho"));
        assertDoesNotThrow(() -> VariacaoProduto.class.getDeclaredField("cor"));
        assertDoesNotThrow(() -> VariacaoProduto.class.getDeclaredField("sku"));
        assertDoesNotThrow(() -> VariacaoProduto.class.getDeclaredField("preco"));
        assertDoesNotThrow(() -> VariacaoProduto.class.getDeclaredField("stock"));
    }
}
