package com.restaurante.service.metrics;

import java.util.function.Supplier;

public interface PaymentPollingMetricsService {

    void recordPollingAttempt(String result);

    void recordPollingConfirmed();

    void recordPollingPending();

    void recordPollingFailed(String result);

    void recordPollingExpired();

    <T> T timePolling(Supplier<T> supplier);
}

