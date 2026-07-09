package com.restaurante.fiscal.autoissue;

import com.restaurante.exception.BusinessException;
import com.restaurante.fiscal.autoissue.service.FiscalAutoIssueFailureClassifier;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

import static org.assertj.core.api.Assertions.assertThat;

public class FiscalAutoIssueFailureClassifierTest {

    private final FiscalAutoIssueFailureClassifier classifier = new FiscalAutoIssueFailureClassifier();

    @Test
    void classificaBusinessExceptionComoPermanente() {
        var c = classifier.classify(new BusinessException("Regra"));
        assertThat(c.retryable()).isFalse();
        assertThat(c.code()).isEqualTo("BUSINESS_RULE");
    }

    @Test
    void classificaDataAccessComoRetryable() {
        var c = classifier.classify(new TransientDataAccessResourceException("db"));
        assertThat(c.retryable()).isTrue();
        assertThat(c.code()).isEqualTo("DB_TRANSIENT");
    }
}

