package com.restaurante.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.User;
import com.restaurante.repository.UserRepository;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
@org.springframework.transaction.annotation.Transactional
class PlatformUserPasswordManagementIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void ownerWithTemporaryPassword_canChangeFirstPassword_andContinueTenantFlow() throws Exception {
        ProvisionedOwner owner = provisionOwner("change");
        String loginResp = login(owner.username(), owner.temporaryPassword(), status().isOk());
        JsonNode loginJson = objectMapper.readTree(loginResp);

        assertThat(loginJson.at("/data/mustChangePassword").asBoolean()).isTrue();
        assertThat(loginJson.at("/data/passwordResetRequired").asBoolean()).isTrue();
        assertThat(loginJson.at("/data/temporaryPasswordExpiresAt").asText()).isNotBlank();
        String globalToken = loginJson.at("/data/accessToken").asText();
        assertThat(globalToken).isNotBlank();

        String newPassword = "NovaSenha@123";
        String changeResp = mockMvc.perform(post("/auth/password/change")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "%s",
                                  "newPassword": "%s",
                                  "confirmPassword": "%s"
                                }
                                """.formatted(owner.temporaryPassword(), newPassword, newPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andExpect(jsonPath("$.data.passwordResetRequired").value(false))
                .andReturn().getResponse().getContentAsString();

        JsonNode changeJson = objectMapper.readTree(changeResp);
        assertThat(changeJson.at("/data/lastPasswordChangedAt").asText()).isNotBlank();

        User persisted = userRepository.findById(owner.userId()).orElseThrow();
        assertThat(persisted.getMustChangePassword()).isFalse();
        assertThat(persisted.getPasswordResetRequired()).isFalse();
        assertThat(persisted.getTemporaryPasswordExpiresAt()).isNull();
        assertThat(persisted.getLastPasswordChangedAt()).isNotNull();
        assertThat(passwordEncoder.matches(owner.temporaryPassword(), persisted.getPassword())).isFalse();
        assertThat(passwordEncoder.matches(newPassword, persisted.getPassword())).isTrue();

        login(owner.username(), owner.temporaryPassword(), status().isBadRequest());
        String newLoginResp = login(owner.username(), newPassword, status().isOk());
        JsonNode newLoginJson = objectMapper.readTree(newLoginResp);
        assertThat(newLoginJson.at("/data/mustChangePassword").asBoolean()).isFalse();
        assertThat(newLoginJson.at("/data/passwordResetRequired").asBoolean()).isFalse();
        assertThat(newLoginJson.at("/data/temporaryPasswordExpiresAt").isMissingNode()
                || newLoginJson.at("/data/temporaryPasswordExpiresAt").isNull()).isTrue();

        String newGlobalToken = newLoginJson.at("/data/accessToken").asText();
        mockMvc.perform(get("/auth/tenants")
                        .header("Authorization", "Bearer " + newGlobalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tenantId").value(owner.tenantId()));

        String selectResp = mockMvc.perform(post("/auth/tenant/select")
                        .header("Authorization", "Bearer " + newGlobalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "tenantId": %d }
                                """.formatted(owner.tenantId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String tenantToken = objectMapper.readTree(selectResp).at("/data/accessToken").asText();

        mockMvc.perform(get("/tenant/cardapio/status")
                        .header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isOk());

        changePassword(newGlobalToken, "SenhaErrada@123", "OutraSenha@123", "OutraSenha@123", 400);
        changePassword(newGlobalToken, newPassword, "OutraSenha@123", "Diferente@123", 400);
        changePassword(newGlobalToken, newPassword, "fraca1", "fraca1", 400);
        changePassword(newGlobalToken, newPassword, newPassword, newPassword, 400);
    }

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void expiredTemporaryPassword_blocksSelfChangeWithClearCode() throws Exception {
        ProvisionedOwner owner = provisionOwner("expired");
        User user = userRepository.findById(owner.userId()).orElseThrow();
        user.setTemporaryPasswordExpiresAt(LocalDateTime.now().minusMinutes(1));
        userRepository.saveAndFlush(user);

        String loginResp = login(owner.username(), owner.temporaryPassword(), status().isOk());
        String globalToken = objectMapper.readTree(loginResp).at("/data/accessToken").asText();

        mockMvc.perform(post("/auth/password/change")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "%s",
                                  "newPassword": "NovaSenha@123",
                                  "confirmPassword": "NovaSenha@123"
                                }
                                """.formatted(owner.temporaryPassword())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEMPORARY_PASSWORD_EXPIRED"));
    }

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void platformAdmin_canResetPassword_andTenantTokenCannotUsePlatformReset() throws Exception {
        ProvisionedOwner owner = provisionOwner("reset");

        String resetResp = mockMvc.perform(post("/platform/users/{userId}/password/reset", owner.userId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Reset IT",
                                  "temporaryPasswordExpiresInHours": 12
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(owner.userId()))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.passwordResetRequired").value(true))
                .andReturn().getResponse().getContentAsString();

        JsonNode resetJson = objectMapper.readTree(resetResp);
        String resetPassword = resetJson.at("/data/temporaryPassword").asText();
        assertThat(resetPassword).isNotBlank();
        User persisted = userRepository.findById(owner.userId()).orElseThrow();
        assertThat(persisted.getPassword()).doesNotContain(resetPassword);
        assertThat(passwordEncoder.matches(resetPassword, persisted.getPassword())).isTrue();
        assertThat(persisted.getTemporaryPasswordExpiresAt()).isNotNull();

        String loginResp = login(owner.username(), resetPassword, status().isOk());
        JsonNode loginJson = objectMapper.readTree(loginResp);
        assertThat(loginJson.at("/data/mustChangePassword").asBoolean()).isTrue();
        assertThat(loginJson.at("/data/passwordResetRequired").asBoolean()).isTrue();
        String globalToken = loginJson.at("/data/accessToken").asText();

        String accessSummary = mockMvc.perform(get("/platform/tenants/{tenantId}/access", owner.tenantId())
                        .with(user("platform-admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(accessSummary).doesNotContain(resetPassword);
        assertThat(objectMapper.readTree(accessSummary).findValues("temporaryPassword")).isEmpty();

        mockMvc.perform(post("/platform/users/{userId}/password/reset", owner.userId())
                        .with(user("platform-admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.temporaryPassword").isNotEmpty());

        mockMvc.perform(post("/platform/users/{userId}/password/reset", 999999999L)
                        .with(user("platform-admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/platform/users/{userId}/password/reset", owner.userId())
                        .with(anonymous())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        String selectResp = mockMvc.perform(post("/auth/tenant/select")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "tenantId": %d }
                                """.formatted(owner.tenantId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String tenantToken = objectMapper.readTree(selectResp).at("/data/accessToken").asText();

        mockMvc.perform(post("/platform/users/{userId}/password/reset", owner.userId())
                        .header("Authorization", "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void tenantAccessReset_validatesUserTenantScope() throws Exception {
        ProvisionedOwner ownerA = provisionOwner("scope-a");
        ProvisionedOwner ownerB = provisionOwner("scope-b");

        mockMvc.perform(post("/platform/tenants/{tenantId}/access/reset-password", ownerB.tenantId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d,
                                  "motivo": "Cross tenant"
                                }
                                """.formatted(ownerA.userId())))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/platform/tenants/{tenantId}/access/reset-password", ownerB.tenantId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d,
                                  "motivo": "Reset tenant IT",
                                  "temporaryPasswordExpiresInHours": 24
                                }
                                """.formatted(ownerB.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(ownerB.userId()))
                .andExpect(jsonPath("$.data.temporaryPassword").isNotEmpty())
                .andExpect(jsonPath("$.data.passwordResetRequired").value(true));
    }

    private ProvisionedOwner provisionOwner(String marker) throws Exception {
        String suffix = marker.replaceAll("[^a-z0-9-]", "") + "-" + Math.abs(System.nanoTime() % 1_000_000L);
        String digits = String.format("%06d", Math.abs(System.nanoTime() % 1_000_000L));
        String payload = """
                {
                  "createBusinessAccount": true,
                  "businessAccountNome": "Conta Password %s",
                  "businessAccountEmail": "conta-password-%s@test.com",
                  "businessAccountTelefone": "+244921%s",
                  "maxTenants": 1,
                  "tenantNome": "Tenant Password %s",
                  "tenantSlug": "tenant-password-%s",
                  "tenantTipo": "VENDEDOR_RUA",
                  "tenantEmail": "tenant-password-%s@test.com",
                  "tenantTelefone": "+244931%s",
                  "ownerNome": "Owner Password %s",
                  "ownerUsername": "owner.password.%s",
                  "ownerEmail": "owner-password-%s@test.com",
                  "ownerTelefone": "+244941%s",
                  "gerarSenhaTemporaria": true,
                  "ativarTenant": true
                }
                """.formatted(
                suffix, suffix, digits,
                suffix, suffix, suffix, digits,
                suffix, suffix, suffix, digits
        );

        String resp = mockMvc.perform(post("/platform/tenants/provision-with-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(resp);
        return new ProvisionedOwner(
                json.at("/data/tenantId").asLong(),
                json.at("/data/businessAccountId").asLong(),
                json.at("/data/ownerUserId").asLong(),
                json.at("/data/ownerUsername").asText(),
                json.at("/data/temporaryPassword").asText()
        );
    }

    private String login(String username,
                         String password,
                         org.springframework.test.web.servlet.ResultMatcher expectedStatus) throws Exception {
        return mockMvc.perform(post("/auth/jwt/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(expectedStatus)
                .andReturn().getResponse().getContentAsString();
    }

    private void changePassword(String token,
                                String currentPassword,
                                String newPassword,
                                String confirmPassword,
                                int expectedStatus) throws Exception {
        mockMvc.perform(post("/auth/password/change")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "%s",
                                  "newPassword": "%s",
                                  "confirmPassword": "%s"
                                }
                                """.formatted(currentPassword, newPassword, confirmPassword)))
                .andExpect(status().is(expectedStatus));
    }

    private record ProvisionedOwner(
            long tenantId,
            long businessAccountId,
            long userId,
            String username,
            String temporaryPassword
    ) {}
}
