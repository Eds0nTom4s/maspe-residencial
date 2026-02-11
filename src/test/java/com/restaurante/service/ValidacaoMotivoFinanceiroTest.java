package com.restaurante.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.TipoPagamentoPedido;
import com.restaurante.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Testes de validação de motivo obrigatório para ações financeiras críticas.
 */
@ExtendWith(MockitoExtension.class)
class ValidacaoMotivoFinanceiroTest {

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

    @InjectMocks
    private PedidoFinanceiroService pedidoFinanceiroServiceReal;

    private Pedido pedidoPagoPrePago;

    @BeforeEach
    void setUp() {
        pedidoPagoPrePago = Pedido.builder()
                .numero("PED-MOTIVO-001")
                .status(StatusPedido.CRIADO)
                .statusFinanceiro(StatusFinanceiroPedido.PAGO)
                .tipoPagamento(TipoPagamentoPedido.PRE_PAGO)
                .total(new BigDecimal("100.00"))
                .build();
        pedidoPagoPrePago.setId(1L);
    }

    @Test
    void cancelarPedidoSemMotivoDeveFalhar() {
        lenient().when(pedidoRepository.findById(pedidoPagoPrePago.getId())).thenReturn(Optional.of(pedidoPagoPrePago));

        assertThrows(BusinessException.class,
                () -> pedidoService.cancelar(pedidoPagoPrePago.getId(), "   "));
    }

    @Test
    void estornarPedidoSemMotivoDeveFalhar() {
        // Usa serviço real de estorno, que valida motivo
        lenient().when(pedidoRepository.findById(pedidoPagoPrePago.getId())).thenReturn(Optional.of(pedidoPagoPrePago));

        assertThrows(BusinessException.class,
                () -> pedidoFinanceiroServiceReal.estornarPedido(pedidoPagoPrePago.getId(), ""));
    }
}
