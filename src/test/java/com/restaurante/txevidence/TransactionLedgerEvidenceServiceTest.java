package com.restaurante.txevidence;

import com.restaurante.financeiro.snapshot.evidence.dto.TransactionLedgerEvidenceSectionDTO;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TransactionEvidenceSourceModule;
import com.restaurante.repository.TenantRepository;
import com.restaurante.txevidence.dto.TransactionEvidenceEventRequest;
import com.restaurante.txevidence.evidence.TransactionLedgerEvidenceService;
import com.restaurante.txevidence.service.TransactionEvidenceLedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "consuma.evidence.tx-ledger.enabled=true",
        "consuma.evidence.tx-ledger.key-version=1",
        "consuma.evidence.tx-ledger.dev-hmac-secret=test-secret"
})
public class TransactionLedgerEvidenceServiceTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private TransactionEvidenceLedgerService ledgerService;
    @Autowired private TransactionLedgerEvidenceService evidenceService;

    @Test
    void buildsEvidenceWithWarningsWhenNoVerificationRun() {
        Tenant tenant = criarTenant();
        LocalDateTime start = LocalDateTime.of(2026, 5, 25, 8, 0);
        LocalDateTime end = start.plusHours(1);

        TransactionEvidenceEventRequest r = new TransactionEvidenceEventRequest();
        r.setTenantId(tenant.getId());
        r.setEventType("PAGAMENTO_CONFIRMADO_POR_POLLING");
        r.setSourceModule(TransactionEvidenceSourceModule.PAYMENT);
        r.setSourceEntityType("PAGAMENTO");
        r.setSourceEntityId(10L);
        r.setOccurredAt(start.plusMinutes(5));
        r.setIdempotencyKey("tenant:" + tenant.getId() + ":ev1");
        r.setPayloadFields(Map.of("x", 1));
        ledgerService.recordEvidenceEvent(r);

        TransactionLedgerEvidenceSectionDTO out = evidenceService.buildForTurno(tenant.getId(), start, end, end);
        assertThat(out.getTotalLedgerEvents()).isEqualTo(1);
        assertThat(out.getWarnings()).contains("TRANSACTION_LEDGER_VERIFICATION_NOT_RUN");
    }

    private Tenant criarTenant() {
        Tenant t = new Tenant();
        t.setNome("Tenant TXE EVID");
        t.setSlug("tenant-txe-evid-" + System.nanoTime());
        t.setTenantCode("TXE" + (System.nanoTime() % 100000));
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }
}

