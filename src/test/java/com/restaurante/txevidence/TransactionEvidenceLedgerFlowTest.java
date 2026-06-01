package com.restaurante.txevidence;

import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TransactionEvidenceEvent;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.TenantRepository;
import com.restaurante.service.operacional.OperationalEventLogService;
import com.restaurante.txevidence.repository.TransactionEvidenceEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "consuma.evidence.tx-ledger.enabled=true",
        "consuma.evidence.tx-ledger.key-version=1",
        "consuma.evidence.tx-ledger.dev-hmac-secret=test-secret"
})
public class TransactionEvidenceLedgerFlowTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private OperationalEventLogService operationalEventLogService;
    @Autowired private TransactionEvidenceEventRepository eventRepository;

    @Test
    void operationalEventLogAfterCommitCreatesLedgerEvent() {
        Tenant tenant = criarTenant();

        operationalEventLogService.logGenericForTenant(
                tenant.getId(),
                OperationalEventType.PAGAMENTO_CONFIRMADO_POR_POLLING,
                OperationalEntityType.PAGAMENTO,
                999L,
                OperationalOrigem.SYSTEM,
                "Pagamento confirmado",
                Map.of("pagamentoId", 999L, "amount", "5000.00"),
                null,
                null
        );

        TransactionEvidenceEvent ev = eventRepository.findTopByTenantIdOrderByLedgerSequenceDesc(tenant.getId()).orElseThrow();
        assertThat(ev.getEventType()).isEqualTo(OperationalEventType.PAGAMENTO_CONFIRMADO_POR_POLLING.name());
        assertThat(ev.getSourceEntityType()).isEqualTo(OperationalEntityType.PAGAMENTO.name());
        assertThat(ev.getSourceEntityId()).isEqualTo(999L);
    }

    private Tenant criarTenant() {
        Tenant t = new Tenant();
        t.setNome("Tenant TXE FLOW");
        t.setSlug("tenant-txe-flow-" + System.nanoTime());
        t.setTenantCode("TXF" + (System.nanoTime() % 100000));
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }
}
