package com.restaurante.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.SaldoInsuficienteException;
import com.restaurante.model.entity.Cliente;
import com.restaurante.model.entity.FundoConsumo;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.TransacaoFundo;
import com.restaurante.model.enums.TipoTransacaoFundo;
import com.restaurante.repository.ClienteRepository;
import com.restaurante.repository.FundoConsumoRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.TransacaoFundoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para FundoConsumoService
 *
 * Cobre crédito, débito, estorno e idempotência básica.
 * Testes de concorrência real ficam em ConcurrencyChaosTest.
 */
@ExtendWith(MockitoExtension.class)
class FundoConsumoServiceTest {

    @Mock
    private FundoConsumoRepository fundoConsumoRepository;

    @Mock
    private TransacaoFundoRepository transacaoFundoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @InjectMocks
    private FundoConsumoService fundoConsumoService;

    private Cliente cliente;
    private FundoConsumo fundo;
    private Pedido pedido;

    @BeforeEach
    void setUp() {
        cliente = Cliente.builder()
                .telefone("+244900000000")
                .build();
        cliente.setId(1L);

        fundo = FundoConsumo.builder()
                .cliente(cliente)
                .saldoAtual(new BigDecimal("50000.00"))
                .ativo(true)
                .build();
        fundo.setId(10L);

        pedido = Pedido.builder()
                .numero("PED-FUNDO-001")
                .total(new BigDecimal("25000.00"))
                .build();
        pedido.setId(100L);
    }

