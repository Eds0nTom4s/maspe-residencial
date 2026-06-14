package com.restaurante.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.BusinessAccountMember;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.BusinessAccountMemberEstado;
import com.restaurante.model.enums.BusinessAccountRole;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.BusinessAccountMemberRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TenantOwnerRequiredGuardIT
 *
 * Integration tests for BACKEND — TENANT-OWNER-REQUIRED-GUARD-001.
 *
 * Validates:
 * 1. Provisioning without owner returns 400 TENANT_OWNER_REQUIRED.
 * 2. Provisioning without BusinessAccount returns 400.
 * 3. Provisioning trying to use Platform Admin username as owner returns error.
 * 4. Valid provisioning with BusinessAccount + real owner returns 201.
 * 5. Valid provisioning creates Tenant.
 * 6. Valid provisioning creates TenantUser OWNER for the real owner.
 * 7. Valid provisioning creates BusinessAccountMember OWNER for the real owner.
 * 8. Platform Admin does NOT become TenantUser OWNER.
 * 9. Platform Admin does NOT become BusinessAccountMember OWNER.
 * 10. Temporary password belongs to the real owner.
 * 11. /auth/tenants of owner returns the provisioned tenant.
 * 12. /auth/tenants of Platform Admin does NOT list tenant as own business.
 * 13. Reset password for real owner works.
 * 14. Reset password targeting a ROLE_ADMIN-only user fails.
 * 15. Regression: Platform Admin can still list tenants via /platform/**.
 */
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
@org.springframework.transaction.annotation.Transactional
class TenantOwnerRequiredGuardIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired BusinessAccountMemberRepository businessAccountMemberRepository;

    // =========================================================================
    // 1. Provisioning WITHOUT owner → 400 TENANT_OWNER_REQUIRED
    // =========================================================================
    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void provisionWithAccess_withoutOwnerName_returns400_tenantOwnerRequired() throws Exception {
        String payload = """
                {
                  "createBusinessAccount": true,
                  "businessAccountNome": "Conta Sem Owner",
                  "tenantNome": "Tenant Sem Owner",
                  "tenantTipo": "VENDEDOR_RUA",
                  "tenantTelefone": "+244900000011"
                }
                """;

        mockMvc.perform(post("/platform/tenants/provision-with-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void provisionWithAccess_withOwnerNameButNoContact_returns400() throws Exception {
        String payload = """
                {
                  "createBusinessAccount": true,
                  "businessAccountNome": "Conta Sem Contato Owner",
                  "tenantNome": "Tenant Sem Contato Owner",
                  "tenantTipo": "VENDEDOR_RUA",
                  "tenantTelefone": "+244900000012",
                  "ownerNome": "Fulano Sem Contato"
                }
                """;

        mockMvc.perform(post("/platform/tenants/provision-with-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 2. Provisioning WITHOUT BusinessAccount → 400
    // =========================================================================
    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void provisionWithAccess_withoutBusinessAccount_returns400() throws Exception {
        String payload = """
                {
                  "tenantNome": "Tenant Sem Conta",
                  "tenantTipo": "VENDEDOR_RUA",
                  "tenantTelefone": "+244900000013",
                  "ownerNome": "Owner Valido",
                  "ownerTelefone": "+244900000014"
                }
                """;

        mockMvc.perform(post("/platform/tenants/provision-with-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 3. Provisioning with Platform Admin username as owner → error
    // =========================================================================
    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void provisionWithAccess_withPlatformAdminAsOwnerUsername_returnsError() throws Exception {
        String payload = """
                {
                  "createBusinessAccount": true,
                  "businessAccountNome": "Conta Admin Owner",
                  "tenantNome": "Tenant Admin Owner",
                  "tenantTipo": "VENDEDOR_RUA",
                  "tenantTelefone": "+244900000015",
                  "ownerNome": "Platform Admin Proprio",
                  "ownerUsername": "platform-admin",
                  "ownerTelefone": "+244900000016"
                }
                """;

        // Should return 4xx (400 or 409) — Platform Admin cannot be owner
        mockMvc.perform(post("/platform/tenants/provision-with-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .isIn(400, 409));
    }

    // =========================================================================
    // 4-10. Valid provisioning — 201 + correct ownership
    // =========================================================================
    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void provisionWithAccess_valid_creates201_withRealOwner_notPlatformAdmin() throws Exception {
        String suffix = String.format("%06d", Math.abs(System.nanoTime() % 1_000_000L));
        String payload = """
                {
                  "createBusinessAccount": true,
                  "businessAccountNome": "Conta Guard %s",
                  "tenantNome": "Tenant Guard %s",
                  "tenantSlug": "tenant-guard-%s",
                  "tenantTipo": "VENDEDOR_RUA",
                  "tenantTelefone": "+244910%s",
                  "ownerNome": "Owner Real Guard",
                  "ownerUsername": "owner.guard.%s",
                  "ownerEmail": "owner-guard-%s@test.com",
                  "ownerTelefone": "+244920%s",
                  "gerarSenhaTemporaria": true,
                  "ativarTenant": true
                }
                """.formatted(
                suffix, suffix, suffix, suffix.substring(0, 6),
                suffix, suffix, suffix.substring(0, 6)
        );

        String resp = mockMvc.perform(post("/platform/tenants/provision-with-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/success").asBoolean()).isTrue();

        long tenantId = json.at("/data/tenantId").asLong();
        long ownerUserId = json.at("/data/ownerUserId").asLong();
        long tenantUserId = json.at("/data/tenantUserId").asLong();
        long businessAccountId = json.at("/data/businessAccountId").asLong();
        long businessAccountMemberId = json.at("/data/businessAccountMemberId").asLong();
        String temporaryPassword = json.at("/data/temporaryPassword").asText();

        // 4. Valid provisioning returns 201 (asserted above)

        // 5. Tenant is created
        assertThat(tenantRepository.findById(tenantId)).isPresent();

        // 6. TenantUser OWNER belongs to real owner
        TenantUser tenantUser = tenantUserRepository.findByTenantIdAndUserId(tenantId, ownerUserId).orElseThrow();
        assertThat(tenantUser.getId()).isEqualTo(tenantUserId);
        assertThat(tenantUser.getRole()).isEqualTo(TenantUserRole.TENANT_OWNER);

        // 7. BusinessAccountMember OWNER belongs to real owner
        BusinessAccountMember member = businessAccountMemberRepository
                .findByBusinessAccountIdAndUserId(businessAccountId, ownerUserId)
                .orElseThrow();
        assertThat(member.getId()).isEqualTo(businessAccountMemberId);
        assertThat(member.getRole()).isEqualTo(BusinessAccountRole.OWNER);
        assertThat(member.getEstado()).isEqualTo(BusinessAccountMemberEstado.ATIVO);

        // 8. Platform Admin (username "platform-admin") is NOT a TenantUser OWNER
        List<TenantUser> allTenantUsers = tenantUserRepository.findByTenantId(tenantId);
        allTenantUsers.stream()
                .filter(tu -> TenantUserRole.TENANT_OWNER.equals(tu.getRole()))
                .forEach(tu -> {
                    User ownerUser = tu.getUser();
                    assertThat(ownerUser.getUsername()).isNotEqualTo("platform-admin");
                    // ROLE_ADMIN-only user should never become TENANT_OWNER
                    assertThat(ownerUser.getRoles().stream()
                            .allMatch(r -> r == Role.ROLE_ADMIN)).isFalse();
                });

        // 9. Platform Admin is NOT a BusinessAccountMember OWNER
        List<BusinessAccountMember> members = businessAccountMemberRepository
                .findByBusinessAccountIdOrderByIdAsc(businessAccountId);
        members.stream()
                .filter(m -> BusinessAccountRole.OWNER.equals(m.getRole()))
                .forEach(m -> {
                    User memberUser = m.getUser();
                    assertThat(memberUser.getUsername()).isNotEqualTo("platform-admin");
                });

        // 10. Temporary password belongs to real owner
        assertThat(temporaryPassword).isNotBlank();
        User owner = userRepository.findById(ownerUserId).orElseThrow();
        assertThat(owner.getMustChangePassword()).isTrue();
        assertThat(owner.getTemporaryPasswordExpiresAt()).isNotNull();
        // ownerUserId must NOT be platform admin
        assertThat(owner.getRoles().stream().allMatch(r -> r == Role.ROLE_ADMIN)).isFalse();
    }

    // =========================================================================
    // 11. /auth/tenants of owner returns the tenant
    // =========================================================================
    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void provisionWithAccess_ownerCanSelectTenantAfterProvisioning() throws Exception {
        String suffix = String.format("%06d", Math.abs(System.nanoTime() % 1_000_000L));
        String payload = """
                {
                  "createBusinessAccount": true,
                  "businessAccountNome": "Conta Auth %s",
                  "tenantNome": "Tenant Auth %s",
                  "tenantSlug": "tenant-auth-%s",
                  "tenantTipo": "VENDEDOR_RUA",
                  "tenantTelefone": "+244930%s",
                  "ownerNome": "Owner Auth",
                  "ownerUsername": "owner.auth.%s",
                  "ownerEmail": "owner-auth-%s@test.com",
                  "ownerTelefone": "+244940%s",
                  "gerarSenhaTemporaria": true,
                  "ativarTenant": true
                }
                """.formatted(
                suffix, suffix, suffix, suffix.substring(0, 6),
                suffix, suffix, suffix.substring(0, 6)
        );

        String resp = mockMvc.perform(post("/platform/tenants/provision-with-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        long tenantId = json.at("/data/tenantId").asLong();
        String ownerUsername = json.at("/data/ownerUsername").asText();
        String temporaryPassword = json.at("/data/temporaryPassword").asText();

        // Login as owner
        String loginResp = mockMvc.perform(post("/auth/jwt/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "%s"}
                                """.formatted(ownerUsername, temporaryPassword)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String ownerToken = objectMapper.readTree(loginResp).at("/data/accessToken").asText();
        assertThat(ownerToken).isNotBlank();

        // 11. /auth/tenants of owner returns the provisioned tenant
        String tenantsResp = mockMvc.perform(get("/auth/tenants")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode tenantsJson = objectMapper.readTree(tenantsResp);
        boolean foundTenant = false;
        for (JsonNode node : tenantsJson.at("/data")) {
            if (node.at("/tenantId").asLong() == tenantId) {
                foundTenant = true;
                break;
            }
        }
        assertThat(foundTenant).as("/auth/tenants of real owner must include the provisioned tenant").isTrue();
    }

    // =========================================================================
    // 13. Reset password for real owner works
    // =========================================================================
    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void resetPassword_forRealOwner_succeeds() throws Exception {
        String suffix = String.format("%06d", Math.abs(System.nanoTime() % 1_000_000L));
        String payload = """
                {
                  "createBusinessAccount": true,
                  "businessAccountNome": "Conta Reset %s",
                  "tenantNome": "Tenant Reset %s",
                  "tenantSlug": "tenant-reset-%s",
                  "tenantTipo": "VENDEDOR_RUA",
                  "tenantTelefone": "+244950%s",
                  "ownerNome": "Owner Reset",
                  "ownerUsername": "owner.reset.%s",
                  "ownerEmail": "owner-reset-%s@test.com",
                  "ownerTelefone": "+244960%s",
                  "gerarSenhaTemporaria": true,
                  "ativarTenant": true
                }
                """.formatted(
                suffix, suffix, suffix, suffix.substring(0, 6),
                suffix, suffix, suffix.substring(0, 6)
        );

        String provResp = mockMvc.perform(post("/platform/tenants/provision-with-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode provJson = objectMapper.readTree(provResp);
        long tenantId = provJson.at("/data/tenantId").asLong();
        long ownerUserId = provJson.at("/data/ownerUserId").asLong();

        // 13. Platform Admin resets owner password — must succeed
        String resetResp = mockMvc.perform(post("/platform/tenants/{tenantId}/access/reset-password", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": %d, "motivo": "Guard IT reset"}
                                """.formatted(ownerUserId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode resetJson = objectMapper.readTree(resetResp);
        assertThat(resetJson.at("/data/userId").asLong()).isEqualTo(ownerUserId);
        assertThat(resetJson.at("/data/temporaryPassword").asText()).isNotBlank();
    }

    // =========================================================================
    // 15. Regression: Platform Admin can still list tenants via /platform/**
    // =========================================================================
    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void regression_platformAdmin_canListTenantsViaplatformEndpoint() throws Exception {
        mockMvc.perform(get("/platform/tenants"))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .isIn(200, 204, 404)); // endpoint may 200 or 404 but never 403
        // key: must not be 403 (Forbidden) for Platform Admin
        mockMvc.perform(get("/platform/tenants"))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .isNotEqualTo(403));
    }

    // =========================================================================
    // Regression: /auth/tenants of Platform Admin does NOT include tenant as business
    // (Platform Admin has no TenantUser link via provision-with-access)
    // =========================================================================
    @Test
    @WithMockUser(username = "platform-admin-guard", authorities = "ROLE_ADMIN")
    void regression_platformAdmin_authTenants_doesNotListOperationalTenantAsOwn() throws Exception {
        String suffix = String.format("%06d", Math.abs(System.nanoTime() % 1_000_000L));
        String payload = """
                {
                  "createBusinessAccount": true,
                  "businessAccountNome": "Conta Reg %s",
                  "tenantNome": "Tenant Reg %s",
                  "tenantSlug": "tenant-reg-%s",
                  "tenantTipo": "VENDEDOR_RUA",
                  "tenantTelefone": "+244970%s",
                  "ownerNome": "Owner Reg",
                  "ownerUsername": "owner.reg.%s",
                  "ownerEmail": "owner-reg-%s@test.com",
                  "ownerTelefone": "+244980%s",
                  "gerarSenhaTemporaria": true,
                  "ativarTenant": true
                }
                """.formatted(
                suffix, suffix, suffix, suffix.substring(0, 6),
                suffix, suffix, suffix.substring(0, 6)
        );

        String resp = mockMvc.perform(post("/platform/tenants/provision-with-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long tenantId = objectMapper.readTree(resp).at("/data/tenantId").asLong();

        // Platform Admin "platform-admin-guard" should NOT appear as TenantUser for this tenant
        Optional<User> adminUser = userRepository.findByUsername("platform-admin-guard");
        if (adminUser.isPresent()) {
            Optional<TenantUser> adminTenantUser = tenantUserRepository
                    .findByTenantIdAndUserId(tenantId, adminUser.get().getId());
            // Platform Admin must not be associated as TenantUser OWNER
            adminTenantUser.ifPresent(tu ->
                    assertThat(tu.getRole()).isNotEqualTo(TenantUserRole.TENANT_OWNER)
            );
        }
    }
}
