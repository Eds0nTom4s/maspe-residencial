package com.restaurante.provisioning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class PlatformTenantProvisioningPreviewControllerIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantRepository tenantRepository;

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "platform-admin")
    void preview_allowed_doesNotPersist() throws Exception {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        String payload = """
                {
                  "tenant": {
                    "nome": "Banca da Tia Rosa",
                    "slug": "banca-tia-rosa",
                    "tenantCode": "ROSA",
                    "tipo": "VENDEDOR_RUA"
                  },
                  "planoCodigo": "PILOTO",
                  "templateCodigo": "VENDEDOR_RUA",
                  "instituicao": { "nome": "Banca da Tia Rosa", "sigla": "ROSA" },
                  "responsavel": {
                    "nome": "Rosa Manuel",
                    "telefone": "+244900000099",
                    "email": "rosa@email.com",
                    "criarUsuario": true
                  },
                  "opcoes": {
                    "criarQrPrincipal": true,
                    "criarCategoriaDefault": true,
                    "criarUnidadeAtendimentoDefault": true,
                    "ativarTenant": true
                  }
                }
                """;

        String resp = mockMvc.perform(post("/platform/tenants/provisionar/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/success").asBoolean()).isTrue();
        assertThat(json.at("/data/permitido").asBoolean()).isTrue();

        assertThat(tenantRepository.existsBySlug("banca-tia-rosa")).isFalse();
        assertThat(tenantRepository.existsByTenantCode("ROSA")).isFalse();
    }

    @Test
    @WithMockUser(username = "platform-admin")
    void preview_blocked_byMaxQrCodes() throws Exception {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        String payload = """
                {
                  "tenant": { "nome": "Rest A", "slug": "rest-a", "tipo": "RESTAURANTE" },
                  "planoCodigo": "PILOTO",
                  "templateCodigo": "RESTAURANTE_SIMPLES",
                  "instituicao": { "nome": "Rest A" },
                  "responsavel": { "email": "owner@a.com", "criarUsuario": true },
                  "opcoes": {
                    "criarMesas": true,
                    "quantidadeMesas": 20,
                    "criarQrPorMesa": true,
                    "criarQrPrincipal": true,
                    "criarUnidadeAtendimentoDefault": true,
                    "criarCategoriaDefault": true,
                    "ativarTenant": true
                  }
                }
                """;

        String resp = mockMvc.perform(post("/platform/tenants/provisionar/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/data/permitido").asBoolean()).isFalse();
        String codes = json.at("/data/bloqueios").toString();
        assertThat(codes).contains("MAX_QR_CODES_EXCEDIDO");
        assertThat(json.at("/data/recursosPlanejados/qrCodesCriados").asInt()).isEqualTo(21);
    }

    @Test
    @WithMockUser(username = "platform-admin")
    void preview_allows_whenOverrideMaxQrCodes() throws Exception {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        String payload = """
                {
                  "tenant": { "nome": "Rest B", "slug": "rest-b", "tipo": "RESTAURANTE" },
                  "planoCodigo": "PILOTO",
                  "templateCodigo": "RESTAURANTE_SIMPLES",
                  "instituicao": { "nome": "Rest B" },
                  "responsavel": { "email": "owner@b.com", "criarUsuario": true },
                  "opcoes": {
                    "criarMesas": true,
                    "quantidadeMesas": 20,
                    "criarQrPorMesa": true,
                    "criarQrPrincipal": true,
                    "criarUnidadeAtendimentoDefault": true,
                    "criarCategoriaDefault": true,
                    "ativarTenant": true
                  },
                  "limitesOverride": { "maxQrCodes": 21, "motivo": "piloto", "configuradoPor": "admin" }
                }
                """;

        String resp = mockMvc.perform(post("/platform/tenants/provisionar/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/data/permitido").asBoolean()).isTrue();
        assertThat(json.at("/data/recursosPlanejados/qrCodesCriados").asInt()).isEqualTo(21);
    }

    @Test
    @WithMockUser(username = "non-platform")
    void nonPlatformAdmin_cannotPreview() throws Exception {
        TenantContextHolder.set(new TenantContext(
                1L, "TA", 2L, Set.of("ROLE_GERENTE"),
                TenantResolutionSource.JWT, false, false
        ));

        String payload = """
                {
                  "tenant": { "nome": "X", "slug": "x", "tipo": "VENDEDOR_RUA" },
                  "planoCodigo": "PILOTO",
                  "templateCodigo": "VENDEDOR_RUA",
                  "instituicao": { "nome": "X" }
                }
                """;

        mockMvc.perform(post("/platform/tenants/provisionar/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }
}
