package com.restaurante.financeiro.service;

import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.StatusReconciliacaoAppyPay;
import com.restaurante.financeiro.polling.PagamentoConfirmacaoService;
import com.restaurante.financeiro.repository.PagamentoEventLogRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.financeiro.enums.TipoPagamentoFinanceiro;
import com.restaurante.service.PedidoPagamentoPolicy;
import com.restaurante.financeiro.reconciliation.service.PagamentoReconciliationCaseService;
import com.restaurante.store.service.StorePaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppyPayReconciliationProcessorTest {

    @Mock PagamentoGatewayRepository pagamentoRepository;
    @Mock PagamentoGatewayService pagamentoGatewayService;
    @Mock PagamentoConfirmacaoService pagamentoConfirmacaoService;
    @Mock StorePaymentService storePaymentService;
    @Mock PagamentoEventLogRepository eventLogRepository;
    @Mock PedidoPagamentoPolicy pedidoPagamentoPolicy;
    @Mock PagamentoReconciliationCaseService reconciliationCaseService;

    private AppyPayReconciliationProcessor processor;
    private Pagamento pagamento;

    @BeforeEach
    void setUp() {
        processor = new AppyPayReconciliationProcessor(pagamentoRepository, pagamentoGatewayService,
                pagamentoConfirmacaoService, storePaymentService, eventLogRepository, pedidoPagamentoPolicy,
                reconciliationCaseService);
        pagamento = new Pagamento();
        pagamento.setId(8L);
        pagamento.setStatus(StatusPagamentoGateway.PENDENTE);
        pagamento.setTipoPagamento(TipoPagamentoFinanceiro.POS_PAGO);
        pagamento.setPedido(new Pedido());
        lenient().when(pagamentoRepository.findForUpdateById(8L)).thenReturn(Optional.of(pagamento));
    }

    @Test
    void mesmaRespostaPendenteNaoCriaEventos() {
        processar(StatusPagamentoGateway.PENDENTE, "PENDING", "hash-pendente");
        processar(StatusPagamentoGateway.PENDENTE, "PENDING", "hash-pendente");

        verifyNoInteractions(eventLogRepository, pagamentoConfirmacaoService,
                pagamentoGatewayService, storePaymentService);
        assertThat(pagamento.getReconciliationStatus())
                .isEqualTo(StatusReconciliacaoAppyPay.AGUARDANDO_MUDANCA_REMOTA);
        assertThat(pagamento.getReconciliationNextAttemptAt()).isNotNull();
    }

    @Test
    void mesmaConfirmacaoRemotaConfirmaSomenteUmaVezMesmoAposRestart() {
        processar(StatusPagamentoGateway.CONFIRMADO, "CONFIRMED", "hash-confirmado");

        AppyPayReconciliationProcessor aposRestart = new AppyPayReconciliationProcessor(
                pagamentoRepository, pagamentoGatewayService, pagamentoConfirmacaoService,
                storePaymentService, eventLogRepository, pedidoPagamentoPolicy, reconciliationCaseService);
        aposRestart.processar(8L, StatusPagamentoGateway.CONFIRMADO, "CONFIRMED", "{}", "hash-confirmado");

        verify(pagamentoConfirmacaoService, times(1)).confirmarPosPagoPorGateway(
                eq(8L), eq("APPYPAY_RECONCILIACAO"), eq("SYSTEM"), isNull(), isNull());
        assertThat(pagamento.getReconciliationStatus()).isEqualTo(StatusReconciliacaoAppyPay.CONCLUIDO);
    }

    @Test
    void pedidoNaoAceiteBloqueiaSemEventoFalsoENaoRepete() {
        doThrow(new IllegalStateException("pagamento só pode ser confirmado após aceite do pedido"))
                .when(pedidoPagamentoPolicy).assertPodeConfirmarPagamento(any(), any());

        var primeira = processar(StatusPagamentoGateway.CONFIRMADO, "CONFIRMED", "hash-bloqueado");
        var segunda = processar(StatusPagamentoGateway.CONFIRMADO, "CONFIRMED", "hash-bloqueado");

        assertThat(primeira).isEqualTo(AppyPayReconciliationService.ResultadoReconcilicao.BLOQUEADO);
        assertThat(segunda).isEqualTo(AppyPayReconciliationService.ResultadoReconcilicao.BLOQUEADO);
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoGateway.PENDENTE);
        assertThat(pagamento.getReconciliationStatus()).isEqualTo(StatusReconciliacaoAppyPay.BLOQUEADO_DOMINIO);
        assertThat(pagamento.getReconciliationLastError()).contains("após aceite");
        verifyNoInteractions(eventLogRepository, pagamentoConfirmacaoService);
        verify(pedidoPagamentoPolicy, times(1)).assertPodeConfirmarPagamento(any(), any());
        verify(reconciliationCaseService, times(1)).materialize(eq(pagamento), any());
    }

    @Test
    void pedidoAceiteConvergeUmaVez() {
        doAnswer(invocation -> {
            pagamento.confirmar();
            return true;
        }).when(pagamentoConfirmacaoService).confirmarPosPagoPorGateway(
                eq(8L), anyString(), anyString(), isNull(), isNull());

        var resultado = processar(StatusPagamentoGateway.CONFIRMADO, "CONFIRMED", "hash-ok");

        assertThat(resultado).isEqualTo(AppyPayReconciliationService.ResultadoReconcilicao.CONFIRMADO);
        verify(pagamentoConfirmacaoService).confirmarPosPagoPorGateway(
                eq(8L), eq("APPYPAY_RECONCILIACAO"), eq("SYSTEM"), isNull(), isNull());
        assertThat(pagamento.getReconciliationStatus()).isEqualTo(StatusReconciliacaoAppyPay.CONCLUIDO);
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamentoGateway.CONFIRMADO);
        assertThat(pagamento.getConfirmedAt()).isNotNull();
    }

    @Test
    void falhaRemotaTemBackoffSemEventoDeConfirmacao() {
        processor.registrarFalhaTemporaria(8L, "timeout remoto");

        assertThat(pagamento.getReconciliationStatus()).isEqualTo(StatusReconciliacaoAppyPay.RETRY_AGENDADO);
        assertThat(pagamento.getReconciliationNextAttemptAt()).isAfter(pagamento.getReconciliationLastAttemptAt());
        assertThat(pagamento.getReconciliationLastError()).isEqualTo("timeout remoto");
        verifyNoInteractions(eventLogRepository, pagamentoConfirmacaoService);
    }

    @Test
    void concorrenciaUsaLockPessimistaPersistente() throws Exception {
        Lock lock = PagamentoGatewayRepository.class
                .getMethod("findForUpdateById", Long.class)
                .getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    private AppyPayReconciliationService.ResultadoReconcilicao processar(
            StatusPagamentoGateway status, String raw, String hash) {
        return processor.processar(8L, status, raw, "{}", hash);
    }
}
