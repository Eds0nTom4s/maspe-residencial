package com.restaurante.notificacao.service;

import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.service.operacional.OperationalCapabilitiesPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WebSocketNotificacaoServiceTest {

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final OperationalCapabilitiesPolicy capabilitiesPolicy = mock(OperationalCapabilitiesPolicy.class);
    private final WebSocketNotificacaoService service = new WebSocketNotificacaoService(
            messagingTemplate,
            capabilitiesPolicy
    );

    @Test
    void pontoSemProducaoNaoPublicaEventoDeEntradaNoKds() {
        Pedido pedido = new Pedido();
        SubPedido subPedido = new SubPedido();
        subPedido.setPedido(pedido);
        when(capabilitiesPolicy.canUseProduction(pedido)).thenReturn(false);

        service.notificarNovoSubPedido(subPedido, "system");

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void pontoSemProducaoNaoPublicaEventoDeLiberacaoAutomatica() {
        Pedido pedido = new Pedido();
        when(capabilitiesPolicy.canUseProduction(pedido)).thenReturn(false);

        service.notificarPedidoLiberadoAutomaticamente(pedido);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void pontoSemProducaoNaoPublicaEventoDePedidoPronto() {
        Pedido pedido = new Pedido();
        SubPedido subPedido = new SubPedido();
        subPedido.setPedido(pedido);
        when(capabilitiesPolicy.canUseProduction(pedido)).thenReturn(false);

        service.notificarSubPedidoPronto(subPedido, "system");

        verifyNoInteractions(messagingTemplate);
    }
}
