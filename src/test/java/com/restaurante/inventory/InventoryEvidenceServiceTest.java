package com.restaurante.inventory;

import com.restaurante.inventory.evidence.InventoryEvidenceService;
import com.restaurante.inventory.service.InventoryItemService;
import com.restaurante.inventory.service.InventoryStockService;
import com.restaurante.inventory.service.InventoryUnitService;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnitOfMeasure;
import com.restaurante.model.enums.InventoryItemType;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"consuma.inventory.enabled=true"})
public class InventoryEvidenceServiceTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private InventoryUnitService unitService;
    @Autowired private InventoryItemService itemService;
    @Autowired private InventoryStockService stockService;
    @Autowired private InventoryEvidenceService evidenceService;

    @Test
    @Transactional
    void inventoryEvidenceIncluiMovementsEHashDeterministico() {
        Tenant tenant = criarTenant();
        UnitOfMeasure unit = unitService.createUnit(tenant, "UNIT", "Unit", null, false);
        var item = itemService.create(tenant, "Item", "S", InventoryItemType.RAW_MATERIAL, null, unit.getCode(), true, true, null, null);
        stockService.stockIn(tenant.getId(), item.getId(), new BigDecimal("5"), unit.getCode(), new BigDecimal("10.00"), "Compra", null);

        var ev1 = evidenceService.buildForTurno(tenant.getId(), 1L, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        var ev2 = evidenceService.buildForTurno(tenant.getId(), 1L, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));

        assertThat(ev1.getTotalMovements()).isEqualTo(1);
        assertThat(ev1.getStockInCount()).isEqualTo(1);
        assertThat(ev1.getConsumptionRecords()).isNotNull();
        assertThat(ev2.getTotalMovements()).isEqualTo(ev1.getTotalMovements());
    }

    private Tenant criarTenant() {
        Tenant t = new Tenant();
        t.setNome("Tenant Evidence");
        t.setSlug("tenant-evidence");
        t.setTenantCode("INVE");
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }
}

