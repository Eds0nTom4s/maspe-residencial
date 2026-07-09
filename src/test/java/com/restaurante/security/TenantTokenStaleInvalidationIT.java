package com.restaurante.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.CriarTenantUsuarioRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.SelectTenantRequest;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.web-application-type=servlet",
                "jwt.secret=0123456789ABCDEF0123456789ABCDEF",
                "jwt.expiration=3600000",
                "consuma.security.tenant-token.require-access-version=true"
        }
)
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("it-postgres")
class TenantTokenStaleInvalidationIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired com.restaurante.repository.UserRepository userRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void roleChange_invalidatesOldTenantToken_with401TenantTokenStale() throws Exception {
        var prov = provisionTenant("tok-stale", "TS", "owner-stale@a.com");

        User owner = userRepository.findById(prov.getOwnerUserId()).orElseThrow();
        String ownerGlobal = jwtTokenProvider.generateToken(owner.getUsername(), "ROLE_GERENTE");
        String ownerTenant = selectTenantToken(ownerGlobal, prov.getTenantId());

        // Owner cria um operador
        String telefone = "+244900" + Math.abs("operator-stale".hashCode() % 1_000_000);
        CriarTenantUsuarioRequest create = new CriarTenantUsuarioRequest();
        create.setNome("Operator");
        create.setTelefone(telefone);
        create.setEmail("operator-stale@a.com");
        create.setRoles(Set.of(TenantUserRole.TENANT_OPERATOR));
        create.setEstadoInicial(com.restaurante.model.enums.TenantUserEstado.ATIVO);

        String createResp = mockMvc.perform(post("/tenant/usuarios")
                        .header("Authorization", "Bearer " + ownerTenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode created = objectMapper.readTree(createResp);
        Long operatorUserId = created.at("/data/userId").asLong();
        assertThat(operatorUserId).isPositive();

        // Operador seleciona tenant e acessa /tenant/me
        User operator = userRepository.findById(operatorUserId).orElseThrow();
        String operatorGlobal = jwtTokenProvider.generateToken(operator.getUsername(), "ROLE_GERENTE");
        String operatorTenantToken = selectTenantToken(operatorGlobal, prov.getTenantId());

        mockMvc.perform(get("/tenant/me")
                        .header("Authorization", "Bearer " + operatorTenantToken))
                .andExpect(status().isOk());

        // Owner altera roles do operador -> incrementa version
        String body = """
                { "roles": ["TENANT_CASHIER"] }
                """;
        mockMvc.perform(put("/tenant/usuarios/" + operatorUserId + "/roles")
                        .header("Authorization", "Bearer " + ownerTenant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Token antigo do operador agora deve falhar (stale)
        mockMvc.perform(get("/tenant/me")
                        .header("Authorization", "Bearer " + operatorTenantToken))
                .andExpect(status().isUnauthorized());
    }

    private String selectTenantToken(String globalToken, Long tenantId) throws Exception {
        String selectResp = mockMvc.perform(post("/auth/tenant/select")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SelectTenantRequest(tenantId))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode selectJson = objectMapper.readTree(selectResp);
        String tenantToken = selectJson.at("/data/accessToken").asText();
        assertThat(tenantToken).isNotBlank();
        return tenantToken;
    }

    private com.restaurante.dto.response.ProvisionarTenantResponse provisionTenant(String slug, String tenantCode, String ownerEmail) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        String phone = "+244900" + Math.abs(slug.hashCode() % 1_000_000);

        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + slug)
                                .slug(slug)
                                .tenantCode(tenantCode)
                                .tipo(TenantTipo.VENDEDOR_RUA)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("VENDEDOR_RUA")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + slug)
                                .sigla(tenantCode)
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email(ownerEmail)
                                .telefone(phone)
                                .criarUsuario(true)
                                .build())
                        .build()
        );
    }
}

