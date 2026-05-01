package com.restaurante.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.TipoPagamentoFinanceiro;
import com.restaurante.financeiro.gateway.appypay.AppyPayClient;
import com.restaurante.financeiro.gateway.appypay.AppyPayProperties;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.store.repository.StoreOrderMetadataRepository;
import com.restaurante.store.service.StorePaymentService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorePaymentServiceTest {

    @Test
    void confirmacaoDuplicadaNaoDuplicaMudancasEMoveParaSeparacao() {
        PagamentoGatewayRepository pagamentoRepository = mock(PagamentoGatewayRepository.class);
        StoreOrderMetadataRepository metadataRepository = mock(StoreOrderMetadataRepository.class);
        PedidoRepository pedidoRepository = mock(PedidoRepository.class);
        SubPedidoRepository subPedidoRepository = mock(SubPedidoRepository.class);
        AppyPayProperties appyPayProperties = new AppyPayProperties();
        appyPayProperties.setCallbackUrl("https://api.gdse.ao/api/pagamentos/callback");
        appyPayProperties.setReturnUrl("https://loja.gdse.ao/pagamento/retorno");
        StorePaymentService service = new StorePaymentService(mock(AppyPayClient.class), pagamentoRepository,
                metadataRepository, pedidoRepository, subPedidoRepository, appyPayProperties, new ObjectMapper());

        Pedido pedido = Pedido.builder()
                .statusFinanceiro(StatusFinanceiroPedido.NAO_PAGO)
                .total(new BigDecimal("25000"))
                .subPedidos(new ArrayList<>())
                .build();
        pedido.setId(10L);
        SubPedido subPedido = SubPedido.builder().pedido(pedido).status(StatusSubPedido.CRIADO).build();
        pedido.getSubPedidos().add(subPedido);

        Pagamento pagamento = Pagamento.builder()
                .pedido(pedido)
                .tipoPagamento(TipoPagamentoFinanceiro.STORE_PEDIDO)
                .status(StatusPagamentoGateway.PENDENTE)
                .amount(new BigDecimal("25000"))
                .externalReference("ST123")
                .build();

        service.confirmStorePayment(pagamento);
        service.confirmStorePayment(pagamento);

        assertEquals(StatusFinanceiroPedido.PAGO, pedido.getStatusFinanceiro());
        assertEquals(StatusSubPedido.EM_PREPARACAO, subPedido.getStatus());
        assertTrue(pagamento.isConfirmado());
        verify(subPedidoRepository, times(1)).save(subPedido);
        verify(pedidoRepository, times(1)).save(pedido);
    }
}
