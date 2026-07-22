package com.restaurante.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.SelectTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.User;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserEstado;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.web-application-type=servlet",
                "jwt.secret=0123456789ABCDEF0123456789ABCDEF",
                "jwt.expiration=3600000"
        }
)
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("it-postgres")
class AuthTenantSelectIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired com.restaurante.repository.UserRepository userRepository;
    @Autowired com.restaurante.repository.TenantRepository tenantRepository;
    @Autowired com.restaurante.repository.TenantUserRepository tenantUserRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void selectTenant_requiresAuthentication() throws Exception {
        mockMvc.perform(post("/auth/tenant/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SelectTenantRequest(1L))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void selectTenant_returnsTenantScopedTokenWithClaims() throws Exception {
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
        ProvisionarTenantResponse provisioned = provisionTenant("banca-a-" + suffix, "BA" + suffix, "owner-a-" + suffix + "@a.com");

        User owner = userRepository.findById(provisioned.getOwnerUserId()).orElseThrow();
        String globalToken = jwtTokenProvider.generateToken(
                owner.getUsername(), "ROLE_GERENTE", null, owner.getId(), "GLOBAL");

        String resp = mockMvc.perform(post("/auth/tenant/select")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SelectTenantRequest(provisioned.getTenantId()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/success").asBoolean()).isTrue();
        assertThat(json.at("/data/tenantId").asLong()).isEqualTo(provisioned.getTenantId());
        assertThat(json.at("/data/tenantCode").asText()).isEqualTo(provisioned.getTenantCode());
        assertThat(json.at("/data/tokenType").asText()).isEqualTo("Bearer");
        assertThat(json.at("/data/accessToken").asText()).isNotBlank();

        String tenantToken = json.at("/data/accessToken").asText();
        var claims = jwtTokenProvider.getClaims(tenantToken);
        assertThat(claims.get("tokenType", String.class)).isEqualTo("TENANT");
        assertThat(claims.get("tenantId", Long.class)).isEqualTo(provisioned.getTenantId());
        assertThat(claims.get("tenantCode", String.class)).isEqualTo(provisioned.getTenantCode());
        @SuppressWarnings("unchecked")
        java.util.List<String> tenantRoles = (java.util.List<String>) claims.get("tenantRoles");
        assertThat(tenantRoles).contains("TENANT_OWNER");
        assertThat(claims.get("userId", Long.class)).isEqualTo(provisioned.getOwnerUserId());
    }

    @Test
    void selectTenant_deniesWhenUserNotMemberOfTenant() throws Exception {
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
        ProvisionarTenantResponse tenantA = provisionTenant("banca-a2-" + suffix, "BA2" + suffix, "owner-a2-" + suffix + "@a.com");
        ProvisionarTenantResponse tenantB = provisionTenant("banca-b2-" + suffix, "BB2" + suffix, "owner-b2-" + suffix + "@b.com");

        User ownerA = userRepository.findById(tenantA.getOwnerUserId()).orElseThrow();
        String globalTokenA = jwtTokenProvider.generateToken(
                ownerA.getUsername(), "ROLE_GERENTE", null, ownerA.getId(), "GLOBAL");

        String resp = mockMvc.perform(post("/auth/tenant/select")
                        .header("Authorization", "Bearer " + globalTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SelectTenantRequest(tenantB.getTenantId()))))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/success").asBoolean()).isFalse();
    }

    @Test
    void selectTenant_multiRoleEmiteTodasAsRolesActivasERejeitaTrocaComJwtTenant() throws Exception {
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
        ProvisionarTenantResponse provisioned = provisionTenant(
                "multi-select-" + suffix,
                "MS" + suffix,
                "owner-ms-" + suffix + "@a.com");
        User owner = userRepository.findById(provisioned.getOwnerUserId()).orElseThrow();
        addRole(provisioned.getTenantId(), owner, TenantUserRole.TENANT_FINANCE, TenantUserEstado.ATIVO);
        addRole(provisioned.getTenantId(), owner, TenantUserRole.TENANT_CASHIER, TenantUserEstado.ATIVO);
        addRole(provisioned.getTenantId(), owner, TenantUserRole.TENANT_ADMIN, TenantUserEstado.SUSPENSO);
        String globalToken = jwtTokenProvider.generateToken(
                owner.getUsername(), "ROLE_GERENTE", null, owner.getId(), "GLOBAL");

        String response = mockMvc.perform(post("/auth/tenant/select")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SelectTenantRequest(provisioned.getTenantId()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(objectMapper.convertValue(data.path("roles"), java.util.List.class))
                .containsExactly("TENANT_CASHIER", "TENANT_FINANCE", "TENANT_OWNER");
        String tenantToken = data.path("accessToken").asText();
        @SuppressWarnings("unchecked")
        java.util.List<String> tokenRoles = (java.util.List<String>) jwtTokenProvider.getClaims(tenantToken)
                .get("tenantRoles");
        assertThat(tokenRoles).containsExactly("TENANT_CASHIER", "TENANT_FINANCE", "TENANT_OWNER");

        String wrongScope = mockMvc.perform(post("/auth/tenant/select")
                        .header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SelectTenantRequest(provisioned.getTenantId()))))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(wrongScope).path("code").asText())
                .isEqualTo("TOKEN_SCOPE_INVALID");
    }

    private ProvisionarTenantResponse provisionTenant(String slug, String tenantCode, String ownerEmail) {
        // Simula PLATFORM_ADMIN para provisionar via service diretamente.
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

    private void addRole(Long tenantId, User user, TenantUserRole role, TenantUserEstado estado) {
        TenantUser row = new TenantUser();
        row.setTenant(tenantRepository.findById(tenantId).orElseThrow());
        row.setUser(user);
        row.setRole(role);
        row.setEstado(estado);
        tenantUserRepository.saveAndFlush(row);
    }
}
