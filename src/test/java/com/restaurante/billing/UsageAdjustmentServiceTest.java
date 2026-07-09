package com.restaurante.billing;

import com.restaurante.billing.repository.UsageAdjustmentRepository;
import com.restaurante.billing.service.UsageAdjustmentService;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.UsageAdjustmentType;
import com.restaurante.model.enums.UsageMetricCode;
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
        "consuma.billing.enabled=true"
})
public class UsageAdjustmentServiceTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private UsageAdjustmentService service;
    @Autowired private UsageAdjustmentRepository repository;

    @Test
    @Transactional
    void criaAjusteManualSemAlterarEventoOriginal() {
        Tenant tenant = criarTenant();
        var a = service.create(
                tenant.getId(),
                UsageMetricCode.PAYMENT_CONFIRMED,
                UsageAdjustmentType.ADMIN_CREDIT,
                new BigDecimal("-1"),
                new BigDecimal("-10.00"),
                "disputa",
                "ADMIN",
                1L,
                null
        );
        assertThat(a.getId()).isNotNull();
        assertThat(repository.findById(a.getId())).isPresent();
        assertThat(a.getMetricCode()).isEqualTo(UsageMetricCode.PAYMENT_CONFIRMED);
    }

    private Tenant criarTenant() {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);
        Tenant t = new Tenant();
        t.setNome("Tenant Adj " + suffix);
        t.setSlug("tenant-adj-" + suffix);
        t.setTenantCode("TJ" + suffix);
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }
}

