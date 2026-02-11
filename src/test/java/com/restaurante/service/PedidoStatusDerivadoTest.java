package com.restaurante.service;

import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes para garantir que StatusPedido é sempre derivado
 * exclusivamente dos SubPedidos via PedidoService.recalcularStatusPedido.
 */
@ExtendWith(MockitoExtension.class)
class PedidoStatusDerivadoTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private UnidadeDeConsumoService unidadeDeConsumoService;

    @Mock
    private ProdutoService produtoService;

    @Mock
    private SubPedidoService subPedidoService;

    @Mock
    private EventLogService eventLogService;

    @Mock
    private PedidoFinanceiroService pedidoFinanceiroService;

    @InjectMocks
    private PedidoService pedidoService;

    private Pedido pedido;

    @BeforeEach
    void setUp() {
        pedido = Pedido.builder()
                .numero("PED-STATUS-001")
                .status(StatusPedido.CRIADO)
                .build();
        pedido.setId(1L);
    }

    private void configurarPedidoComSubPedidos(StatusSubPedido... statusSubPedidos) {
        List<SubPedido> subPedidos = java.util.Arrays.stream(statusSubPedidos)
                .map(status -> {
                    SubPedido sp = new SubPedido();
                    sp.setStatus(status);
                    sp.setPedido(pedido);
                    return sp;
                })
                .toList();

        pedido.setSubPedidos(subPedidos);

        when(pedidoRepository.findById(pedido.getId())).thenReturn(Optional.of(pedido));
        lenient().when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void pedidoComTodosSubPedidosEntreguesDeveFicarFinalizado() {
        configurarPedidoComSubPedidos(
                StatusSubPedido.ENTREGUE,
                StatusSubPedido.ENTREGUE
        );

        StatusPedido novoStatus = pedidoService.recalcularStatusPedido(pedido.getId());

        assertEquals(StatusPedido.FINALIZADO, novoStatus);
        assertEquals(StatusPedido.FINALIZADO, pedido.getStatus());
        verify(eventLogService, times(1))
                .registrarEventoPedido(eq(pedido), eq(StatusPedido.CRIADO), eq(StatusPedido.FINALIZADO), any(), any());
    }

    @Test
    void pedidoComUmSubPedidoEmPreparacaoDeveFicarEmAndamento() {
        configurarPedidoComSubPedidos(
                StatusSubPedido.ENTREGUE,
                StatusSubPedido.EM_PREPARACAO
        );

        StatusPedido novoStatus = pedidoService.recalcularStatusPedido(pedido.getId());

        assertEquals(StatusPedido.EM_ANDAMENTO, novoStatus);
        assertEquals(StatusPedido.EM_ANDAMENTO, pedido.getStatus());
        verify(eventLogService, times(1))
                .registrarEventoPedido(eq(pedido), eq(StatusPedido.CRIADO), eq(StatusPedido.EM_ANDAMENTO), any(), any());
    }

    @Test
    void pedidoComTodosSubPedidosCanceladosDeveFicarCancelado() {
        configurarPedidoComSubPedidos(
                StatusSubPedido.CANCELADO,
                StatusSubPedido.CANCELADO
        );

        StatusPedido novoStatus = pedidoService.recalcularStatusPedido(pedido.getId());

        assertEquals(StatusPedido.CANCELADO, novoStatus);
        assertEquals(StatusPedido.CANCELADO, pedido.getStatus());
        verify(eventLogService, times(1))
                .registrarEventoPedido(eq(pedido), eq(StatusPedido.CRIADO), eq(StatusPedido.CANCELADO), any(), any());
    }

    @Test
    void naoDeveAlterarStatusQuandoStatusCalculadoForIgualAoAtual() {
        // Pedido já EM_ANDAMENTO
        pedido.setStatus(StatusPedido.EM_ANDAMENTO);
        configurarPedidoComSubPedidos(
                StatusSubPedido.PENDENTE,
                StatusSubPedido.EM_PREPARACAO
        );

        StatusPedido novoStatus = pedidoService.recalcularStatusPedido(pedido.getId());

        assertEquals(StatusPedido.EM_ANDAMENTO, novoStatus);
        assertEquals(StatusPedido.EM_ANDAMENTO, pedido.getStatus());
        // Não deve registrar novo event log se status não mudou
        verify(eventLogService, never()).registrarEventoPedido(any(), any(), any(), any(), any());
    }
}
