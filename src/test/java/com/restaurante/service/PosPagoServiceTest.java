package com.restaurante.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.LimitePosPagoExcedidoException;
import com.restaurante.exception.PosPagoDesabilitadoException;
import com.restaurante.exception.PosPagoNaoPermitidoException;
import com.restaurante.model.entity.ConfiguracaoFinanceiraSistema;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.TipoPagamentoPedido;
import com.restaurante.repository.ConfiguracaoFinanceiraSistemaRepository;
import com.restaurante.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes para regras de pós-pago (risco financeiro).
 */
@ExtendWith(MockitoExtension.class)
class PosPagoServiceTest {

    @Mock
    private FundoConsumoService fundoConsumoService;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ConfiguracaoFinanceiraSistemaRepository configuracaoRepository;

    @Mock
    private EventLogService eventLogService;

    @InjectMocks
    private PedidoFinanceiroService pedidoFinanceiroService;

    @InjectMocks
    private ConfiguracaoFinanceiraService configuracaoFinanceiraService;

    private Pedido pedidoPosPago;

    @BeforeEach
    void setUp() {
        pedidoPosPago = Pedido.builder()
                .numero("PED-POS-001")
                .tipoPagamento(TipoPagamentoPedido.POS_PAGO)
                .statusFinanceiro(StatusFinanceiroPedido.NAO_PAGO)
                .total(new BigDecimal("100.00"))
                .build();
                pedidoPosPago.setId(1L);
    }

    @Test
    void gerenteOuAdminPodeAutorizarPosPago() {
        assertDoesNotThrow(() -> pedidoFinanceiroService.autorizarPosPago(Set.of("GERENTE")));
        assertDoesNotThrow(() -> pedidoFinanceiroService.autorizarPosPago(Set.of("ADMIN")));
    }

    @Test
    void atendenteOuClienteNaoPodemAutorizarPosPago() {
        assertThrows(PosPagoNaoPermitidoException.class,
                () -> pedidoFinanceiroService.autorizarPosPago(Set.of("ATENDENTE")));
        assertThrows(PosPagoNaoPermitidoException.class,
                () -> pedidoFinanceiroService.autorizarPosPago(Set.of("CLIENTE")));
    }

    @Test
    void deveLancarPosPagoDesabilitadoQuandoInterruptorGlobalOff() {
        ConfiguracaoFinanceiraSistema config = ConfiguracaoFinanceiraSistema.builder()
                .posPagoAtivo(false)
                .build();
                config.setId(10L);
        when(configuracaoRepository.findAtual()).thenReturn(Optional.of(config));

        assertThrows(PosPagoDesabilitadoException.class,
                () -> configuracaoFinanceiraService.validarCriacaoPosPago(1L, new BigDecimal("100.00"), Set.of("GERENTE")));
    }

    @Test
    void deveRespeitarLimiteDeRiscoPosPago() {
        ConfiguracaoFinanceiraSistema config = ConfiguracaoFinanceiraSistema.builder()
                .posPagoAtivo(true)
                .build();
                config.setId(10L);
        when(configuracaoRepository.findAtual()).thenReturn(Optional.of(config));

        // Total aberto atual 400, limite padrão 500
        Pedido aberto1 = Pedido.builder()
                .total(new BigDecimal("150.00"))
                .tipoPagamento(TipoPagamentoPedido.POS_PAGO)
                .statusFinanceiro(StatusFinanceiroPedido.NAO_PAGO)
                .build();
        aberto1.setId(2L);
        Pedido aberto2 = Pedido.builder()
                .total(new BigDecimal("250.00"))
                .tipoPagamento(TipoPagamentoPedido.POS_PAGO)
                .statusFinanceiro(StatusFinanceiroPedido.NAO_PAGO)
                .build();
        aberto2.setId(3L);

        when(pedidoRepository.findByUnidadeConsumoIdAndTipoPagamentoAndStatusFinanceiro(
                eq(1L), eq(TipoPagamentoPedido.POS_PAGO), eq(StatusFinanceiroPedido.NAO_PAGO)))
                .thenReturn(List.of(aberto1, aberto2));

        // Novo pedido dentro do limite → OK
        assertDoesNotThrow(() -> configuracaoFinanceiraService.validarCriacaoPosPago(
                1L, new BigDecimal("100.00"), Set.of("GERENTE")));

        // Novo pedido que estoura limite → exceção
        assertThrows(LimitePosPagoExcedidoException.class,
                () -> configuracaoFinanceiraService.validarCriacaoPosPago(
                        1L, new BigDecimal("200.00"), Set.of("GERENTE")));
    }

    @Test
    void confirmarPagamentoPosPagoDeveMarcarComoPagoEUmaUnicaVez() {
        when(pedidoRepository.findById(pedidoPosPago.getId())).thenReturn(Optional.of(pedidoPosPago));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Primeira confirmação → sucesso
        pedidoFinanceiroService.confirmarPagamentoPosPago(pedidoPosPago.getId());
        assertEquals(StatusFinanceiroPedido.PAGO, pedidoPosPago.getStatusFinanceiro());

        // Segunda confirmação → BusinessException (não idempotente por API)
        assertThrows(BusinessException.class,
                () -> pedidoFinanceiroService.confirmarPagamentoPosPago(pedidoPosPago.getId()));

        verify(pedidoRepository, times(2)).findById(pedidoPosPago.getId());
        verify(pedidoRepository, times(1)).save(any(Pedido.class));
    }
}
