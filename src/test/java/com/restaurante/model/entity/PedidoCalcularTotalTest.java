package com.restaurante.model.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PedidoCalcularTotalTest {

    @Test
    void pedidoSemSubPedidosCalculaTotalPelosItensDirectos() {
        Pedido pedido = new Pedido();
        pedido.setSubPedidos(List.of());
        pedido.setItens(List.of(
                item("400.00"),
                item("600.50")
        ));

        BigDecimal total = pedido.calcularTotal();

        assertThat(total).isEqualByComparingTo("1000.50");
        assertThat(pedido.getTotal()).isEqualByComparingTo("1000.50");
    }

    @Test
    void pedidoComSubPedidosCalculaTotalPelosSubPedidosSemDuplicarItensDirectos() {
        Pedido pedido = new Pedido();
        pedido.setItens(List.of(
                item("999.00")
        ));
        pedido.setSubPedidos(List.of(
                subPedido("300.00"),
                subPedido("700.00")
        ));

        BigDecimal total = pedido.calcularTotal();

        assertThat(total).isEqualByComparingTo("1000.00");
        assertThat(pedido.getTotal()).isEqualByComparingTo("1000.00");
    }

    @Test
    void subtotalNuloEmItemDirectoNaoQuebraCalculo() {
        Pedido pedido = new Pedido();
        pedido.setSubPedidos(List.of());
        pedido.setItens(List.of(
                item(null),
                item("250.00")
        ));

        BigDecimal total = pedido.calcularTotal();

        assertThat(total).isEqualByComparingTo("250.00");
        assertThat(pedido.getTotal()).isEqualByComparingTo("250.00");
    }

    @Test
    void pedidoSemItensESemSubPedidosCalculaZero() {
        Pedido pedido = new Pedido();
        pedido.setSubPedidos(List.of());
        pedido.setItens(List.of());

        BigDecimal total = pedido.calcularTotal();

        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(pedido.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private ItemPedido item(String subtotal) {
        ItemPedido item = new ItemPedido();
        item.setSubtotal(subtotal != null ? new BigDecimal(subtotal) : null);
        return item;
    }

    private SubPedido subPedido(String total) {
        SubPedido subPedido = new SubPedido();
        subPedido.setTotal(new BigDecimal(total));
        return subPedido;
    }
}
