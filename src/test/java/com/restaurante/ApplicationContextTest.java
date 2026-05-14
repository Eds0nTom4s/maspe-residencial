package com.restaurante;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTest {

    @Test
    void contextLoads() {
        // Smoke test: garante que o contexto Spring inicializa no perfil de teste.
        // (Integração com PostgreSQL via Testcontainers será adicionada quando houver Docker disponível no CI.)
    }
}
