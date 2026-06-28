package com.restaurante.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.SelectTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.OperationalEventLogRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.JwtTokenProvider;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.main.web-application-type=servlet",
        "jwt.secret=0123456789ABCDEF0123456789ABCDEF",
        "jwt.expiration=3600000"
})
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("it-postgres")
class PlatformTenantAccessIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository userRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired OperationalEventLogRepository operationalEventLogRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void platformAdmin_listsAllTenants_andTenantAdminCannotUsePlatformEndpoint() throws Exception {
        String suffix = suffix();
        User platformAdmin = createUser("platform-list-" + suffix + "@test.com", Role.ROLE_ADMIN);
        ProvisionarTenantResponse tenantA = provisionTenant(platformAdmin, "pta-a-" + suffix, "PTA" + suffix, "owner-pta-a-" + suffix + "@test.com");
        ProvisionarTenantResponse tenantB = provisionTenant(platformAdmin, "pta-b-" + suffix, "PTB" + suffix, "owner-pta-b-" + suffix + "@test.com");

        String adminToken = globalToken(platformAdmin, "ROLE_ADMIN");
        String resp = mockMvc.perform(get("/platform/tenants")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(resp).at("/data");
        assertThat(containsTenant(data, tenantA.getTenantId())).isTrue();
        assertThat(containsTenant(data, tenantB.getTenantId())).isTrue();
        assertThat(data.findValues("password")).isEmpty();

        User ownerA = userRepository.findById(tenantA.getOwnerUserId()).orElseThrow();
        String ownerToken = globalToken(ownerA, "ROLE_GERENTE");
        mockMvc.perform(get("/platform/tenants")
                        .header("Authorization", "Bearer " + ownerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformAdmin_seesProvisionedTenantInAuthTenants_selectsIt_andTenantTokenAccessesTenantEndpoints() throws Exception {
        String suffix = suffix();
        User platformAdmin = createUser("platform-select-" + suffix + "@test.com", Role.ROLE_ADMIN);
        ProvisionarTenantResponse provisioned = provisionTenant(platformAdmin, "pts-" + suffix, "PTS" + suffix, "owner-pts-" + suffix + "@test.com");

        String adminToken = globalToken(platformAdmin, "ROLE_ADMIN");
        String listResp = mockMvc.perform(get("/auth/tenants")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode authTenants = objectMapper.readTree(listResp).at("/data");
        assertThat(containsTenant(authTenants, provisioned.getTenantId())).isTrue();

        String selectResp = mockMvc.perform(post("/auth/tenant/select")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SelectTenantRequest(provisioned.getTenantId()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode selected = objectMapper.readTree(selectResp).at("/data");
        String tenantToken = selected.at("/accessToken").asText();
        assertThat(tenantToken).isNotBlank();
        var claims = jwtTokenProvider.getClaims(tenantToken);
        assertThat(claims.get("tenantId", Long.class)).isEqualTo(provisioned.getTenantId());
        assertThat(claims.get("platformAdmin", Boolean.class)).isTrue();
        @SuppressWarnings("unchecked")
        java.util.List<String> tenantRoles = (java.util.List<String>) claims.get("tenantRoles");
        assertThat(tenantRoles).contains(TenantUserRole.TENANT_ADMIN.name());

        mockMvc.perform(get("/tenant/cardapio/status")
                        .header("Authorization", "Bearer " + tenantToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        assertThat(operationalEventLogRepository.findByTenantIdAndEventType(
                provisioned.getTenantId(),
                OperationalEventType.PLATFORM_TENANT_CONTEXT_ASSUMED
        )).isNotEmpty();
    }

    @Test
    void tenantAdmin_onlyListsLinkedTenants_andCannotSelectUnlinkedTenant() throws Exception {
        String suffix = suffix();
        User platformAdmin = createUser("platform-common-" + suffix + "@test.com", Role.ROLE_ADMIN);
        ProvisionarTenantResponse tenantA = provisionTenant(platformAdmin, "ptc-a-" + suffix, "PCA" + suffix, "owner-ptc-a-" + suffix + "@test.com");
        ProvisionarTenantResponse tenantB = provisionTenant(platformAdmin, "ptc-b-" + suffix, "PCB" + suffix, "owner-ptc-b-" + suffix + "@test.com");

        User ownerA = userRepository.findById(tenantA.getOwnerUserId()).orElseThrow();
        String ownerToken = globalToken(ownerA, "ROLE_GERENTE");

        String listResp = mockMvc.perform(get("/auth/tenants")
                        .header("Authorization", "Bearer " + ownerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(listResp).at("/data");
        assertThat(containsTenant(data, tenantA.getTenantId())).isTrue();
        assertThat(containsTenant(data, tenantB.getTenantId())).isFalse();

        mockMvc.perform(post("/auth/tenant/select")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SelectTenantRequest(tenantB.getTenantId()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformTenantDetail_returnsAdministrativeContract_andPublicQrRemainsUnauthenticated() throws Exception {
        String suffix = suffix();
        User platformAdmin = createUser("platform-detail-" + suffix + "@test.com", Role.ROLE_ADMIN);
        ProvisionarTenantResponse provisioned = provisionTenant(platformAdmin, "ptd-" + suffix, "PTD" + suffix, "owner-ptd-" + suffix + "@test.com");

        String adminToken = globalToken(platformAdmin, "ROLE_ADMIN");
        String detailResp = mockMvc.perform(get("/platform/tenants/{tenantId}", provisioned.getTenantId())
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode tenant = objectMapper.readTree(detailResp).at("/data");
        assertThat(tenant.at("/tenantId").asLong()).isEqualTo(provisioned.getTenantId());
        assertThat(tenant.at("/tenantCode").asText()).isEqualTo(provisioned.getTenantCode());
        assertThat(tenant.has("cardapio")).isTrue();
        assertThat(tenant.has("selecionavel")).isTrue();

        mockMvc.perform(get("/public/q/{token}/cardapio", "token-inexistente-" + suffix)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    }

    private ProvisionarTenantResponse provisionTenant(User platformAdmin, String slug, String tenantCode, String ownerEmail) {
        TenantContextHolder.set(new TenantContext(
                null, null, platformAdmin.getId(), Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        String code = tenantCode.replaceAll("[^A-Z0-9]", "");
        if (code.length() > 20) code = code.substring(0, 20);
        String phone = "+24493" + String.format("%07d", Math.abs(slug.hashCode() % 10_000_000L));

        ProvisionarTenantResponse response = provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + slug)
                                .slug(slug)
                                .tenantCode(code)
                                .tipo(TenantTipo.RESTAURANTE)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("RESTAURANTE_SIMPLES")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + slug)
                                .sigla(code.substring(0, Math.min(4, code.length())))
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email(ownerEmail)
                                .telefone(phone)
                                .criarUsuario(true)
                                .build())
                        .opcoes(ProvisionarTenantRequest.OpcoesProvisionamento.builder()
                                .criarQrPrincipal(true)
                                .criarMesas(false)
                                .criarQrPorMesa(false)
                                .build())
                        .build()
        );
        TenantContextHolder.clear();
        return response;
    }

    private User createUser(String email, Role role) {
        User user = new User();
        user.setUsername(email);
        user.setPassword("x");
        user.setEmail(email);
        user.setTelefone("+24494" + String.format("%07d", Math.abs(email.hashCode() % 10_000_000L)));
        user.setRoles(Set.of(role));
        user.setAtivo(true);
        return userRepository.saveAndFlush(user);
    }

    private String globalToken(User user, String roles) {
        return jwtTokenProvider.generateToken(user.getUsername(), roles, null, user.getId(), "GLOBAL");
    }

    private boolean containsTenant(JsonNode data, Long tenantId) {
        for (JsonNode item : data) {
            if (item.at("/tenantId").asLong() == tenantId) {
                return true;
            }
        }
        return false;
    }

    private String suffix() {
        return String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
    }
}
