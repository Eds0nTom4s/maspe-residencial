package com.restaurante.financeiro.polling;

import com.restaurante.config.PaymentPollingProperties;
import com.restaurante.financeiro.enums.PagamentoPollingStatus;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.service.operacional.OperationalEventLogService;
import com.restaurante.service.metrics.NoOpPaymentPollingMetricsService;
import com.restaurante.service.metrics.PaymentPollingMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;

@Service
@Slf4j
public class PagamentoGatewayPollingService {

    private final PaymentPollingProperties props;
    private final PagamentoGatewayRepository pagamentoRepository;
    private final PaymentGatewayStatusPort statusPort;
    private final PagamentoConfirmacaoService confirmacaoService;
    private final OperationalEventLogService operationalEventLogService;
    private final PaymentPollingMetricsService metrics;

    public PagamentoGatewayPollingService(PaymentPollingProperties props,
                                         PagamentoGatewayRepository pagamentoRepository,
                                         PaymentGatewayStatusPort statusPort,
                                         PagamentoConfirmacaoService confirmacaoService,
                                         OperationalEventLogService operationalEventLogService,
                                         ObjectProvider<PaymentPollingMetricsService> metricsProvider) {
        this.props = props;
        this.pagamentoRepository = pagamentoRepository;
        this.statusPort = statusPort;
        this.confirmacaoService = confirmacaoService;
        this.operationalEventLogService = operationalEventLogService;
        this.metrics = metricsProvider.getIfAvailable(NoOpPaymentPollingMetricsService::new);
    }

    public void pollBatch() {
        if (!props.isEnabled()) return;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime initialDelayThreshold = now.minusMinutes(Math.max(0, props.getInitialDelayMinutes()));
        LocalDateTime maxAgeThreshold = now.minusHours(Math.max(1, props.getMaxAgeHours()));

        List<Long> ids = pagamentoRepository.findEligibleIdsForPolling(
                now,
                initialDelayThreshold,
                maxAgeThreshold,
                props.getMaxAttempts(),
                props.getBatchSize()
        );
        for (Long id : ids) {
            try {
                pollPagamento(id);
            } catch (Exception e) {
                log.warn("Polling falhou para pagamentoId={}: {}", id, e.getMessage());
            }
        }
    }

    public void pollPagamento(Long pagamentoId) {
        if (pagamentoId == null) return;

        metrics.timePolling(() -> {
            Pagamento snapshot = markInProgress(pagamentoId);
            if (snapshot == null) return null;

            GatewayPaymentStatusResponse gateway;
            try {
                gateway = statusPort.consultarStatus(snapshot.getGatewayChargeId(), snapshot.getExternalReference());
            } catch (Exception e) {
                metrics.recordPollingFailed("GATEWAY_ERROR");
                recordGatewayError(pagamentoId, "GATEWAY_ERROR", e.getMessage());
                return null;
            }

            applyGatewayResult(pagamentoId, gateway);
            return null;
        });
    }

