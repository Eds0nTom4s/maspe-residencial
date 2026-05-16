package com.restaurante.provisioning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class TenantProvisioningErrorHandlingIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "platform-admin")
    void realProvision_returnsStandardizedError_onQrLimitExceeded() throws Exception {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        String payload = """
                {
                  "tenant": { "nome": "Rest C", "slug": "rest-c", "tipo": "RESTAURANTE" },
                  "planoCodigo": "PILOTO",
                  "templateCodigo": "RESTAURANTE_SIMPLES",
                  "instituicao": { "nome": "Rest C" },
                  "responsavel": { "email": "owner@c.com", "criarUsuario": true },
                  "opcoes": {
                    "criarMesas": true,
                    "quantidadeMesas": 10,
                    "criarQrPorMesa": true,
                    "criarQrPrincipal": true,
                    "criarUnidadeAtendimentoDefault": true,
                    "criarCategoriaDefault": true,
                    "ativarTenant": true
                  }
                }
                """;

        String resp = mockMvc.perform(post("/platform/tenants/provisionar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/code").asText()).isEqualTo("MAX_QR_CODES_EXCEDIDO");
        assertThat(json.at("/field").asText()).contains("maxQrCodes");
    }
}

