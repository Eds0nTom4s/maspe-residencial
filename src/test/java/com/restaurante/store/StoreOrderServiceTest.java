package com.restaurante.store;

import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.repository.VariacaoProdutoRepository;
import com.restaurante.store.dto.StoreOrderDTO;
import com.restaurante.store.dto.StoreOrderTrackingDTO;
import com.restaurante.store.dto.StoreSocioIdentityDTO;
import com.restaurante.store.mapper.StoreMapper;
import com.restaurante.store.model.StoreOrderMetadata;
import com.restaurante.store.repository.StoreOrderMetadataRepository;
import com.restaurante.store.security.StoreSocioIdentityResolver;
import com.restaurante.store.service.StoreOrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StoreOrderServiceTest {

    @Test
    void cancelamentoNaoPagoLiberaStockEIdempotente() {
        Fixture fixture = new Fixture(StatusFinanceiroPedido.NAO_PAGO, StatusPedido.CRIADO, StatusSubPedido.CRIADO, 2);
        StoreOrderService service = fixture.service();

        StoreOrderDTO dto = new StoreOrderDTO();
        dto.setStatus("CANCELADO");
        when(fixture.mapper.toOrderDTO(any(StoreOrderMetadata.class))).thenReturn(dto);

        StoreOrderDTO response = service.cancelUnpaidOrder(10L, mock(HttpServletRequest.class));
        StoreOrderDTO second = service.cancelUnpaidOrder(10L, mock(HttpServletRequest.class));

        assertEquals("CANCELADO", response.getStatus());
        assertEquals("CANCELADO", second.getStatus());
        assertEquals(3, fixture.variacao.getStock());
        assertEquals(StatusPedido.CANCELADO, fixture.pedido.getStatus());
        verify(fixture.variacaoRepository, times(1)).save(fixture.variacao);
    }

    @Test
    void ordemPagaNaoPodeSerCanceladaCasualmente() {
        Fixture fixture = new Fixture(StatusFinanceiroPedido.PAGO, StatusPedido.CRIADO, StatusSubPedido.EM_PREPARACAO, 2);
        StoreOrderService service = fixture.service();

        assertThrows(BusinessException.class,
                () -> service.cancelUnpaidOrder(10L, mock(HttpServletRequest.class)));
        assertEquals(2, fixture.variacao.getStock());
        verify(fixture.variacaoRepository, never()).save(any());
    }

    @Test
    void listaHistoricoDoSocioAutenticado() {
        Fixture fixture = new Fixture(StatusFinanceiroPedido.PAGO, StatusPedido.CRIADO, StatusSubPedido.CRIADO, 2);
        StoreOrderService service = fixture.service();
        StoreOrderDTO dto = new StoreOrderDTO();
        dto.setNumero("ORD-1");
        when(fixture.mapper.toOrderDTO(any(StoreOrderMetadata.class))).thenReturn(dto);
        when(fixture.metadataRepository.findBySocioIdOrderByCreatedAtDesc("socio-1"))
                .thenReturn(List.of(fixture.metadata));

        List<StoreOrderDTO> result = service.listMyOrders(mock(HttpServletRequest.class));

        assertEquals(1, result.size());
        assertEquals("ORD-1", result.get(0).getNumero());
    }

    @Test
    void rastreioPublicoValidaTelefoneDaOrdem() {
        Fixture fixture = new Fixture(StatusFinanceiroPedido.PAGO, StatusPedido.CRIADO, StatusSubPedido.CRIADO, 2);
        StoreOrderService service = fixture.service();
        StoreOrderTrackingDTO tracking = new StoreOrderTrackingDTO();
        tracking.setNumero("ORD-1");
        when(fixture.metadataRepository.findByPedidoNumero("ORD-1")).thenReturn(Optional.of(fixture.metadata));
        when(fixture.mapper.toTrackingDTO(fixture.metadata)).thenReturn(tracking);

        assertEquals("ORD-1", service.trackPublicOrder("ORD-1", "+244923000000").getNumero());
        assertThrows(com.restaurante.exception.ResourceNotFoundException.class,
                () -> service.trackPublicOrder("ORD-1", "+244999000000"));
    }

    private static class Fixture {
        final StoreOrderMetadataRepository metadataRepository = mock(StoreOrderMetadataRepository.class);
        final StoreSocioIdentityResolver identityResolver = mock(StoreSocioIdentityResolver.class);
        final StoreMapper mapper = mock(StoreMapper.class);
        final PedidoRepository pedidoRepository = mock(PedidoRepository.class);
        final SubPedidoRepository subPedidoRepository = mock(SubPedidoRepository.class);
        final VariacaoProdutoRepository variacaoRepository = mock(VariacaoProdutoRepository.class);
        final Pedido pedido;
        final VariacaoProduto variacao;
        final StoreOrderMetadata metadata;

        Fixture(StatusFinanceiroPedido financeiro, StatusPedido pedidoStatus, StatusSubPedido separacaoStatus, int stock) {
            StoreSocioIdentityDTO identity = new StoreSocioIdentityDTO("socio-1", "Socio", "+244923000000", null);
            when(identityResolver.resolve(any())).thenReturn(identity);

            variacao = VariacaoProduto.builder()
                    .tipo(VariacaoProduto.TipoVariacao.TAMANHO)
                    .valor("M")
                    .stock(stock)
                    .ativo(true)
                    .build();
            variacao.setId(1L);

            ItemPedido item = ItemPedido.builder()
                    .variacaoProduto(variacao)
                    .quantidade(1)
                    .build();
            pedido = Pedido.builder()
                    .numero("ORD-1")
                    .status(pedidoStatus)
                    .statusFinanceiro(financeiro)
                    .itens(new ArrayList<>())
                    .subPedidos(new ArrayList<>())
                    .build();
            pedido.setId(10L);
            Cliente cliente = Cliente.builder()
                    .nome("Socio")
                    .telefone("+244923000000")
                    .build();
            SessaoConsumo sessao = SessaoConsumo.builder()
                    .cliente(cliente)
                    .build();
            pedido.setSessaoConsumo(sessao);
            pedido.getItens().add(item);
            SubPedido subPedido = SubPedido.builder().pedido(pedido).status(separacaoStatus).build();
            pedido.getSubPedidos().add(subPedido);

            metadata = new StoreOrderMetadata();
            metadata.setPedido(pedido);
            metadata.setSocioId("socio-1");
            when(metadataRepository.findByPedidoId(10L)).thenReturn(Optional.of(metadata));
        }

        StoreOrderService service() {
            return new StoreOrderService(metadataRepository, identityResolver, mapper, pedidoRepository,
                    subPedidoRepository, variacaoRepository);
        }
    }
}
