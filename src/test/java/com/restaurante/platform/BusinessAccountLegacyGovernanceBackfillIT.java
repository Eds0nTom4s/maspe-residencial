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
import com.restaurante.testsupport.UniqueTestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
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
class BusinessAccountLegacyGovernanceBackfillIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BusinessAccountRepository businessAccountRepository;
    @Autowired BusinessAccountMemberRepository businessAccountMemberRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired UserRepository userRepository;

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void legacyGovernanceBackfill_promotesDraftAccountAndCreatesBusinessOwnerFromTenantOwner() throws Exception {
        String suffix = suffix();
        BusinessAccount account = createBusinessAccount("legacy-governance-" + suffix, BusinessAccountEstado.RASCUNHO, null);
        Tenant tenant = createTenant("legacy-governance-tenant-" + suffix, account);
        User owner = createUser("legacy-governance-owner-" + suffix + "@test.com", Set.of());
        createTenantOwner(tenant, owner);

        User admin = createUser("legacy-governance-admin-" + suffix + "@test.com", Set.of(Role.ROLE_ADMIN));
        createTenantOwner(tenant, admin);
        createBusinessAccountOwner(account, admin);

        String response = mockMvc.perform(post("/platform/business-accounts/{id}/legacy-governance-backfill", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "corrigir conta legada de sandbox"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(data.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(data.path("promotedToAtiva").asBoolean()).isTrue();
        assertThat(data.path("responsavelUpdated").asBoolean()).isTrue();
        assertThat(data.path("requiresBackfillBefore").asBoolean()).isTrue();
        assertThat(data.path("requiresBackfillAfter").asBoolean()).isFalse();
        assertThat(data.path("ownerUserIds").toString()).contains(String.valueOf(owner.getId()));

        BusinessAccount reloaded = businessAccountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getEstado()).isEqualTo(BusinessAccountEstado.ATIVA);
        assertThat(reloaded.getResponsavel().getId()).isEqualTo(owner.getId());
        assertThat(reloaded.getObservacao()).contains("corrigir conta legada de sandbox");

        BusinessAccountMember member = businessAccountMemberRepository
                .findByBusinessAccountIdAndUserId(account.getId(), owner.getId())
                .orElseThrow();
        assertThat(member.getRole()).isEqualTo(BusinessAccountRole.OWNER);
        assertThat(member.getEstado()).isEqualTo(BusinessAccountMemberEstado.ATIVO);
        assertThat(userRepository.findById(owner.getId()).orElseThrow().getRoles()).contains(Role.ROLE_GERENTE);
        assertThat(businessAccountMemberRepository.findByBusinessAccountIdAndUserId(account.getId(), admin.getId())).isEmpty();
        assertThat(tenantUserRepository.existsByTenantIdAndUserIdAndRoleAndEstado(
                tenant.getId(),
                admin.getId(),
                TenantUserRole.TENANT_OWNER,
                TenantUserEstado.ATIVO
        )).isFalse();

        String diagnosticResponse = mockMvc.perform(get("/platform/business-accounts/{id}/governance-diagnostic", account.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode diagnostic = objectMapper.readTree(diagnosticResponse).path("data");
        assertThat(diagnostic.path("requiresBackfill").asBoolean()).isFalse();
        assertThat(diagnostic.path("blockingReasons")).isEmpty();
    }

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void legacyGovernanceBackfill_withoutRealTenantOwner_returns400() throws Exception {
        String suffix = suffix();
        BusinessAccount account = createBusinessAccount("legacy-governance-blocked-" + suffix, BusinessAccountEstado.RASCUNHO, null);
        createTenant("legacy-governance-blocked-tenant-" + suffix, account);

        mockMvc.perform(post("/platform/business-accounts/{id}/legacy-governance-backfill", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private BusinessAccount createBusinessAccount(String slug, BusinessAccountEstado estado, User responsavel) {
        BusinessAccount account = new BusinessAccount();
        account.setNome("BA " + slug);
        account.setSlug(slug);
        account.setEstado(estado);
        account.setResponsavel(responsavel);
        account.setMaxTenants(5);
        account.setProvisionedAt(LocalDateTime.now());
        return businessAccountRepository.saveAndFlush(account);
    }

    private Tenant createTenant(String slug, BusinessAccount account) {
        Tenant tenant = new Tenant();
        tenant.setNome("Tenant " + slug);
        tenant.setSlug(slug);
        tenant.setTenantCode(UniqueTestData.uniqueTenantCode("LGB"));
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(TenantEstado.ATIVO);
        tenant.setBusinessAccount(account);
        return tenantRepository.saveAndFlush(tenant);
    }

    private User createUser(String email, Set<Role> roles) {
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setNomeCompleto("User " + email);
        user.setTelefone(UniqueTestData.uniqueTelefone());
        user.setPassword("encodedPassword");
        user.setAtivo(true);
        user.setRoles(roles);
        return userRepository.saveAndFlush(user);
    }

    private void createTenantOwner(Tenant tenant, User owner) {
        TenantUser tenantUser = new TenantUser();
        tenantUser.setTenant(tenant);
        tenantUser.setUser(owner);
        tenantUser.setRole(TenantUserRole.TENANT_OWNER);
        tenantUser.setEstado(TenantUserEstado.ATIVO);
        tenantUserRepository.saveAndFlush(tenantUser);
    }

    private void createBusinessAccountOwner(BusinessAccount account, User owner) {
        BusinessAccountMember member = new BusinessAccountMember();
        member.setBusinessAccount(account);
        member.setUser(owner);
        member.setRole(BusinessAccountRole.OWNER);
        member.setEstado(BusinessAccountMemberEstado.ATIVO);
        businessAccountMemberRepository.saveAndFlush(member);
    }

    private String suffix() {
        return String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
    }
}
