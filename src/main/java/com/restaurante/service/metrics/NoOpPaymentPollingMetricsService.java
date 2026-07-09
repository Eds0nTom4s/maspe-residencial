package com.restaurante.service.metrics;

import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class NoOpPaymentPollingMetricsService implements PaymentPollingMetricsService {

    @Override
    public void recordPollingAttempt(String result) { }

    @Override
    public void recordPollingConfirmed() { }

    @Override
    public void recordPollingPending() { }

    @Override
    public void recordPollingFailed(String result) { }

    @Override
    public void recordPollingExpired() { }

    @Override
    public <T> T timePolling(Supplier<T> supplier) {
        return supplier.get();
    }
}

