package com.restaurante.service.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CanonicalCommandSupportTest {

    @Test
    void fingerprint_isStableForEquivalentMapOrdering() {
        CanonicalCommandSupport support = new CanonicalCommandSupport(new ObjectMapper(), mock(EntityManager.class));
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("vertical", "CONSUMA_PONTO");
        first.put("planoCodigo", "PRO");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("planoCodigo", "PRO");
        second.put("vertical", "CONSUMA_PONTO");

        assertThat(support.fingerprint(first)).isEqualTo(support.fingerprint(second)).hasSize(64);
    }

    @Test
    void fingerprint_changesWhenLogicalPayloadChanges() {
        CanonicalCommandSupport support = new CanonicalCommandSupport(new ObjectMapper(), mock(EntityManager.class));
        assertThat(support.fingerprint(Map.of("planoCodigo", "PRO")))
                .isNotEqualTo(support.fingerprint(Map.of("planoCodigo", "PILOTO")));
    }

    @Test
    void requireReasonTrimsAndRejectsBlankOrOversizedValues() {
        CanonicalCommandSupport support = new CanonicalCommandSupport(new ObjectMapper(), mock(EntityManager.class));

        assertThat(support.requireReason("  activação explícita  ")).isEqualTo("activação explícita");
        assertThatThrownBy(() -> support.requireReason("   ")).hasMessage("REASON_REQUIRED");
        assertThatThrownBy(() -> support.requireReason("x".repeat(501))).hasMessage("REASON_TOO_LONG");
    }

    @Test
    void nifNormalizationIsSharedAndStrict() {
        CanonicalCommandSupport support = new CanonicalCommandSupport(new ObjectMapper(), mock(EntityManager.class));
        assertThat(support.normalizeNif("  nif.12/34-5 ")).isEqualTo("NIF12345");
        assertThat(support.normalizeNif("   ")).isNull();
        assertThatThrownBy(() -> support.normalizeNif("12?"))
                .hasMessageContaining("NIF inválido");
    }
}
