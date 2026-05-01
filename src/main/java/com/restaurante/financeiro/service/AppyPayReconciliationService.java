package com.restaurante.financeiro.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.TipoEventoFinanceiro;
import com.restaurante.financeiro.gateway.appypay.AppyPayClient;
import com.restaurante.financeiro.gateway.appypay.AppyPayProperties;
import com.restaurante.financeiro.gateway.appypay.AppyPayStatusMapper;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeResponse;
import com.restaurante.financeiro.repository.PagamentoEventLogRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.PagamentoEventLog;
import com.restaurante.store.service.StorePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppyPayReconciliationService {

    private final PagamentoGatewayRepository pagamentoRepository;
    private final PagamentoEventLogRepository eventLogRepository;
    private final PagamentoGatewayService pagamentoGatewayService;
    private final StorePaymentService storePaymentService;
    private final AppyPayClient appyPayClient;
    private final AppyPayProperties properties;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "${app.payment.appypay.reconciliation.cron:0 */15 * * * *}")
    public void reconciliarPagamentosPendentes() {
        if (!properties.getReconciliation().isEnabled()) {
            log.debug("[APPYPAY_RECONCILIACAO] Rotina desabilitada por configuração");
            return;
        }

        int batchSize = Math.max(1, properties.getReconciliation().getBatchSize());
        int minAge = Math.max(1, properties.getReconciliation().getMinAgeMinutes());
        LocalDateTime createdBefore = LocalDateTime.now().minusMinutes(minAge);

        var pendentes = pagamentoRepository.findPagamentosParaReconciliacao(
                createdBefore,
                PageRequest.of(0, batchSize));

        if (pendentes.isEmpty()) {
            log.debug("[APPYPAY_RECONCILIACAO] Nenhum pagamento pendente para reconciliar");
            return;
        }

        int confirmados = 0;
        int falhados = 0;
        int aguardando = 0;
        int erros = 0;

        log.info("[APPYPAY_RECONCILIACAO] Iniciando reconciliação: total={}", pendentes.size());
        for (Pagamento pagamento : pendentes) {
            try {
                ResultadoReconcilicao resultado = reconciliarPagamento(pagamento.getId());
                switch (resultado) {
                    case CONFIRMADO -> confirmados++;
                    case FALHOU -> falhados++;
                    case AGUARDANDO -> aguardando++;
                }
            } catch (Exception e) {
                erros++;
                log.error("[APPYPAY_RECONCILIACAO] Erro ao reconciliar pagamento id={}: {}",
                        pagamento.getId(), e.getMessage(), e);
            }
        }

        log.info("[APPYPAY_RECONCILIACAO] Concluída: confirmados={}, falhados={}, aguardando={}, erros={}",
                confirmados, falhados, aguardando, erros);
    }

    @Transactional
    public ResultadoReconcilicao reconciliarPagamento(Long pagamentoId) {
        Pagamento pagamento = pagamentoRepository.findById(pagamentoId)
                .orElseThrow(() -> new IllegalArgumentException("Pagamento não encontrado: " + pagamentoId));

        if (pagamento.getStatus() != StatusPagamentoGateway.PENDENTE) {
            return ResultadoReconcilicao.AGUARDANDO;
        }
        if (pagamento.getGatewayChargeId() == null || pagamento.getGatewayChargeId().isBlank()) {
            return ResultadoReconcilicao.AGUARDANDO;
        }

        AppyPayChargeResponse response = appyPayClient.getCharge(pagamento.getGatewayChargeId());
        StatusPagamentoGateway status = AppyPayStatusMapper.toGatewayStatus(response != null ? response.getStatus() : null);

        pagamento.setGatewayResponse(serialize(response));
        pagamentoRepository.save(pagamento);

        if (status == StatusPagamentoGateway.CONFIRMADO) {
            log.warn("[APPYPAY_RECONCILIACAO] Pagamento confirmado no gateway e pendente localmente: id={}",
                    pagamento.getId());
            registrarEvento(TipoEventoFinanceiro.CONFIRMACAO_PAGAMENTO, pagamento,
                    "Pagamento reconciliado via consulta AppyPay");
            if (pagamento.isPrePago()) {
                pagamentoGatewayService.confirmarPagamentoRecargaFundo(
                        pagamento.getId(),
                        "APPYPAY_RECONCILIACAO",
                        "SYSTEM",
                        null);
            } else {
                storePaymentService.confirmStorePayment(pagamento);
            }
            return ResultadoReconcilicao.CONFIRMADO;
        }

        if (status == StatusPagamentoGateway.FALHOU) {
            pagamento.marcarComoFalho("Gateway via reconciliação: " + (response != null ? response.getStatus() : null));
            pagamentoRepository.save(pagamento);
            registrarEvento(TipoEventoFinanceiro.PAGAMENTO_FALHOU, pagamento,
                    "Pagamento marcado como falho via reconciliação AppyPay");
            return ResultadoReconcilicao.FALHOU;
        }

        return ResultadoReconcilicao.AGUARDANDO;
    }

    private void registrarEvento(TipoEventoFinanceiro tipo, Pagamento pagamento, String motivo) {
        eventLogRepository.save(PagamentoEventLog.builder()
                .tipoEvento(tipo)
                .pagamento(pagamento)
                .pedido(pagamento.getPedido())
                .usuario("APPYPAY_RECONCILIACAO")
                .role("SYSTEM")
                .ip(null)
                .motivo(motivo)
                .dadosAdicionais("{\"gatewayChargeId\":\"" + safe(pagamento.getGatewayChargeId())
                        + "\",\"externalReference\":\"" + safe(pagamento.getExternalReference()) + "\"}")
                .build());
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }

    public enum ResultadoReconcilicao {
        CONFIRMADO,
        FALHOU,
        AGUARDANDO
    }
}
