package com.restaurante.financeiro.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.gateway.appypay.AppyPayClient;
import com.restaurante.financeiro.gateway.appypay.AppyPayProperties;
import com.restaurante.financeiro.gateway.appypay.AppyPayStatusMapper;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeResponse;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Pagamento;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppyPayReconciliationService {

    private final PagamentoGatewayRepository pagamentoRepository;
    private final AppyPayReconciliationProcessor processor;
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
                LocalDateTime.now(),
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
                    case AGUARDANDO, BLOQUEADO -> aguardando++;
                }
            } catch (Exception e) {
                erros++;
                processor.registrarFalhaTemporaria(pagamento.getId(), e.getMessage());
                log.error("[APPYPAY_RECONCILIACAO] Erro ao reconciliar pagamento id={}: {}",
                        pagamento.getId(), e.getMessage(), e);
            }
        }

        log.info("[APPYPAY_RECONCILIACAO] Concluída: confirmados={}, falhados={}, aguardando={}, erros={}",
                confirmados, falhados, aguardando, erros);
    }

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
        String serialized = serialize(response);
        return processor.processar(pagamentoId, status,
                response != null ? response.getStatus() : null,
                serialized, sha256(serialized));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    public enum ResultadoReconcilicao {
        CONFIRMADO,
        FALHOU,
        AGUARDANDO,
        BLOQUEADO
    }
}
