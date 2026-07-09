package com.restaurante.inventory;

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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "consuma.inventory.enabled=true"
})
public class InventoryStockServiceTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private InventoryUnitService unitService;
    @Autowired private InventoryItemService itemService;
    @Autowired private InventoryStockService stockService;

    @Test
    @Transactional
    void stockInAumentaQuantidadeEAtualizaCustoMedio() {
        Tenant tenant = criarTenant();
        UnitOfMeasure unit = unitService.createUnit(tenant, "UNIT", "Unit", null, false);

        var item = itemService.create(tenant, "Carne", "SKU-1", InventoryItemType.RAW_MATERIAL, "Food", unit.getCode(), true, true, null, null);

        stockService.stockIn(tenant.getId(), item.getId(), new BigDecimal("10"), "UNIT", new BigDecimal("100.00"), "Compra", "F001");
        var reloaded = itemService.get(tenant.getId(), item.getId());
        assertThat(reloaded.getCurrentQuantity()).isEqualByComparingTo("10.000000");
        assertThat(reloaded.getAverageCost()).isEqualByComparingTo("100.000000");

        stockService.stockIn(tenant.getId(), item.getId(), new BigDecimal("10"), "UNIT", new BigDecimal("200.00"), "Compra2", "F002");
        reloaded = itemService.get(tenant.getId(), item.getId());
        assertThat(reloaded.getCurrentQuantity()).isEqualByComparingTo("20.000000");
        assertThat(reloaded.getAverageCost()).isEqualByComparingTo("150.000000");
    }

    private Tenant criarTenant() {
        Tenant t = new Tenant();
        t.setNome("Tenant Inventory");
        t.setSlug("tenant-inventory");
        t.setTenantCode("INV");
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }
}

