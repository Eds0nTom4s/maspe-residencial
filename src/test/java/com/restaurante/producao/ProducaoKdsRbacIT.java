package com.restaurante.producao;

import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.TenantProvisioningService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class ProducaoKdsRbacIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired TenantProvisioningService provisioningService;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "finance-user")
    void finance_isBlockedFromMetricasAndSubpedidosTenant() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant();
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), 9999L,
                Set.of("TENANT_FINANCE"), TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(get("/tenant/producao/metricas").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/tenant/producao/subpedidos").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "kitchen-user")
    void kitchen_isBlockedFromSubpedidosTenantGeneralEndpoint() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant();
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), 9999L,
                Set.of("TENANT_KITCHEN"), TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(get("/tenant/producao/subpedidos").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "operator-user")
    void pageSizeAboveMax_isLimited_notRejected() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant();
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of("TENANT_OPERATOR"), TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(get("/tenant/producao/subpedidos")
                        .param("size", "1000")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private ProvisionarTenantResponse provisionTenant() {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        String slug = "tenant-kds-rbac-" + System.nanoTime();
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant KDS RBAC")
                                .slug(slug)
                                .tenantCode("TR" + (System.nanoTime() % 1000))
                                .tipo(TenantTipo.RESTAURANTE)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("RESTAURANTE_SIMPLES")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst KDS RBAC")
                                .sigla(uniqueSigla("IR"))
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email("owner-kds-rbac-" + System.nanoTime() + "@a.com")
                                .telefone("+244900" + (System.nanoTime() % 1_000_000))
                                .criarUsuario(true)
                                .build())
                .build()
        );
    }

    private static String uniqueSigla(String prefix) {
        String normalizedPrefix = prefix == null ? "I" : prefix.replaceAll("[^A-Z0-9]", "");
        if (normalizedPrefix.isBlank()) {
            normalizedPrefix = "I";
        }
        if (normalizedPrefix.length() > 3) {
            normalizedPrefix = normalizedPrefix.substring(0, 3);
        }

        long suffix = Math.abs(System.nanoTime() % 10_000_000L);
        return normalizedPrefix + String.format("%07d", suffix);
    }
}
