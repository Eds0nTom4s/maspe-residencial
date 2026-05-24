package com.restaurante.fiscal.autoissue.service;

import com.restaurante.exception.BusinessException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class FiscalAutoIssueFailureClassifier {

    public Classification classify(Throwable error) {
        if (error == null) {
            return new Classification(false, "UNKNOWN", "Erro desconhecido");
        }

        if (error instanceof DataAccessException) {
            return new Classification(true, "DB_TRANSIENT", safeMessage(error));
        }
        if (error instanceof BusinessException) {
            return new Classification(false, "BUSINESS_RULE", safeMessage(error));
        }
        if (error instanceof IllegalStateException) {
            return new Classification(false, "INVALID_STATE", safeMessage(error));
        }

        return new Classification(true, "UNEXPECTED", safeMessage(error));
    }

    private static String safeMessage(Throwable t) {
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) return t.getClass().getSimpleName();
        msg = msg.replaceAll("[\\r\\n\\t]", " ").trim();
        if (msg.length() > 500) {
            return msg.substring(0, 500);
        }
        return msg;
    }

    public record Classification(boolean retryable, String code, String message) { }
}