    @Transactional
    protected Pagamento markInProgress(Long pagamentoId) {
        Pagamento pagamento = pagamentoRepository.findForUpdateById(pagamentoId).orElse(null);
        if (pagamento == null) return null;

        if (!pagamento.isPollingEnabled()) return null;
        if (pagamento.getStatus() == null || pagamento.getStatus().isTerminal()) return null;
        if (pagamento.getStatus() != StatusPagamentoGateway.PENDENTE) return null;

        LocalDateTime now = LocalDateTime.now();
        if (pagamento.getCreatedAt() != null && now.isBefore(pagamento.getCreatedAt().plusMinutes(props.getInitialDelayMinutes()))) {
            return null;
        }
        if (pagamento.getCreatedAt() != null && now.isAfter(pagamento.getCreatedAt().plusHours(props.getMaxAgeHours()))) {
            pagamento.setPollingEnabled(false);
            pagamento.setPollingStatus(PagamentoPollingStatus.MAX_ATTEMPTS_REACHED);
            pagamentoRepository.save(pagamento);
            return null;
        }
        if (pagamento.getPollingAttempts() >= props.getMaxAttempts()) {
            pagamento.setPollingEnabled(false);
            pagamento.setPollingStatus(PagamentoPollingStatus.MAX_ATTEMPTS_REACHED);
            pagamentoRepository.save(pagamento);
            operationalEventLogService.logPagamentoEvent(
                    OperationalEventType.PAGAMENTO_POLLING_MAX_TENTATIVAS,
                    pagamento,
                    OperationalOrigem.SYSTEM,
                    "Polling max attempts",
                    Map.of("attempts", pagamento.getPollingAttempts()),
                    null,
                    null
            );
            return null;
        }
        if (pagamento.getNextPollingAttemptAt() != null && pagamento.getNextPollingAttemptAt().isAfter(now)) {
            return null;
        }

        pagamento.setPollingStatus(PagamentoPollingStatus.IN_PROGRESS);
        pagamento.setLastPollingAttemptAt(now);
        pagamento.setGatewayStatusLastCheckedAt(now);
        pagamento.setPollingAttempts(pagamento.getPollingAttempts() + 1);
        pagamentoRepository.save(pagamento);

        operationalEventLogService.logPagamentoEvent(
                OperationalEventType.PAGAMENTO_POLLING_TENTADO,
                pagamento,
                OperationalOrigem.GATEWAY,
                "Polling attempt",
                Map.of("attempt", pagamento.getPollingAttempts()),
                null,
                null
        );
        metrics.recordPollingAttempt("OK");

        return pagamento;
    }

    @Transactional
    protected void recordGatewayError(Long pagamentoId, String code, String message) {
        Pagamento pagamento = pagamentoRepository.findForUpdateById(pagamentoId).orElse(null);
        if (pagamento == null) return;
        if (pagamento.getStatus() == null || pagamento.getStatus().isTerminal()) return;

        pagamento.setPollingLastErrorCode(code);
        pagamento.setPollingLastErrorMessage(truncate(message, 1000));
        pagamento.setPollingStatus(PagamentoPollingStatus.ELIGIBLE);
        pagamento.setNextPollingAttemptAt(calcNextAttempt(pagamento.getPollingAttempts()));
        pagamentoRepository.save(pagamento);

        operationalEventLogService.logPagamentoEvent(
                OperationalEventType.PAGAMENTO_POLLING_FALHOU,
                pagamento,
                OperationalOrigem.GATEWAY,
                "Polling gateway error",
                Map.of("errorCode", code),
                null,
                null
        );
    }

