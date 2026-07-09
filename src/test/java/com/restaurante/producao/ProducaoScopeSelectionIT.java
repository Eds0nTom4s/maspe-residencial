package com.restaurante.producao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.UnidadeProducao;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.UnidadeProducaoTipo;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UnidadeProducaoRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class ProducaoScopeSelectionIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;

    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired UnidadeProducaoRepository unidadeProducaoRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "kitchen-user")
    void kitchen_withMultipleUnits_canSelectAndClearMinhaUnidade() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant();

        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        Instituicao inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();

        UnidadeProducao extra = new UnidadeProducao();
        extra.setTenant(tenant);
        extra.setInstituicao(inst);
        extra.setUnidadeAtendimento(ua);
        extra.setNome("Bar");
        extra.setCodigo("BAR-" + (System.nanoTime() % 1000));
        extra.setTipo(UnidadeProducaoTipo.BAR);
        extra.setAtivo(true);
        extra.setOrdem(2);
        extra = unidadeProducaoRepository.save(extra);

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of("TENANT_KITCHEN"), TenantResolutionSource.JWT, false, false
        ));

        String resp1 = mockMvc.perform(get("/tenant/producao/minha-unidade").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json1 = objectMapper.readTree(resp1);
        assertThat(json1.at("/data/modoResolucao").asText()).isEqualTo("EXPLICIT_REQUIRED");
        assertThat(json1.at("/data/opcoes").isArray()).isTrue();

        String payload = "{ \"unidadeProducaoId\": " + extra.getId() + " }";
        String resp2 = mockMvc.perform(post("/tenant/producao/minha-unidade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json2 = objectMapper.readTree(resp2);
        assertThat(json2.at("/data/unidadeProducaoId").asLong()).isEqualTo(extra.getId());

        String resp3 = mockMvc.perform(get("/tenant/producao/minha-unidade").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json3 = objectMapper.readTree(resp3);
        assertThat(json3.at("/data/unidadeProducaoId").asLong()).isEqualTo(extra.getId());
        assertThat(json3.at("/data/modoResolucao").asText()).isEqualTo("USER_DEFAULT");

        mockMvc.perform(delete("/tenant/producao/minha-unidade").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        String resp4 = mockMvc.perform(get("/tenant/producao/minha-unidade").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json4 = objectMapper.readTree(resp4);
        assertThat(json4.at("/data/modoResolucao").asText()).isEqualTo("EXPLICIT_REQUIRED");
    }

    private ProvisionarTenantResponse provisionTenant() {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        String slug = "tenant-sel-" + System.nanoTime();
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant Select")
                                .slug(slug)
                                .tenantCode("TS" + (System.nanoTime() % 1000))
                                .tipo(TenantTipo.RESTAURANTE)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("RESTAURANTE_SIMPLES")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst Sel")
                                .sigla(uniqueSigla("IS"))
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email("owner-sel-" + System.nanoTime() + "@a.com")
                                .telefone("+244901" + (System.nanoTime() % 1_000_000))
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
