package com.restaurante.provisioning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.Role;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.JwtTokenProvider;
import com.restaurante.security.tenant.TenantContextHolder;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.main.web-application-type=servlet",
        "jwt.secret=0123456789ABCDEF0123456789ABCDEF",
        "jwt.expiration=3600000"
})
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("it-postgres")
class FullStackFirstLoginHappyPathIT extends PostgresTestcontainersConfig {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider tokens;

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void provisionedCredentialsReachTenantScopedApplication() throws Exception {
        String suffix = Long.toString(Math.abs(System.nanoTime() % 1_000_000));
        User admin = new User();
        admin.setUsername("platform-happy-" + suffix);
        admin.setPassword("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
        admin.setEmail("platform-happy-" + suffix + "@example.test");
        admin.setNomeCompleto("Platform Happy Path");
        admin.setTelefone("+24499" + String.format("%07d", Long.parseLong(suffix)));
        admin.setRoles(Set.of(Role.ROLE_ADMIN));
        admin.setAtivo(true);
        admin = users.saveAndFlush(admin);
        String adminToken = tokens.generateToken(admin.getUsername(), "ROLE_ADMIN", null, admin.getId(), "GLOBAL");
        mockMvc.perform(get("/does-not-exist").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        String ownerUsername = "owner.happy." + suffix;
        String ownerPhone = "+24498" + String.format("%07d", Long.parseLong(suffix));
        String accountPayload = """
                {"nome":"Conta Happy %s","slug":"account-happy-%s","maxTenants":1,
                 "responsavelPrincipal":{"strategy":"CREATE_NEW","username":"%s",
                 "temporaryPassword":"abcde","nome":"Owner Happy %s",
                 "email":"owner-happy-%s@example.test","telefone":"%s"}}
                """.formatted(suffix, suffix, ownerUsername, suffix, suffix, ownerPhone);

        JsonNode account = data(command(adminToken, "/platform/business-accounts", accountPayload, status().isCreated()));
        long accountId = account.path("id").asLong();
        assertThat(account.path("responsavelUserId").asLong()).isPositive();
        assertThat(account.path("memberCount").asLong()).isEqualTo(1);

        JsonNode activeAccount = data(command(adminToken, "/platform/business-accounts/" + accountId + "/activate",
                "{\"accountVersion\":" + account.path("version").asLong() + ",\"reason\":\"Happy path test\"}", status().isOk()));
        long accountVersion = activeAccount.path("version").asLong();

        String tenantCode = "HP" + suffix;
        String previewPayload = """
                {"accountVersion":%d,"planoCodigo":"PILOTO","vertical":"CONSUMA_PONTO",
                 "negocio":{"nomeNegocio":"Negócio Happy %s","slug":"business-happy-%s",
                 "tenantCode":"%s","tipo":"VENDEDOR_RUA","telefone":"%s"},
                 "ponto":{"entregaManual":false,"allowPickup":true},
                 "acessos":{"strategy":"ACCOUNT_OWNER_AS_TENANT_OWNER","additionalAccesses":[]}}
                """.formatted(accountVersion, suffix, suffix, tenantCode, ownerPhone);
        JsonNode preview = data(command(adminToken,
                "/platform/business-accounts/" + accountId + "/businesses/preview", previewPayload, status().isOk()));
        assertThat(preview.path("allowedToProvision").asBoolean()).isTrue();

        String provisionPayload = "{\"previewId\":\"" + preview.path("previewId").asText()
                + "\",\"requestFingerprint\":\"" + preview.path("requestFingerprint").asText()
                + "\",\"accountVersion\":" + accountVersion + ",\"confirmed\":true}";
        JsonNode provision = data(command(adminToken,
                "/platform/business-accounts/" + accountId + "/businesses/provision", provisionPayload, status().isCreated()));
        long tenantId = provision.path("tenantId").asLong();
        assertThat(tenantId).isPositive();
        assertThat(provision.path("status").asText()).isEqualTo("SUCCEEDED");

        JsonNode readiness = data(mockMvc.perform(get("/platform/business-accounts/{accountId}/businesses/{tenantId}/readiness", accountId, tenantId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(readiness.path("ready").asBoolean()).isTrue();
        JsonNode activated = data(command(adminToken,
                "/platform/business-accounts/" + accountId + "/businesses/" + tenantId + "/activate",
                "{\"accountVersion\":" + readiness.path("accountVersion").asLong()
                        + ",\"tenantVersion\":" + readiness.path("tenantVersion").asLong()
                        + ",\"reason\":\"Happy path activation\"}", status().isOk()));
        assertThat(activated.path("estado").asText()).isEqualTo("ATIVO");

        User outsider = new User();
        outsider.setUsername("outsider-happy-" + suffix);
        outsider.setPassword("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
        outsider.setEmail("outsider-happy-" + suffix + "@example.test");
        outsider.setNomeCompleto("Outsider Happy Path");
        outsider.setTelefone("+24495" + String.format("%07d", Long.parseLong(suffix)));
        outsider.setRoles(Set.of());
        outsider.setAtivo(true);
        outsider = users.saveAndFlush(outsider);
        String outsiderToken = tokens.generateUserToken(outsider);
        mockMvc.perform(get("/auth/tenants").header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(post("/auth/tenant/select")
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + "}"))
                .andExpect(status().isForbidden());

        JsonNode login = data(mockMvc.perform(post("/auth/jwt/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ownerUsername + "\",\"password\":\"abcde\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString());
        String temporaryToken = login.path("accessToken").asText();
        mockMvc.perform(get("/auth/tenants").header("Authorization", "Bearer " + temporaryToken))
                .andExpect(status().isForbidden());

        JsonNode changed = data(mockMvc.perform(post("/auth/password/change")
                        .header("Authorization", "Bearer " + temporaryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"abcde\",\"newPassword\":\"Owner12345\",\"confirmPassword\":\"Owner12345\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andReturn().getResponse().getContentAsString());
        String globalToken = changed.path("accessToken").asText();

        JsonNode tenantOptions = data(mockMvc.perform(get("/auth/tenants")
                        .header("Authorization", "Bearer " + globalToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(tenantOptions).anySatisfy(option -> assertThat(option.path("tenantId").asLong()).isEqualTo(tenantId));

        JsonNode selected = data(mockMvc.perform(post("/auth/tenant/select")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tenantId\":" + tenantId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(selected.path("tenantId").asLong()).isEqualTo(tenantId);
        mockMvc.perform(get("/tenant/auditoria").header("Authorization", "Bearer " + selected.path("accessToken").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void temporaryPasswordContractRejectsFourCharacters() throws Exception {
        String suffix = Long.toString(Math.abs(System.nanoTime() % 1_000_000));
        User admin = new User();
        admin.setUsername("platform-min-" + suffix);
        admin.setPassword("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
        admin.setEmail("platform-min-" + suffix + "@example.test");
        admin.setNomeCompleto("Platform Minimum");
        admin.setTelefone("+24497" + String.format("%07d", Long.parseLong(suffix)));
        admin.setRoles(Set.of(Role.ROLE_ADMIN));
        admin.setAtivo(true);
        admin = users.saveAndFlush(admin);
        String token = tokens.generateToken(admin.getUsername(), "ROLE_ADMIN", null, admin.getId(), "GLOBAL");
        command(token, "/platform/business-accounts", """
                {"nome":"Conta Inválida","slug":"account-invalid-%s","responsavelPrincipal":{
                "strategy":"CREATE_NEW","username":"owner.invalid.%s","temporaryPassword":"abcd",
                "nome":"Owner Invalid","telefone":"+24496%s"}}
                """.formatted(suffix, suffix, String.format("%07d", Long.parseLong(suffix))), status().isBadRequest());
    }

    private String command(String token, String path, String payload,
                           org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        return mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Correlation-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(expected).andReturn().getResponse().getContentAsString();
    }

    private JsonNode data(String body) throws Exception {
        return json.readTree(body).path("data");
    }
}