    @Transactional
    protected void applyGatewayResult(Long pagamentoId, GatewayPaymentStatusResponse gateway) {
        Pagamento pagamento = pagamentoRepository.findForUpdateById(pagamentoId).orElse(null);
        if (pagamento == null) return;

        if (pagamento.getStatus() == null || pagamento.getStatus().isTerminal()) return;

        pagamento.setGatewayStatusLastCheckedAt(LocalDateTime.now());
        pagamento.setGatewayStatusRaw(gateway != null ? gateway.getRawPayload() : null);

        GatewayPaymentStatus st = gateway != null ? gateway.getStatus() : GatewayPaymentStatus.UNKNOWN;
        if (st == GatewayPaymentStatus.CONFIRMED) {
            try {
                boolean changed = confirmacaoService.confirmarPosPagoPorGateway(
                        pagamento.getId(),
                        "APPYPAY_POLLING",
                        "SYSTEM",
                        null,
                        gateway.getAmountCents()
                );
                pagamento.setPollingStatus(PagamentoPollingStatus.CONFIRMED_BY_POLLING);
                pagamento.setPollingEnabled(false);
                pagamentoRepository.save(pagamento);

                operationalEventLogService.logPagamentoEvent(
                        OperationalEventType.PAGAMENTO_CONFIRMADO_POR_POLLING,
                        pagamento,
                        OperationalOrigem.GATEWAY,
                        "Pagamento confirmado por polling",
                        Map.of("changed", changed),
                        null,
                        null
                );
                metrics.recordPollingConfirmed();
            } catch (IllegalStateException divergence) {
                pagamento.setPollingLastErrorCode("AMOUNT_MISMATCH");
                pagamento.setPollingLastErrorMessage(truncate(divergence.getMessage(), 1000));
                pagamento.setPollingStatus(PagamentoPollingStatus.FAILED);
                pagamento.setPollingEnabled(false);
                pagamentoRepository.save(pagamento);

                operationalEventLogService.logPagamentoEvent(
                        OperationalEventType.PAGAMENTO_POLLING_DIVERGENCIA_VALOR,
                        pagamento,
                        OperationalOrigem.GATEWAY,
                        "Divergência de valor no polling",
                        Map.of("expected", pagamento.getAmount(), "gatewayAmountCents", gateway.getAmountCents()),
                        null,
                        null
                );
                metrics.recordPollingFailed("AMOUNT_MISMATCH");
            }
            return;
        }

        if (st == GatewayPaymentStatus.PENDING) {
            pagamento.setPollingStatus(PagamentoPollingStatus.ELIGIBLE);
            pagamento.setNextPollingAttemptAt(calcNextAttempt(pagamento.getPollingAttempts()));
            pagamentoRepository.save(pagamento);
            operationalEventLogService.logPagamentoEvent(
                    OperationalEventType.PAGAMENTO_POLLING_PENDENTE,
                    pagamento,
                    OperationalOrigem.GATEWAY,
                    "Pagamento ainda pendente no gateway",
                    Map.of("attempt", pagamento.getPollingAttempts()),
                    null,
                    null
            );
            metrics.recordPollingPending();
            return;
        }

        if (st == GatewayPaymentStatus.EXPIRED || st == GatewayPaymentStatus.CANCELLED || st == GatewayPaymentStatus.FAILED) {
            pagamento.marcarComoFalho("Gateway: " + (gateway != null ? gateway.getRawStatus() : st.name()));
            pagamento.setPollingStatus(st == GatewayPaymentStatus.EXPIRED ? PagamentoPollingStatus.EXPIRED : PagamentoPollingStatus.FAILED);
            pagamento.setPollingEnabled(false);
            pagamentoRepository.save(pagamento);
            operationalEventLogService.logPagamentoEvent(
                    st == GatewayPaymentStatus.EXPIRED ? OperationalEventType.PAGAMENTO_POLLING_EXPIRADO : OperationalEventType.PAGAMENTO_POLLING_FALHOU,
                    pagamento,
                    OperationalOrigem.GATEWAY,
                    "Pagamento terminal no gateway: " + st,
                    Map.of("status", st.name()),
                    null,
                    null
            );
            if (st == GatewayPaymentStatus.EXPIRED) metrics.recordPollingExpired();
            else metrics.recordPollingFailed(st.name());
            return;
        }

        // UNKNOWN -> tenta novamente
        pagamento.setPollingLastErrorCode("UNKNOWN_STATUS");
        pagamento.setPollingLastErrorMessage(truncate(gateway != null ? gateway.getRawStatus() : "null", 200));
        pagamento.setPollingStatus(PagamentoPollingStatus.ELIGIBLE);
        pagamento.setNextPollingAttemptAt(calcNextAttempt(pagamento.getPollingAttempts()));
        pagamentoRepository.save(pagamento);
    }

    private LocalDateTime calcNextAttempt(int attempts) {
        int baseMinutes = 1;
        long delay = (long) (baseMinutes * Math.pow(Math.max(1, props.getBackoffMultiplier()), Math.max(0, attempts - 1)));
        delay = Math.min(delay, props.getMaxBackoffMinutes());
        return LocalDateTime.now().plusMinutes(delay);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }
}
