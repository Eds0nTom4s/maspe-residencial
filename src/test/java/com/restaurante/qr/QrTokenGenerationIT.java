package com.restaurante.qr;

import com.restaurante.repository.QrCodeOperacionalRepository;
import com.restaurante.service.QrCodeOperacionalService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("it-postgres")
class QrTokenGenerationIT extends PostgresTestcontainersConfig {

    @Autowired QrCodeOperacionalService qrCodeOperacionalService;
    @Autowired QrCodeOperacionalRepository qrCodeOperacionalRepository;

    @Test
    void generatedTokens_areNonEmpty_prefixed_andUnlikelyToCollide() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String token = qrCodeOperacionalService.gerarTokenUnico();
            assertThat(token).startsWith("q_");
            assertThat(token.length()).isGreaterThanOrEqualTo(10);
            assertThat(qrCodeOperacionalRepository.existsByToken(token)).isFalse();
            tokens.add(token);
        }
        assertThat(tokens).hasSize(50);
    }
}

