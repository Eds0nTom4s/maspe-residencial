package com.restaurante.financeiro.polling;

import com.restaurante.config.PaymentPollingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "consuma.payment.polling", name = "enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class PagamentoGatewayPollingJob {

    private final PaymentPollingProperties props;
    private final PagamentoGatewayPollingService pollingService;

    @Scheduled(fixedDelayString = "${consuma.payment.polling.fixed-delay-ms:60000}")
    public void run() {
        if (!props.isEnabled()) return;
        try {
            pollingService.pollBatch();
        } catch (Exception e) {
            log.warn("Job de polling de pagamentos falhou: {}", e.getMessage());
        }
    }
}

