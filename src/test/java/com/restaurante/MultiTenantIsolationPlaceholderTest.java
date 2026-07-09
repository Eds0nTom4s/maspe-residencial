package com.restaurante;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MultiTenantIsolationPlaceholderTest {

    @Test
    void multiTenantIsolationMustBeEnforcedInFuture() {
        // Placeholder (Prompt 0.1):
        // - Cenários que DEVEM existir após Tenant Core (Fase 4+):
        //   * Produto do Tenant A não aparece no Tenant B
        //   * Pedido do Tenant A não aparece no Tenant B
        //   * Callback de pagamento não afeta outro tenant
        //   * QR de Tenant A não abre sessão/consulta dados do Tenant B
        //
        // Este teste propositalmente não implementa Tenant ainda.
    }
}
