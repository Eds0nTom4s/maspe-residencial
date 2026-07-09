package com.restaurante.consumo;

import com.restaurante.config.PhoneNormalizationProperties;
import com.restaurante.consumo.identificacao.service.TelefoneNormalizerService;
import com.restaurante.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelefoneNormalizerServiceTest {

    private TelefoneNormalizerService service() {
        PhoneNormalizationProperties props = new PhoneNormalizationProperties();
        props.setDefaultCountryCode("+244");
        props.setCountryMode("ANGOLA");
        return new TelefoneNormalizerService(props);
    }

    @Test
    void angola_plain_9xxxxxxxx_normalizes_to_e164() {
        assertThat(service().normalizeOrThrow("923000000")).isEqualTo("+244923000000");
    }

    @Test
    void angola_244_prefix_normalizes() {
        assertThat(service().normalizeOrThrow("244923000000")).isEqualTo("+244923000000");
    }

    @Test
    void angola_plus244_is_kept() {
        assertThat(service().normalizeOrThrow("+244923000000")).isEqualTo("+244923000000");
    }

    @Test
    void strips_symbols_and_spaces() {
        assertThat(service().normalizeOrThrow(" +244 923-000-000 ")).isEqualTo("+244923000000");
    }

    @Test
    void invalid_phone_throws() {
        assertThatThrownBy(() -> service().normalizeOrThrow("123"))
                .isInstanceOf(BusinessException.class);
    }
}

