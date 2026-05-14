package com.restaurante;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TenantCorePreparationTest {

    @Test
    void flywayAndPostgresBootstrapIsReadyForTenantCore() {
        // Placeholder (Prompt 0.1):
        // - Quando Tenant Core for introduzido, este teste deve evoluir para validar que:
        //   1) as migrations Flyway aplicam em banco limpo
        //   2) ddl-auto permanece em validate (produção-like)
        //   3) schema contém Tenant/Plano/Subscricao/TenantUser conforme migrations
    }
}
