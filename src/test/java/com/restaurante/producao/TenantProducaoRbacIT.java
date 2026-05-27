package com.restaurante.producao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class TenantProducaoRbacIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "kitchen-user")
    void kitchen_canListUnidadesProducao() throws Exception {
        ProvisionarTenantResponse provisioned = provisionTenant();

        TenantContextHolder.set(new TenantContext(
                provisioned.getTenantId(), provisioned.getTenantCode(), provisioned.getOwnerUserId(),
                Set.of("TENANT_KITCHEN"), TenantResolutionSource.JWT, false, false
        ));

        String resp = mockMvc.perform(get("/tenant/producao/unidades").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/success").asBoolean()).isTrue();
        assertThat(json.at("/data").isArray()).isTrue();
    }

    @Test
    @WithMockUser(username = "finance-user")
    void finance_cannotAccessProducao() throws Exception {
        ProvisionarTenantResponse provisioned = provisionTenant();

        TenantContextHolder.set(new TenantContext(
                provisioned.getTenantId(), provisioned.getTenantCode(), provisioned.getOwnerUserId(),
                Set.of("TENANT_FINANCE"), TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(get("/tenant/producao/unidades").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    private ProvisionarTenantResponse provisionTenant() {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        String slug = "tenant-prod-" + System.nanoTime();
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant Producao")
                                .slug(slug)
                                .tenantCode("TP" + (System.nanoTime() % 1000))
                                .tipo(TenantTipo.VENDEDOR_RUA)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("VENDEDOR_RUA")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst Producao")
                                .sigla(uniqueSigla("TP"))
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email("owner-prod-" + System.nanoTime() + "@a.com")
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
