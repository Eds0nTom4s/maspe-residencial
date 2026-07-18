package com.restaurante.platform;

import com.restaurante.model.entity.User;
import com.restaurante.model.enums.Role;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.JwtTokenProvider;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.main.web-application-type=servlet",
        "jwt.secret=0123456789ABCDEF0123456789ABCDEF",
        "jwt.expiration=3600000",
        "cors.allowed-origins=https://frontend.example.test"
})
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("it-postgres")
class PlatformSecurityAuthorizationIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @Test
    void canonicalPlatformEndpointsReturn401And403WithoutProtectedData() throws Exception {
        String suffix = Long.toString(Math.abs(System.nanoTime()));
        User nonAdmin = new User();
        nonAdmin.setUsername("non-admin-" + suffix + "@test.local");
        nonAdmin.setPassword("x");
        nonAdmin.setNomeCompleto("Non Admin " + suffix);
        nonAdmin.setEmail(nonAdmin.getUsername());
        nonAdmin.setTelefone("+24491" + suffix.substring(Math.max(0, suffix.length() - 7)));
        nonAdmin.setAtivo(true);
        nonAdmin.setRoles(Set.of(Role.ROLE_GERENTE));
        nonAdmin = users.saveAndFlush(nonAdmin);
        String nonAdminToken = jwtTokenProvider.generateToken(nonAdmin.getUsername(), "ROLE_GERENTE",
                null, nonAdmin.getId(), "GLOBAL");

        for (Supplier<MockHttpServletRequestBuilder> endpoint : protectedEndpoints()) {
            var unauthenticated = mockMvc.perform(endpoint.get()).andReturn().getResponse();
            assertThat(unauthenticated.getStatus()).isEqualTo(401);
            assertSafeSecurityBody(unauthenticated.getContentAsString());

            var invalidResult = mockMvc.perform(endpoint.get().header("Authorization", "Bearer invalid.jwt.token"))
                    .andReturn().getResponse();
            assertThat(invalidResult.getStatus()).isEqualTo(401);
            assertSafeSecurityBody(invalidResult.getContentAsString());

            var forbiddenResult = mockMvc.perform(endpoint.get().header("Authorization", "Bearer " + nonAdminToken))
                    .andReturn().getResponse();
            assertThat(forbiddenResult.getStatus()).isEqualTo(403);
            assertSafeSecurityBody(forbiddenResult.getContentAsString());
        }
    }

    @Test
    void platformAdminPassesAuthorizationLayerAndMissingResourcesAreNever500() throws Exception {
        String suffix = Long.toString(Math.abs(System.nanoTime()));
        User admin = new User();
        admin.setUsername("admin-authz-" + suffix + "@test.local");
        admin.setPassword("x");
        admin.setNomeCompleto("Admin " + suffix);
        admin.setEmail(admin.getUsername());
        admin.setTelefone("+24492" + suffix.substring(Math.max(0, suffix.length() - 7)));
        admin.setAtivo(true);
        admin.setRoles(Set.of(Role.ROLE_ADMIN));
        admin = users.saveAndFlush(admin);
        String adminToken = jwtTokenProvider.generateToken(admin.getUsername(), "ROLE_ADMIN",
                null, admin.getId(), "GLOBAL");

        for (Supplier<MockHttpServletRequestBuilder> endpoint : protectedEndpoints()) {
            int actual = mockMvc.perform(endpoint.get().header("Authorization", "Bearer " + adminToken))
                    .andReturn().getResponse().getStatus();
            assertThat(actual).isNotIn(401, 403, 500);
        }
    }

    private List<Supplier<MockHttpServletRequestBuilder>> protectedEndpoints() {
        long missing = Long.MAX_VALUE;
        String accountActivation = "{\"accountVersion\":0,\"reason\":\"teste de autorização\"}";
        String businessActivation = "{\"accountVersion\":0,\"tenantVersion\":0,\"reason\":\"teste de autorização\"}";
        return List.of(
                () -> get("/platform/business-accounts/{id}", missing),
                () -> get("/platform/tenants/{tenantId}", missing),
                () -> get("/platform/business-accounts/{accountId}/businesses/{tenantId}/readiness", missing, missing),
                () -> post("/platform/business-accounts/{id}/activate", missing)
                        .header("Idempotency-Key", "authz-account-key")
                        .header("X-Correlation-Id", "authz-account-correlation")
                        .contentType(MediaType.APPLICATION_JSON).content(accountActivation),
                () -> post("/platform/business-accounts/{accountId}/businesses/{tenantId}/activate", missing, missing)
                        .header("Idempotency-Key", "authz-business-key")
                        .header("X-Correlation-Id", "authz-business-correlation")
                        .contentType(MediaType.APPLICATION_JSON).content(businessActivation),
                () -> get("/platform/provisioning-operations/{operationId}", "missing-operation")
        );
    }

    private void assertSafeSecurityBody(String body) {
        assertThat(body)
                .doesNotContain("Authorization", "Bearer", "invalid.jwt.token", "org.springframework",
                        "com.restaurante", "SELECT ", "business_accounts", "stackTrace", ".java:");
    }
}
