package com.restaurante.financeiro.paymentmethod.job;

import com.restaurante.config.PaymentPolicyRolloutProperties;
import com.restaurante.financeiro.paymentmethod.service.PaymentMethodPolicyRolloutWorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "consuma.financeiro.payment-policy-rollout", name = "worker-enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class PaymentMethodPolicyRolloutJob {

    private final PaymentPolicyRolloutProperties props;
    private final PaymentMethodPolicyRolloutWorkerService workerService;

    @Scheduled(fixedDelayString = "${consuma.financeiro.payment-policy-rollout.fixed-delay-ms:30000}")
    public void run() {
        if (!props.isWorkerEnabled()) return;
        try {
            workerService.processOneEligibleRollout();
        } catch (Exception e) {
            log.warn("Job de rollout async falhou: {}", e.getMessage());
        }
    }
}

