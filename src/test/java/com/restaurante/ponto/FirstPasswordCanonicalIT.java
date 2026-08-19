package com.restaurante.ponto;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.User;
import com.restaurante.repository.UserRepository;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import com.restaurante.testsupport.UniqueTestData;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet")
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("it-postgres")
class FirstPasswordCanonicalIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void temporaryGlobalCredentialCanOnlyChangePasswordBeforeTenantDiscovery() throws Exception {
        String username = "first-password-" + UniqueTestData.uniqueSlug("owner");
        User owner = new User();
        owner.setUsername(username);
        owner.setPassword(passwordEncoder.encode("Temporary123"));
        owner.setEmail(UniqueTestData.uniqueEmail("first-password"));
        owner.setNomeCompleto("Owner First Password");
        owner.setTelefone(UniqueTestData.uniqueTelefone());
        owner.setRoles(Set.of());
        owner.setAtivo(true);
        owner.setMustChangePassword(true);
        owner.setPasswordResetRequired(true);
        owner.setTemporaryPasswordExpiresAt(LocalDateTime.now().plusHours(1));
        users.saveAndFlush(owner);

        String loginBody = mockMvc.perform(post("/auth/jwt/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "username", username, "password", "Temporary123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString();
        String temporaryToken = json.readTree(loginBody).at("/data/accessToken").asText();

        mockMvc.perform(get("/auth/tenants").header("Authorization", "Bearer " + temporaryToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        String changedBody = mockMvc.perform(post("/auth/password/change")
                        .header("Authorization", "Bearer " + temporaryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Temporary123","newPassword":"Permanent12345",
                                 "confirmPassword":"Permanent12345"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andExpect(jsonPath("$.data.passwordResetRequired").value(false))
                .andReturn().getResponse().getContentAsString();
        String globalToken = json.readTree(changedBody).at("/data/accessToken").asText();

        mockMvc.perform(get("/auth/tenants").header("Authorization", "Bearer " + globalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
