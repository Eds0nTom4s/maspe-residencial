package com.restaurante.producao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.InstituicaoRepository;
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
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired InstituicaoRepository instituicaoRepository;

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
                provisioned.getTenantId(), provisioned.getTenantCode(), 9999L,
                Set.of("TENANT_FINANCE"), TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(get("/tenant/producao/unidades").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "tenant-owner")
    void owner_canManageCanonicalProductionUnitsAndRoutes() throws Exception {
        ProvisionarTenantResponse provisioned = provisionTenant();
        TenantContextHolder.set(new TenantContext(
                provisioned.getTenantId(), provisioned.getTenantCode(), provisioned.getOwnerUserId(),
                Set.of("TENANT_OWNER"), TenantResolutionSource.JWT, false, false
        ));
        var institution = instituicaoRepository.findByTenantId(provisioned.getTenantId()).getFirst();
        var category = categoriaProdutoRepository
                .findByTenantIdAndAtivoTrueOrderByOrdemAsc(provisioned.getTenantId()).getFirst();

        String createdBody = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/tenant/producao/unidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instituicaoId": %d,
                                  "unidadeAtendimentoId": %d,
                                  "nome": "Cozinha Lanches",
                                  "codigo": "COZINHA_LANCHES",
                                  "tipo": "COZINHA",
                                  "ordem": 1
                                }
                                """.formatted(institution.getId(), provisioned.getUnidadeAtendimentoId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long productionUnitId = objectMapper.readTree(createdBody).at("/data/id").asLong();

        String partialUpdateBody = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/tenant/producao/unidades/{id}", productionUnitId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Cozinha Lanches Actualizada"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(partialUpdateBody).at("/data/unidadeAtendimentoId").asLong())
                .isEqualTo(provisioned.getUnidadeAtendimentoId());

        String detachedBody = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/tenant/producao/unidades/{id}", productionUnitId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"unidadeAtendimentoId": null}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(detachedBody).at("/data/unidadeAtendimentoId").isNull()).isTrue();

        String routeBody = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/tenant/producao/rotas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoriaProdutoId": %d, "unidadeProducaoId": %d, "prioridade": 0}
                                """.formatted(category.getId(), productionUnitId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode route = objectMapper.readTree(routeBody).path("data");
        assertThat(route.path("instituicaoId").asLong()).isEqualTo(institution.getId());
        assertThat(route.path("unidadeProducaoId").asLong()).isEqualTo(productionUnitId);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/tenant/producao/unidades/{id}/desativar", productionUnitId))
                .andExpect(status().isConflict());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/tenant/producao/rotas/{id}", route.path("id").asLong()))
                .andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/tenant/producao/unidades/{id}/desativar", productionUnitId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "kitchen-user")
    void kitchen_cannotMutateProductionTopology() throws Exception {
        ProvisionarTenantResponse provisioned = provisionTenant();
        TenantContextHolder.set(new TenantContext(
                provisioned.getTenantId(), provisioned.getTenantCode(), 9_999_999L,
                Set.of("TENANT_KITCHEN"), TenantResolutionSource.JWT, false, false
        ));
        var institution = instituicaoRepository.findByTenantId(provisioned.getTenantId()).getFirst();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/tenant/producao/unidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"instituicaoId": %d, "nome": "Forbidden", "codigo": "FORBIDDEN", "tipo": "COZINHA"}
                                """.formatted(institution.getId())))
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
