package com.restaurante.txevidence;

import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TransactionEvidenceEvent;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TransactionEvidenceSourceModule;
import com.restaurante.repository.TenantRepository;
import com.restaurante.txevidence.dto.TransactionEvidenceEventRequest;
import com.restaurante.txevidence.service.TransactionEvidenceLedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
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
public class TransactionEvidenceLedgerServiceTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private TransactionEvidenceLedgerService ledgerService;

    @Test
    void recordsSequenceAndChainsWithIdempotency() {
        Tenant tenant = criarTenant();

        TransactionEvidenceEventRequest r1 = new TransactionEvidenceEventRequest();
        r1.setTenantId(tenant.getId());
        r1.setEventType("PAYMENT_CONFIRMED");
        r1.setSourceModule(TransactionEvidenceSourceModule.PAYMENT);
        r1.setSourceEntityType("PAGAMENTO");
        r1.setSourceEntityId(10L);
        r1.setOccurredAt(LocalDateTime.of(2026, 5, 25, 10, 0));
        r1.setIdempotencyKey("tenant:" + tenant.getId() + ":e1");
        r1.setPayloadFields(Map.of("amount", new BigDecimal("5000.00")));

        TransactionEvidenceEvent e1 = ledgerService.recordEvidenceEvent(r1);
        assertThat(e1.getLedgerSequence()).isEqualTo(1L);
        assertThat(e1.getPreviousEventHash()).isEqualTo(TransactionEvidenceLedgerService.GENESIS_HASH);
        assertThat(e1.getEventHash()).isNotBlank();

        TransactionEvidenceEvent e1dup = ledgerService.recordEvidenceEvent(r1);
        assertThat(e1dup.getId()).isEqualTo(e1.getId());

        TransactionEvidenceEventRequest r2 = new TransactionEvidenceEventRequest();
        r2.setTenantId(tenant.getId());
        r2.setEventType("FISCAL_DOCUMENT_ISSUED");
        r2.setSourceModule(TransactionEvidenceSourceModule.FISCAL);
        r2.setSourceEntityType("FISCAL_DOCUMENT");
        r2.setSourceEntityId(99L);
        r2.setOccurredAt(LocalDateTime.of(2026, 5, 25, 10, 1));
        r2.setIdempotencyKey("tenant:" + tenant.getId() + ":e2");
        r2.setPayloadFields(Map.of("docId", 99L));

        TransactionEvidenceEvent e2 = ledgerService.recordEvidenceEvent(r2);
        assertThat(e2.getLedgerSequence()).isEqualTo(2L);
        assertThat(e2.getPreviousEventHash()).isEqualTo(e1.getEventHash());
    }

    private Tenant criarTenant() {
        Tenant t = new Tenant();
        t.setNome("Tenant TXE");
        t.setSlug("tenant-txe-" + System.nanoTime());
        t.setTenantCode("TXE" + (System.nanoTime() % 100000));
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }
}

