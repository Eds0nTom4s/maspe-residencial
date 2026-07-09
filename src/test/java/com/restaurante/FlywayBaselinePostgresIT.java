package com.restaurante;

import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Teste de integração (PostgreSQL real via Testcontainers) para validar:
 * - Flyway aplica o baseline V1 em banco limpo
 * - Hibernate consegue validar o schema (ddl-auto=validate)
 *
 * Observação: será automaticamente ignorado quando Docker não estiver disponível.
 */
@SpringBootTest
@ActiveProfiles("it-postgres")
class FlywayBaselinePostgresIT extends PostgresTestcontainersConfig {

    @Test
    void contextLoadsWithFlywayBaselineOnPostgres() {
        // Smoke test de baseline real em PostgreSQL.
    }
}

