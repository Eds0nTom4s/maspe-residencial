package com.restaurante.provisioning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.repository.QrCodeOperacionalRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContextHolder;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class TenantProvisioningAdvancedTemplateIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired TenantRepository tenantRepository;
    @Autowired QrCodeOperacionalRepository qrCodeOperacionalRepository;
    @Autowired UserRepository userRepository;

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void restauranteSimples_templateCreatesMesasAndQrPorMesa_withinPilotLimits() throws Exception {
        String payload = """
                {
                  "tenant": {
                    "nome": "Restaurante Teste",
                    "slug": "restaurante-teste",
                    "tenantCode": "RST",
                    "telefone": "+244900000111",
                    "email": "rst@email.com",
                    "tipo": "RESTAURANTE"
                  },
                  "planoCodigo": "PILOTO",
                  "templateCodigo": "RESTAURANTE_SIMPLES",
                  "instituicao": { "nome": "Restaurante Teste", "sigla": "RST1", "nif": "NIF-RST1", "telefone": "+244900000111" },
                  "responsavel": { "nome": "Owner", "telefone": "+244900000111", "email": "rst@email.com", "senhaTemporaria": "Alterar@123" },
                  "opcoes": { "ativarTenant": true }
                }
                """;

        String resp = mockMvc.perform(post("/platform/tenants/provisionar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        long tenantId = json.at("/data/tenantId").asLong();
        int totalMesas = json.at("/data/totalMesasCriadas").asInt();
        int totalQr = json.at("/data/totalQrCodesCriados").asInt();

        // template default: 9 mesas + 1 QR principal = 10 QRs (compatível com PILOTO maxQrCodes=20)
        assertThat(totalMesas).isEqualTo(9);
        assertThat(totalQr).isEqualTo(10);

        assertThat(json.at("/data/mesas").size()).isEqualTo(9);
        assertThat(qrCodeOperacionalRepository.countByTenantId(tenantId)).isEqualTo(10);
    }

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void exceedsMaxQrCodes_failsAndRollsBack() throws Exception {
        String payload = """
                {
                  "tenant": {
                    "nome": "Restaurante Limite",
                    "slug": "restaurante-limite",
                    "tenantCode": "LIM",
                    "telefone": "+244900000222",
                    "email": "lim@email.com",
                    "tipo": "RESTAURANTE"
                  },
                  "planoCodigo": "PILOTO",
                  "templateCodigo": "RESTAURANTE_SIMPLES",
                  "instituicao": { "nome": "Restaurante Limite", "sigla": "LIM1", "nif": "NIF-LIM1", "telefone": "+244900000222" },
                  "responsavel": { "nome": "Owner", "telefone": "+244900000222", "email": "lim@email.com", "senhaTemporaria": "Alterar@123" },
                  "opcoes": { "criarMesas": true, "quantidadeMesas": 20, "criarQrPorMesa": true, "ativarTenant": true }
                }
                """;

        mockMvc.perform(post("/platform/tenants/provisionar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());

        assertThat(tenantRepository.findBySlug("restaurante-limite")).isEmpty();
        assertThat(tenantRepository.findByTenantCode("LIM")).isEmpty();
    }

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void overrideMaxQrCodes_allowsMoreMesasWithQrPorMesa() throws Exception {
        String payload = """
                {
                  "tenant": {
                    "nome": "Restaurante Override",
                    "slug": "restaurante-override",
                    "tenantCode": "OVR",
                    "telefone": "+244900000333",
                    "email": "ovr@email.com",
                    "tipo": "RESTAURANTE"
                  },
                  "planoCodigo": "PILOTO",
                  "templateCodigo": "RESTAURANTE_SIMPLES",
                  "instituicao": { "nome": "Restaurante Override", "sigla": "OVR1", "nif": "NIF-OVR1", "telefone": "+244900000333" },
                  "responsavel": { "nome": "Owner", "telefone": "+244900000333", "email": "ovr@email.com", "senhaTemporaria": "Alterar@123" },
                  "limitesOverride": { "maxQrCodes": 21, "motivo": "Piloto restaurante", "configuradoPor": "PLATFORM_ADMIN" },
                  "opcoes": { "criarMesas": true, "quantidadeMesas": 20, "criarQrPorMesa": true, "ativarTenant": true }
                }
                """;

        String resp = mockMvc.perform(post("/platform/tenants/provisionar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        long tenantId = json.at("/data/tenantId").asLong();
        assertThat(json.at("/data/totalMesasCriadas").asInt()).isEqualTo(20);
        assertThat(qrCodeOperacionalRepository.countByTenantId(tenantId)).isEqualTo(21);
    }

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void overrideMaxUsuarios_zero_blocksProvisioning() throws Exception {
        String payload = """
                {
                  "tenant": { "nome": "NoUsers", "slug": "no-users", "tenantCode": "NUS", "tipo": "VENDEDOR_RUA" },
                  "planoCodigo": "PILOTO",
                  "templateCodigo": "VENDEDOR_RUA",
                  "instituicao": { "nome": "NoUsers", "sigla": "NUS1", "nif": "NIF-NUS1", "telefone": "+244900000444" },
                  "responsavel": { "nome": "Owner", "telefone": "+244900000444", "email": "nus@email.com", "senhaTemporaria": "Alterar@123" },
                  "limitesOverride": { "maxUsuarios": 0, "motivo": "teste", "configuradoPor": "PLATFORM_ADMIN" }
                }
                """;

        mockMvc.perform(post("/platform/tenants/provisionar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());

        assertThat(tenantRepository.findBySlug("no-users")).isEmpty();
        assertThat(userRepository.findByEmail("nus@email.com")).isEmpty();
    }
}
