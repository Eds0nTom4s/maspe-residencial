package com.restaurante.financeiro.service;

import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.StatusReconciliacaoAppyPay;
import com.restaurante.financeiro.enums.TipoEventoFinanceiro;
import com.restaurante.financeiro.polling.PagamentoConfirmacaoService;
import com.restaurante.financeiro.repository.PagamentoEventLogRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.PagamentoEventLog;
import com.restaurante.service.PedidoPagamentoPolicy;
import com.restaurante.financeiro.reconciliation.model.ReconciliationCaseOrigin;
import com.restaurante.financeiro.reconciliation.service.PagamentoReconciliationCaseService;
import com.restaurante.store.service.StorePaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AppyPayReconciliationProcessor {

    private static final int MAX_BACKOFF_MINUTES = 24 * 60;

    private final PagamentoGatewayRepository pagamentoRepository;
    private final PagamentoGatewayService pagamentoGatewayService;
    private final PagamentoConfirmacaoService pagamentoConfirmacaoService;
    private final StorePaymentService storePaymentService;
    private final PagamentoEventLogRepository eventLogRepository;
    private final PedidoPagamentoPolicy pedidoPagamentoPolicy;
    private final PagamentoReconciliationCaseService reconciliationCaseService;

    @Transactional
    public AppyPayReconciliationService.ResultadoReconcilicao processar(
            Long pagamentoId,
            StatusPagamentoGateway remoteStatus,
            String remoteStatusRaw,
            String serializedResponse,
            String responseHash) {
        Pagamento pagamento = pagamentoRepository.findForUpdateById(pagamentoId)
                .orElseThrow(() -> new IllegalArgumentException("Pagamento não encontrado: " + pagamentoId));

        if (pagamento.getStatus() != StatusPagamentoGateway.PENDENTE) {
            return AppyPayReconciliationService.ResultadoReconcilicao.AGUARDANDO;
        }

        LocalDateTime now = LocalDateTime.now();
        boolean mesmaResposta = Objects.equals(responseHash, pagamento.getReconciliationLastResponseHash())
                && Objects.equals(remoteStatusRaw, pagamento.getReconciliationLastRemoteStatus());

        pagamento.setGatewayResponse(serializedResponse);
        pagamento.setReconciliationLastResponseHash(responseHash);
        pagamento.setReconciliationLastRemoteStatus(remoteStatusRaw);
        pagamento.setReconciliationLastAttemptAt(now);
        pagamento.setReconciliationAttempts(pagamento.getReconciliationAttempts() + 1);

        if (mesmaResposta && pagamento.getReconciliationStatus() == StatusReconciliacaoAppyPay.BLOQUEADO_DOMINIO) {
            return AppyPayReconciliationService.ResultadoReconcilicao.BLOQUEADO;
        }
        if (mesmaResposta && pagamento.getReconciliationStatus() == StatusReconciliacaoAppyPay.CONCLUIDO) {
            return AppyPayReconciliationService.ResultadoReconcilicao.AGUARDANDO;
        }

        if (remoteStatus == StatusPagamentoGateway.CONFIRMADO) {
            if (pagamento.isPosPago()) {
                try {
                    pedidoPagamentoPolicy.assertPodeConfirmarPagamento(
                            pagamento.getPedido(), PedidoPagamentoPolicy.PaymentFlow.GATEWAY_CONFIRMATION);
                } catch (RuntimeException ex) {
                    bloquearPorDominio(pagamento, now, ex);
                    return AppyPayReconciliationService.ResultadoReconcilicao.BLOQUEADO;
                }
            }

            confirmarNoDominio(pagamento);
            pagamento.setReconciliationStatus(StatusReconciliacaoAppyPay.CONCLUIDO);
            pagamento.setReconciliationNextAttemptAt(null);
            pagamento.setReconciliationLastError(null);
            pagamentoRepository.save(pagamento);
            return AppyPayReconciliationService.ResultadoReconcilicao.CONFIRMADO;
        }

        if (remoteStatus == StatusPagamentoGateway.FALHOU) {
            pagamento.marcarComoFalho("Gateway via reconciliação: " + remoteStatusRaw);
            pagamento.setReconciliationStatus(StatusReconciliacaoAppyPay.CONCLUIDO);
            pagamento.setReconciliationNextAttemptAt(null);
            pagamentoRepository.save(pagamento);
            registrarEvento(TipoEventoFinanceiro.PAGAMENTO_FALHOU, pagamento,
                    "Pagamento marcado como falho via reconciliação AppyPay");
            return AppyPayReconciliationService.ResultadoReconcilicao.FALHOU;
        }

        pagamento.setReconciliationStatus(StatusReconciliacaoAppyPay.AGUARDANDO_MUDANCA_REMOTA);
        pagamento.setReconciliationNextAttemptAt(now.plusMinutes(backoffMinutes(pagamento.getReconciliationAttempts())));
        pagamento.setReconciliationLastError(null);
        pagamentoRepository.save(pagamento);
        return AppyPayReconciliationService.ResultadoReconcilicao.AGUARDANDO;
    }

    @Transactional
    public void registrarFalhaTemporaria(Long pagamentoId, String erro) {
        Pagamento pagamento = pagamentoRepository.findForUpdateById(pagamentoId).orElse(null);
        if (pagamento == null || pagamento.getStatus() != StatusPagamentoGateway.PENDENTE) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        pagamento.setReconciliationLastAttemptAt(now);
        pagamento.setReconciliationAttempts(pagamento.getReconciliationAttempts() + 1);
        pagamento.setReconciliationStatus(StatusReconciliacaoAppyPay.RETRY_AGENDADO);
        pagamento.setReconciliationLastError(limit(erro, 4000));
        pagamento.setReconciliationNextAttemptAt(now.plusMinutes(backoffMinutes(pagamento.getReconciliationAttempts())));
        pagamentoRepository.save(pagamento);
    }

    private void confirmarNoDominio(Pagamento pagamento) {
        if (pagamento.isPrePago()) {
            pagamentoGatewayService.confirmarPagamentoRecargaFundo(
                    pagamento.getId(), "APPYPAY_RECONCILIACAO", "SYSTEM", null);
        } else if (pagamento.isPosPago()) {
            pagamentoConfirmacaoService.confirmarPosPagoPorGateway(
                    pagamento.getId(), "APPYPAY_RECONCILIACAO", "SYSTEM", null, null);
        } else if (pagamento.isStorePedido()) {
            storePaymentService.confirmStorePayment(pagamento);
            registrarEvento(TipoEventoFinanceiro.CONFIRMACAO_PAGAMENTO, pagamento,
                    "Pagamento confirmado por APPYPAY_RECONCILIACAO");
        } else {
            throw new IllegalStateException("Tipo de pagamento não suportado: " + pagamento.getTipoPagamento());
        }
    }

    private void bloquearPorDominio(Pagamento pagamento, LocalDateTime now, RuntimeException ex) {
        pagamento.setReconciliationStatus(StatusReconciliacaoAppyPay.BLOQUEADO_DOMINIO);
        pagamento.setReconciliationNextAttemptAt(null);
        pagamento.setReconciliationLastError(limit(ex.getMessage(), 4000));
        pagamentoRepository.save(pagamento);
        reconciliationCaseService.materialize(pagamento, ReconciliationCaseOrigin.AUTOMATIC_BLOCK);
    }

    private void registrarEvento(TipoEventoFinanceiro tipo, Pagamento pagamento, String motivo) {
        eventLogRepository.save(PagamentoEventLog.builder()
                .tipoEvento(tipo)
                .pagamento(pagamento)
                .pedido(pagamento.getPedido())
                .usuario("APPYPAY_RECONCILIACAO")
                .role("SYSTEM")
                .motivo(motivo)
                .build());
    }

    static long backoffMinutes(int attempts) {
        int exponent = Math.min(Math.max(0, attempts - 1), 10);
        return Math.min(MAX_BACKOFF_MINUTES, 15L * (1L << exponent));
    }

    private static String limit(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }
}
