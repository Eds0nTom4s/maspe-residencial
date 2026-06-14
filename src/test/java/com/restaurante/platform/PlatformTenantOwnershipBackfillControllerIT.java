package com.restaurante.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.BusinessAccount;
import com.restaurante.model.entity.BusinessAccountMember;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.BusinessAccountMemberEstado;
import com.restaurante.model.enums.BusinessAccountRole;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.BusinessAccountMemberRepository;
import com.restaurante.repository.BusinessAccountRepository;
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

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
@org.springframework.transaction.annotation.Transactional
class PlatformTenantOwnershipBackfillControllerIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired BusinessAccountRepository businessAccountRepository;
    @Autowired BusinessAccountMemberRepository businessAccountMemberRepository;

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void diagnose_nonExistingTenant_returns404() throws Exception {
        mockMvc.perform(get("/platform/tenants/999999/ownership-diagnostic"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void diagnose_healthyTenant_returnsCorrectDiagnostic() throws Exception {
        String suffix = String.format("%06d", Math.abs(System.nanoTime() % 1_000_000L));

        BusinessAccount ba = new BusinessAccount();
        ba.setNome("Healthy BA " + suffix);
        ba.setSlug("healthy-ba-" + suffix);
        ba.setEstado(BusinessAccountEstado.ATIVA);
        ba.setMaxTenants(5);
        ba.setProvisionedAt(LocalDateTime.now());
        ba = businessAccountRepository.saveAndFlush(ba);

        Tenant tenant = new Tenant();
        tenant.setNome("Healthy Tenant " + suffix);
        tenant.setSlug("healthy-tenant-" + suffix);
        tenant.setTenantCode("HLT" + suffix);
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(TenantEstado.ATIVO);
        tenant.setBusinessAccount(ba);
        tenant = tenantRepository.saveAndFlush(tenant);

        User owner = new User();
        owner.setUsername("owner.healthy." + suffix);
        owner.setNomeCompleto("Owner Healthy " + suffix);
        owner.setEmail("healthy-" + suffix + "@test.com");
        owner.setTelefone("+244900" + suffix);
        owner.setPassword("encodedPassword");
        owner.setAtivo(true);
        owner.setRoles(Set.of(Role.ROLE_GERENTE));
        owner = userRepository.saveAndFlush(owner);

        ba.setResponsavel(owner);
        ba = businessAccountRepository.saveAndFlush(ba);

        TenantUser tu = new TenantUser();
        tu.setTenant(tenant);
        tu.setUser(owner);
        tu.setRole(TenantUserRole.TENANT_OWNER);
        tu.setEstado(TenantUserEstado.ATIVO);
        tenantUserRepository.saveAndFlush(tu);

        BusinessAccountMember bam = new BusinessAccountMember();
        bam.setBusinessAccount(ba);
        bam.setUser(owner);
        bam.setRole(BusinessAccountRole.OWNER);
        bam.setEstado(BusinessAccountMemberEstado.ATIVO);
        businessAccountMemberRepository.saveAndFlush(bam);

        String response = mockMvc.perform(get("/platform/tenants/{tenantId}/ownership-diagnostic", tenant.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.at("/success").asBoolean()).isTrue();
        assertThat(json.at("/data/requiresBackfill").asBoolean()).isFalse();
        assertThat(json.at("/data/blockingReasons").isEmpty()).isTrue();
        assertThat(json.at("/data/hasBusinessAccount").asBoolean()).isTrue();
        assertThat(json.at("/data/hasTenantOwner").asBoolean()).isTrue();
    }

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void diagnose_legacyIncompleteTenant_returnsTrueRequiresBackfill() throws Exception {
        String suffix = String.format("%06d", Math.abs(System.nanoTime() % 1_000_000L));

        Tenant tenant = new Tenant();
        tenant.setNome("Legacy Tenant " + suffix);
        tenant.setSlug("legacy-tenant-" + suffix);
        tenant.setTenantCode("LEG" + suffix);
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(TenantEstado.ATIVO);
        tenant = tenantRepository.saveAndFlush(tenant);

        String response = mockMvc.perform(get("/platform/tenants/{tenantId}/ownership-diagnostic", tenant.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.at("/success").asBoolean()).isTrue();
        assertThat(json.at("/data/requiresBackfill").asBoolean()).isTrue();
        assertThat(json.at("/data/blockingReasons").toString()).contains("BUSINESS_ACCOUNT_MISSING", "TENANT_OWNER_MISSING");
    }

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void executeBackfill_createsBusinessAccountAndUserSuccessfully() throws Exception {
        String suffix = String.format("%06d", Math.abs(System.nanoTime() % 1_000_000L));

        Tenant tenant = new Tenant();
        tenant.setNome("Legacy Tenant " + suffix);
        tenant.setSlug("legacy-tenant-" + suffix);
        tenant.setTenantCode("LEG" + suffix);
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(TenantEstado.ATIVO);
        tenant = tenantRepository.saveAndFlush(tenant);

        // Also mock a platform admin user as owner first to verify revocation
        User adminUser = new User();
        adminUser.setUsername("platform.admin.owner." + suffix);
        adminUser.setNomeCompleto("Platform Admin Owner " + suffix);
        adminUser.setEmail("admin-owner-" + suffix + "@test.com");
        adminUser.setTelefone("+244800" + suffix);
        adminUser.setPassword("pass");
        adminUser.setAtivo(true);
        adminUser.setRoles(Set.of(Role.ROLE_ADMIN));
        adminUser = userRepository.saveAndFlush(adminUser);

        TenantUser tuAdmin = new TenantUser();
        tuAdmin.setTenant(tenant);
        tuAdmin.setUser(adminUser);
        tuAdmin.setRole(TenantUserRole.TENANT_OWNER);
        tuAdmin.setEstado(TenantUserEstado.ATIVO);
        tenantUserRepository.saveAndFlush(tuAdmin);

        String payload = """
                {
                  "ownerName": "Legacy Owner %s",
                  "ownerUsername": "legacy.owner.%s",
                  "ownerEmail": "owner-legacy-%s@test.com",
                  "ownerPhone": "+244955%s",
                  "generateTemporaryPassword": true
                }
                """.formatted(suffix, suffix, suffix, suffix.substring(0, 6));

        String response = mockMvc.perform(post("/platform/tenants/{tenantId}/ownership-backfill", tenant.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.at("/success").asBoolean()).isTrue();
        assertThat(json.at("/data/status").asText()).isEqualTo("COMPLETED");
        assertThat(json.at("/data/temporaryPassword").asText()).isNotBlank();

        long ownerUserId = json.at("/data/ownerUserId").asLong();
        long businessAccountId = json.at("/data/businessAccountId").asLong();

        // Check if tenant has the correct business account linked
        Tenant updatedTenant = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(updatedTenant.getBusinessAccount()).isNotNull();
        assertThat(updatedTenant.getBusinessAccount().getId()).isEqualTo(businessAccountId);

        // Verify that the old Platform Admin owner TenantUser record was revoked/deleted
        boolean adminOwnerExists = tenantUserRepository.existsByTenantIdAndUserId(tenant.getId(), adminUser.getId());
        assertThat(adminOwnerExists).isFalse();

        // Verify the new owner details
        User owner = userRepository.findById(ownerUserId).orElseThrow();
        assertThat(owner.getRoles()).contains(Role.ROLE_GERENTE);
        assertThat(owner.getRoles()).doesNotContain(Role.ROLE_ADMIN);

        // Verify diagnostic is now healthy
        String diagResp = mockMvc.perform(get("/platform/tenants/{tenantId}/ownership-diagnostic", tenant.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode diagJson = objectMapper.readTree(diagResp);
        assertThat(diagJson.at("/data/requiresBackfill").asBoolean()).isFalse();
    }

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void executeBackfill_withPlatformAdminAsOwner_fails400() throws Exception {
        String suffix = String.format("%06d", Math.abs(System.nanoTime() % 1_000_000L));

        Tenant tenant = new Tenant();
        tenant.setNome("Legacy Tenant " + suffix);
        tenant.setSlug("legacy-tenant-" + suffix);
        tenant.setTenantCode("LEG" + suffix);
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(TenantEstado.ATIVO);
        tenant = tenantRepository.saveAndFlush(tenant);

        User platformAdmin = new User();
        platformAdmin.setUsername("admin.only." + suffix);
        platformAdmin.setNomeCompleto("Admin Only " + suffix);
        platformAdmin.setEmail("admin-only-" + suffix + "@test.com");
        platformAdmin.setTelefone("+244700" + suffix);
        platformAdmin.setPassword("pass");
        platformAdmin.setAtivo(true);
        platformAdmin.setRoles(Set.of(Role.ROLE_ADMIN));
        platformAdmin = userRepository.saveAndFlush(platformAdmin);

        String payload = """
                {
                  "ownerUserId": %d
                }
                """.formatted(platformAdmin.getId());

        mockMvc.perform(post("/platform/tenants/{tenantId}/ownership-backfill", tenant.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }
}