    @Test
    void deveCreditarFundoComSucesso() {
        when(clienteRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
        when(fundoConsumoRepository.existsByClienteIdAndAtivoTrue(cliente.getId())).thenReturn(false);
        when(fundoConsumoRepository.save(any(FundoConsumo.class))).thenReturn(fundo);

        fundoConsumoService.criarFundo(cliente.getId());

        when(fundoConsumoRepository.findByClienteIdAndAtivoTrue(cliente.getId())).thenReturn(Optional.of(fundo));
        when(fundoConsumoRepository.save(any(FundoConsumo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transacaoFundoRepository.save(any(TransacaoFundo.class))).thenAnswer(invocation -> {
            TransacaoFundo t = invocation.getArgument(0);
            t.setId(1L);
            return t;
        });

        var transacao = fundoConsumoService.recarregar(cliente.getId(), new BigDecimal("50000.00"), "Carga inicial");

        assertNotNull(transacao.getId());
        assertEquals(TipoTransacaoFundo.CREDITO, transacao.getTipo());
        assertEquals(new BigDecimal("50000.00"), transacao.getValor());
        assertEquals(new BigDecimal("100000.00"), transacao.getSaldoNovo());
    }

    @Test
    void deveDebitarPedidoComSaldoSuficienteEMarcarTransacaoDebito() {
        when(fundoConsumoRepository.findByClienteIdAndAtivoTrue(cliente.getId())).thenReturn(Optional.of(fundo));
        when(pedidoRepository.findById(pedido.getId())).thenReturn(Optional.of(pedido));
        when(transacaoFundoRepository.findByPedidoIdAndTipo(pedido.getId(), TipoTransacaoFundo.DEBITO))
                .thenReturn(Optional.empty());
        when(fundoConsumoRepository.save(any(FundoConsumo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transacaoFundoRepository.save(any(TransacaoFundo.class))).thenAnswer(invocation -> {
            TransacaoFundo t = invocation.getArgument(0);
            t.setId(2L);
            return t;
        });

        var transacao = fundoConsumoService.debitar(cliente.getId(), pedido.getId(), pedido.getTotal());

        assertNotNull(transacao.getId());
        assertEquals(TipoTransacaoFundo.DEBITO, transacao.getTipo());
        assertEquals(pedido.getTotal(), transacao.getValor());
        assertEquals(new BigDecimal("25000.00"), transacao.getSaldoNovo());
    }

    @Test
    void deveLancarSaldoInsuficienteAoDebitar() {
        fundo.setSaldoAtual(new BigDecimal("10000.00"));
        when(fundoConsumoRepository.findByClienteIdAndAtivoTrue(cliente.getId())).thenReturn(Optional.of(fundo));

        assertThrows(SaldoInsuficienteException.class,
                () -> fundoConsumoService.debitar(cliente.getId(), pedido.getId(), new BigDecimal("25000.00")));
    }

    @Test
    void debitoDeveSerIdempotentePorPedido() {
        when(fundoConsumoRepository.findByClienteIdAndAtivoTrue(cliente.getId())).thenReturn(Optional.of(fundo));
        when(pedidoRepository.findById(pedido.getId())).thenReturn(Optional.of(pedido));

        TransacaoFundo existente = TransacaoFundo.builder()
                .fundoConsumo(fundo)
                .pedido(pedido)
                .tipo(TipoTransacaoFundo.DEBITO)
                .valor(pedido.getTotal())
                .saldoAnterior(new BigDecimal("50000.00"))
                .saldoNovo(new BigDecimal("25000.00"))
                .build();
        existente.setId(5L);

        when(transacaoFundoRepository.findByPedidoIdAndTipo(pedido.getId(), TipoTransacaoFundo.DEBITO))
                .thenReturn(Optional.of(existente));

        var transacao = fundoConsumoService.debitar(cliente.getId(), pedido.getId(), pedido.getTotal());

        assertSame(existente, transacao);
        verify(fundoConsumoRepository, never()).save(any(FundoConsumo.class));
        verify(transacaoFundoRepository, never()).save(any(TransacaoFundo.class));
    }

    @Test
    void estornoDeveRetornarSaldoESerIdempotente() {
        // Débito original
        TransacaoFundo debito = TransacaoFundo.builder()
                .fundoConsumo(fundo)
                .pedido(pedido)
                .tipo(TipoTransacaoFundo.DEBITO)
                .valor(pedido.getTotal())
                .saldoAnterior(new BigDecimal("50000.00"))
                .saldoNovo(new BigDecimal("25000.00"))
                .build();
        debito.setId(6L);

        when(transacaoFundoRepository.findByPedidoIdAndTipo(pedido.getId(), TipoTransacaoFundo.DEBITO))
                .thenReturn(Optional.of(debito));
        TransacaoFundo estornoExistente = TransacaoFundo.builder()
                .fundoConsumo(fundo)
                .pedido(pedido)
                .tipo(TipoTransacaoFundo.ESTORNO)
                .valor(pedido.getTotal())
                .saldoAnterior(new BigDecimal("25000.00"))
                .saldoNovo(new BigDecimal("50000.00"))
                .build();
        estornoExistente.setId(7L);

        when(transacaoFundoRepository.findByPedidoIdAndTipo(pedido.getId(), TipoTransacaoFundo.ESTORNO))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(estornoExistente));

        when(fundoConsumoRepository.save(any(FundoConsumo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pedidoRepository.findById(pedido.getId())).thenReturn(Optional.of(pedido));
        when(transacaoFundoRepository.save(any(TransacaoFundo.class))).thenAnswer(invocation -> {
            TransacaoFundo t = invocation.getArgument(0);
            t.setId(7L);
            return t;
        });

        // Primeiro estorno: efetivo
        var estorno1 = fundoConsumoService.estornar(pedido.getId());
        assertEquals(TipoTransacaoFundo.ESTORNO, estorno1.getTipo());
        assertEquals(new BigDecimal("50000.00"), estorno1.getSaldoNovo());

        // Segundo estorno: deve ser idempotente e apenas retornar existente
        var estorno2 = fundoConsumoService.estornar(pedido.getId());
        assertSame(estorno1, estorno2);
    }

    @Test
    void naoDevePermitirRecarregarFundoInativo() {
        fundo.setAtivo(false);
        when(fundoConsumoRepository.findByClienteIdAndAtivoTrue(cliente.getId())).thenReturn(Optional.of(fundo));

        assertThrows(BusinessException.class,
                () -> fundoConsumoService.recarregar(cliente.getId(), new BigDecimal("100.00"), "recarregar"));
    }
}
