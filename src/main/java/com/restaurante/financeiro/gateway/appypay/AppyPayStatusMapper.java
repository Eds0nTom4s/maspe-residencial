package com.restaurante.financeiro.gateway.appypay;

import com.restaurante.financeiro.enums.StatusPagamentoGateway;

import java.util.Locale;

public final class AppyPayStatusMapper {

    private AppyPayStatusMapper() {}

    public static StatusPagamentoGateway toGatewayStatus(String status) {
        if (status == null || status.isBlank()) {
            return StatusPagamentoGateway.PENDENTE;
        }

        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "CONFIRMED", "SUCCESS", "SUCCEEDED", "PAID", "PAGO" -> StatusPagamentoGateway.CONFIRMADO;
            case "FAILED", "FAILURE", "CANCELLED", "CANCELED", "EXPIRED", "REJECTED" -> StatusPagamentoGateway.FALHOU;
            case "REFUNDED", "REVERSED" -> StatusPagamentoGateway.ESTORNADO;
            default -> StatusPagamentoGateway.PENDENTE;
        };
    }

    public static boolean isConfirmed(String status) {
        return toGatewayStatus(status) == StatusPagamentoGateway.CONFIRMADO;
    }

    public static boolean isFailed(String status) {
        return toGatewayStatus(status) == StatusPagamentoGateway.FALHOU;
    }

    public static boolean isMethod(String value, String expectedPrefix) {
        return value != null
                && expectedPrefix != null
                && value.trim().toUpperCase(Locale.ROOT).startsWith(expectedPrefix.toUpperCase(Locale.ROOT));
    }
}
