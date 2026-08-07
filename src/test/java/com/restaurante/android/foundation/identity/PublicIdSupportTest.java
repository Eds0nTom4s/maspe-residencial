package com.restaurante.android.foundation.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicIdSupportTest {

    @Test
    void generatesAndFormatsCanonicalLowercaseUuidV4() {
        UUID generated = PublicIdSupport.generate();

        assertThat(generated.version()).isEqualTo(4);
        assertThat(generated.variant()).isEqualTo(2);
        assertThat(PublicIdSupport.format(generated))
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
        assertThat(PublicIdSupport.parseCanonical(generated.toString())).isEqualTo(generated);
    }

    @Test
    void rejectsNonCanonicalOrNonV4Identifiers() {
        UUID v4 = UUID.randomUUID();

        assertThatThrownBy(() -> PublicIdSupport.parseCanonical(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PublicIdSupport.parseCanonical(v4.toString().toUpperCase()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PublicIdSupport.parseCanonical("00000000-0000-1000-8000-000000000000"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PublicIdSupport.format(UUID.fromString("00000000-0000-1000-8000-000000000000")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
