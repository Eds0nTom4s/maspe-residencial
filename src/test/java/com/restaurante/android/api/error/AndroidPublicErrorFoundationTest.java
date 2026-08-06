package com.restaurante.android.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.android.api.AndroidPublicApiController;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class AndroidPublicErrorFoundationTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AndroidPublicTraceIdResolver traceIds = new AndroidPublicTraceIdResolver();
    private final AndroidPublicApiExceptionHandler handler = new AndroidPublicApiExceptionHandler();

    @Test
    void serializesExactPublicEnvelopeAndFieldErrors() throws Exception {
        AndroidPublicErrorEnvelope envelope = new AndroidPublicErrorEnvelope(new AndroidPublicError(
                "INVALID_REQUEST",
                "Dados inválidos.",
                false,
                List.of(new AndroidPublicFieldError("/customer/phone", "INVALID_VALUE", "Telefone inválido.")),
                "trace-123"));

        JsonNode json = mapper.readTree(mapper.writeValueAsBytes(envelope));
        assertThat(json.fieldNames()).toIterable().containsExactly("error");
        assertThat(json.at("/error/code").asText()).isEqualTo("INVALID_REQUEST");
        assertThat(json.at("/error/retryable").asBoolean()).isFalse();
        assertThat(json.at("/error/fieldErrors/0/field").asText()).isEqualTo("/customer/phone");
        assertThat(json.at("/error/traceId").asText()).isEqualTo("trace-123");
    }

    @Test
    void unexpectedFailureUsesSafeMessageAndNeverLeaksInternals() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "request-safe-1");

        var response = handler.handleUnexpected(
                new RuntimeException("select tenant_id from pedidos; stack trace secret"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        AndroidPublicError error = response.getBody().error();
        assertThat(error.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(error.message()).isEqualTo("Não foi possível processar o pedido.");
        assertThat(error.message()).doesNotContain("select", "tenant_id", "stack", "secret");
        assertThat(error.traceId()).isEqualTo("request-safe-1");
    }

    @Test
    void traceIdUsesSafeExistingHeaderOrGeneratesOpaqueValue() {
        MockHttpServletRequest correlation = new MockHttpServletRequest();
        correlation.addHeader("X-Correlation-Id", "correlation_42");
        assertThat(traceIds.resolve(correlation)).isEqualTo("correlation_42");

        MockHttpServletRequest unsafe = new MockHttpServletRequest();
        unsafe.addHeader("X-Request-Id", "bad value\nwith newline");
        assertThat(traceIds.resolve(unsafe)).matches("[0-9a-f-]{36}");
        assertThat(traceIds.resolve((HttpServletRequest) null)).matches("[0-9a-f-]{36}");
    }

    @Test
    void adviceIsRestrictedToAndroidPublicControllerMarker() {
        RestControllerAdvice advice = AndroidPublicApiExceptionHandler.class
                .getAnnotation(RestControllerAdvice.class);
        assertThat(advice).isNotNull();
        assertThat(advice.annotations()).containsExactly(AndroidPublicApiController.class);
    }

    @Test
    void publicWireRecordsCannotExposeInternalIdentityFields() {
        List<Class<?>> publicRecords = List.of(
                AndroidPublicErrorEnvelope.class,
                AndroidPublicError.class,
                AndroidPublicFieldError.class);
        List<String> forbidden = List.of(
                "tenantId", "businessAccountId", "institutionId", "unitId", "entity", "table", "sql", "stackTrace");

        for (Class<?> record : publicRecords) {
            List<String> fields = Arrays.stream(record.getRecordComponents())
                    .map(RecordComponent::getName)
                    .toList();
            assertThat(fields).doesNotContainAnyElementsOf(forbidden);
        }
    }
}
