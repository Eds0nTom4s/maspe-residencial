package com.restaurante.txevidence;

import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TransactionEvidenceEvent;
import com.restaurante.model.entity.TransactionEvidenceVerificationRun;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TransactionEvidenceSourceModule;
import com.restaurante.model.enums.TransactionEvidenceVerificationRunStatus;
import com.restaurante.repository.TenantRepository;
import com.restaurante.txevidence.dto.TransactionEvidenceEventRequest;
import com.restaurante.txevidence.repository.TransactionEvidenceEventRepository;
import com.restaurante.txevidence.repository.TransactionEvidenceVerificationIssueRepository;
import com.restaurante.txevidence.service.TransactionEvidenceLedgerService;
import com.restaurante.txevidence.service.TransactionEvidenceVerificationService;
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
public class TransactionEvidenceVerificationServiceTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private TransactionEvidenceLedgerService ledgerService;
    @Autowired private TransactionEvidenceVerificationService verificationService;
    @Autowired private TransactionEvidenceEventRepository eventRepository;
    @Autowired private TransactionEvidenceVerificationIssueRepository issueRepository;

    @Test
    void detectsBrokenChain() {
        Tenant tenant = criarTenant();
        LocalDateTime base = LocalDateTime.of(2026, 5, 25, 9, 0);

        TransactionEvidenceEvent e1 = ledgerService.recordEvidenceEvent(req(tenant.getId(), "PAGAMENTO", 1L, base, "k1"));
        TransactionEvidenceEvent e2 = ledgerService.recordEvidenceEvent(req(tenant.getId(), "PAGAMENTO", 2L, base.plusMinutes(1), "k2"));

        // Tamper event to simulate corruption
        e2.setPreviousEventHash("CORRUPTED");
        eventRepository.saveAndFlush(e2);

        TransactionEvidenceVerificationRun run = verificationService.verifyTenantLedger(tenant.getId(), base.minusMinutes(1), base.plusMinutes(10));
        assertThat(run.getStatus()).isEqualTo(TransactionEvidenceVerificationRunStatus.INVALID);
        assertThat(run.getBrokenChainCount()).isGreaterThan(0);
        assertThat(issueRepository.findByTenantIdAndVerificationRun_IdOrderByIdAsc(tenant.getId(), run.getId())).isNotEmpty();
    }

    private TransactionEvidenceEventRequest req(Long tenantId, String entityType, Long entityId, LocalDateTime occurredAt, String idem) {
        TransactionEvidenceEventRequest r = new TransactionEvidenceEventRequest();
        r.setTenantId(tenantId);
        r.setEventType("PAGAMENTO_CONFIRMADO_POR_POLLING");
        r.setSourceModule(TransactionEvidenceSourceModule.PAYMENT);
        r.setSourceEntityType(entityType);
        r.setSourceEntityId(entityId);
        r.setOccurredAt(occurredAt);
        r.setIdempotencyKey("tenant:" + tenantId + ":" + idem);
        r.setPayloadFields(Map.of("entityId", entityId));
        return r;
    }

    private Tenant criarTenant() {
        Tenant t = new Tenant();
        t.setNome("Tenant TXE VERIFY");
        t.setSlug("tenant-txe-verify-" + System.nanoTime());
        t.setTenantCode("TXV" + (System.nanoTime() % 100000));
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }
}

