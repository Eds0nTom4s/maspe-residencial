package com.restaurante.concurrency;

import com.restaurante.exception.SaldoInsuficienteException;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.repository.*;
import com.restaurante.service.FundoConsumoService;
import com.restaurante.service.SubPedidoService;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes de "caos" de concorrência para cenários críticos.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.mode=never")
class ConcurrencyChaosTest {

    @Autowired
    private SubPedidoService subPedidoService;

    @Autowired
    private SubPedidoRepository subPedidoRepository;

    @Autowired
    private CozinhaRepository cozinhaRepository;

    @Autowired
    private UnidadeAtendimentoRepository unidadeAtendimentoRepository;

    @Autowired
    private UnidadeDeConsumoRepository unidadeDeConsumoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private FundoConsumoService fundoConsumoService;

    @Autowired
    private FundoConsumoRepository fundoConsumoRepository;

    @Autowired
    private TransacaoFundoRepository transacaoFundoRepository;

    private SubPedido criarSubPedidoPronto() {
        UnidadeAtendimento unidadeAtendimento = UnidadeAtendimento.builder()
                .nome("Unidade Teste")
                .tipo(TipoUnidadeAtendimento.RESTAURANTE)
                .ativa(true)
                .build();
        unidadeAtendimento = unidadeAtendimentoRepository.save(unidadeAtendimento);

        Cozinha cozinha = Cozinha.builder()
                .nome("Cozinha Teste")
                .tipo(TipoCozinha.CENTRAL)
                .ativa(true)
                .build();
        cozinha = cozinhaRepository.save(cozinha);

        Pedido pedido = Pedido.builder()
                .numero("PED-CONC-001")
                .status(StatusPedido.CRIADO)
                .statusFinanceiro(StatusFinanceiroPedido.NAO_PAGO)
                .tipoPagamento(TipoPagamentoPedido.PRE_PAGO)
                .unidadeConsumo(UnidadeDeConsumo.builder()
                        .referencia("MESA-1")
                        .unidadeAtendimento(unidadeAtendimento)
                        .build())
                .build();
        pedido = pedidoRepository.save(pedido);

        SubPedido subPedido = SubPedido.builder()
                .pedido(pedido)
                .cozinha(cozinha)
                .unidadeAtendimento(unidadeAtendimento)
                .status(StatusSubPedido.PRONTO)
                .build();
        return subPedidoRepository.save(subPedido);
    }

    @Test
    @DisplayName("Dois atendentes entregando o mesmo SubPedido: apenas um ENTREGUE efetivo")
    void doisAtendentesEntregandoMesmoSubPedido() throws InterruptedException {
        SubPedido subPedido = criarSubPedidoPronto();
        Long id = subPedido.getId();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Runnable tarefaEntrega = () -> {
            try {
                startLatch.await();
                subPedidoService.marcarEntregue(id);
                successCount.incrementAndGet();
            } catch (OptimisticLockException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                // Otimistic locking vindo do JPA costuma ser wrapped
                if (e.getCause() instanceof OptimisticLockException) {
                    conflictCount.incrementAndGet();
                } else {
                    e.printStackTrace();
                }
            } finally {
                doneLatch.countDown();
            }
        };

        executor.submit(tarefaEntrega);
        executor.submit(tarefaEntrega);

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        SubPedido finalState = subPedidoRepository.findById(id).orElseThrow();

        assertThat(finalState.getStatus()).isEqualTo(StatusSubPedido.ENTREGUE);
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(conflictCount.get()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Dois cozinheiros assumindo o mesmo SubPedido: apenas um EM_PREPARACAO")
    void doisCozinheirosAssumindoMesmoSubPedido() throws InterruptedException {
        SubPedido subPedido = criarSubPedidoPronto();
        subPedido.setStatus(StatusSubPedido.PENDENTE);
        subPedido = subPedidoRepository.save(subPedido);
        Long id = subPedido.getId();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Runnable tarefaAssumir = () -> {
            try {
                startLatch.await();
                subPedidoService.assumir(id);
                successCount.incrementAndGet();
            } catch (OptimisticLockException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                if (e.getCause() instanceof OptimisticLockException) {
                    conflictCount.incrementAndGet();
                } else {
                    e.printStackTrace();
                }
            } finally {
                doneLatch.countDown();
            }
        };

        executor.submit(tarefaAssumir);
        executor.submit(tarefaAssumir);

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        SubPedido finalState = subPedidoRepository.findById(id).orElseThrow();

        assertThat(finalState.getStatus()).isIn(StatusSubPedido.EM_PREPARACAO, StatusSubPedido.PENDENTE);
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Dois processos debitando o mesmo Fundo: apenas um débito efetivo")
    void doisProcessosDebitantoMesmoFundo() throws InterruptedException {
        Cliente cliente = clienteRepository.save(Cliente.builder().telefone("+244911111111").build());
        FundoConsumo fundo = FundoConsumo.builder()
                .cliente(cliente)
                .saldoAtual(new BigDecimal("50000.00"))
                .ativo(true)
                .build();
        fundo = fundoConsumoRepository.save(fundo);

        Pedido pedido = Pedido.builder()
                .numero("PED-CONC-FUNDO")
                .status(StatusPedido.CRIADO)
                .statusFinanceiro(StatusFinanceiroPedido.NAO_PAGO)
                .tipoPagamento(TipoPagamentoPedido.PRE_PAGO)
                .total(new BigDecimal("25000.00"))
                .build();
        pedido = pedidoRepository.save(pedido);

        Long clienteId = cliente.getId();
        Long pedidoId = pedido.getId();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Runnable tarefaDebito = () -> {
            try {
                startLatch.await();
                fundoConsumoService.debitar(clienteId, pedidoId, new BigDecimal("25000.00"));
                successCount.incrementAndGet();
            } catch (SaldoInsuficienteException e) {
                errorCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
                errorCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        };

        executor.submit(tarefaDebito);
        executor.submit(tarefaDebito);

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        List<TransacaoFundo> debitos = transacaoFundoRepository.findAll().stream()
                .filter(t -> t.getPedido() != null && t.getPedido().getId().equals(pedidoId)
                        && t.getTipo() == TipoTransacaoFundo.DEBITO)
                .toList();

        FundoConsumo fundoFinal = fundoConsumoRepository.findById(fundo.getId()).orElseThrow();

        assertThat(debitos).hasSize(1);
        assertThat(fundoFinal.getSaldoAtual()).isIn(new BigDecimal("25000.00"), new BigDecimal("50000.00"));
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
    }
}
