package com.restaurante.config;

import com.restaurante.financeiro.gateway.appypay.AppyPayProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
@Profile("prod")
@RequiredArgsConstructor
public class CriticalConfigurationValidator {

    private static final List<String> PLACEHOLDER_MARKERS = List.of(
            "change_me",
            "placeholder",
            "pending",
            "your-",
            "your_",
            "seu_",
            "dummy",
            "example.com",
            "localhost",
            "todo"
    );

    private final AppyPayProperties appyPayProperties;

    @PostConstruct
    public void validate() {
        if (!appyPayProperties.isMock()) {
            assertConfigured("app.payment.appypay.base-url", appyPayProperties.getBaseUrl());
            assertConfigured("app.payment.appypay.token-url", appyPayProperties.getTokenUrl());
            assertConfigured("app.payment.appypay.client-id", appyPayProperties.getClientId());
            assertConfigured("app.payment.appypay.client-secret", appyPayProperties.getClientSecret());
            assertConfigured("app.payment.appypay.resource", appyPayProperties.getResource());
            assertConfigured("app.payment.appypay.methods.gpo", appyPayProperties.getGpoMethodId());
            assertConfigured("app.payment.appypay.methods.ref", appyPayProperties.getRefMethodId());
        }

        assertConfigured("app.payment.appypay.callback-url", appyPayProperties.getCallbackUrl());
        assertProdUrl("app.payment.appypay.callback-url", appyPayProperties.getCallbackUrl());

        if (appyPayProperties.isWebhookSignatureRequired()) {
            assertConfigured("app.payment.appypay.webhook-secret", appyPayProperties.getWebhookSecret());
        }
    }

    private void assertConfigured(String propertyName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("[CONFIG] Configuração obrigatória ausente em produção: " + propertyName);
        }
        if (looksLikePlaceholder(value)) {
            throw new IllegalStateException("[CONFIG] Placeholder inválido em produção: " + propertyName);
        }
    }

    private void assertProdUrl(String propertyName, String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.contains("localhost")) {
            throw new IllegalStateException("[CONFIG] URL inválida em produção: " + propertyName);
        }
    }

    private boolean looksLikePlaceholder(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return PLACEHOLDER_MARKERS.stream().anyMatch(lower::contains);
    }
}
