package com.restaurante.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.BusinessAccount;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class BusinessAccountOwnershipOriginAuditIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BusinessAccountRepository businessAccountRepository;
    @Autowired BusinessAccountMemberRepository businessAccountMemberRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired UserRepository userRepository;

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void diagnostic_flagsDraftBusinessAccountLinkedToActiveTenantWithoutBusinessOwner() throws Exception {
        String suffix = suffix();
        BusinessAccount account = createBusinessAccount("graciete-audit-" + suffix, BusinessAccountEstado.RASCUNHO, null);
        User tenantOwner = createUser("tenant-owner-audit-" + suffix + "@test.com", Role.ROLE_GERENTE);
        Tenant tenant = createTenant("tenant-audit-" + suffix, account);
        createTenantOwner(tenant, tenantOwner);

        String response = mockMvc.perform(get("/platform/business-accounts/{id}/governance-diagnostic", account.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(data.path("requiresBackfill").asBoolean()).isTrue();
        assertThat(data.path("hasValidBusinessOwner").asBoolean()).isFalse();
        assertThat(data.path("blockingReasons").toString())
                .contains("BUSINESS_ACCOUNT_RESPONSAVEL_MISSING")
                .contains("BUSINESS_ACCOUNT_OWNER_MISSING")
                .contains("ACTIVE_TENANT_LINKED_TO_NON_ACTIVE_BUSINESS_ACCOUNT");
        assertThat(data.path("tenants")).hasSize(1);
        assertThat(data.at("/tenants/0/hasTenantOwner").asBoolean()).isTrue();
    }

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void activatingBusinessAccountWithoutRealOwner_isRejected() throws Exception {
        String suffix = suffix();
        BusinessAccount account = createBusinessAccount("activation-audit-" + suffix, BusinessAccountEstado.RASCUNHO, null);

        mockMvc.perform(patch("/platform/business-accounts/{id}/estado", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "estado": "ATIVA"
                                }
                                """))
                .andExpect(status().isBadRequest());

        BusinessAccount reloaded = businessAccountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getEstado()).isEqualTo(BusinessAccountEstado.RASCUNHO);
    }

    @Test
    @WithMockUser(username = "platform-admin", authorities = "ROLE_ADMIN")
    void businessTemplateProvisionWithDraftBusinessAccount_createsBusinessOwnerAndActivatesAccount() throws Exception {
        String suffix = suffix();
        BusinessAccount account = createBusinessAccount("template-audit-" + suffix, BusinessAccountEstado.RASCUNHO, null);
        String ownerEmail = "template-owner-audit-" + suffix + "@test.com";
        String slug = UniqueTestData.uniqueSlug("template-audit");
        String tenantCode = UniqueTestData.uniqueTenantCode("AUD");

        String payload = """
                {
                  "planoCodigo": "PILOTO",
                  "businessAccountId": %d,
                  "tenant": {
                    "nomeNegocio": "Template Audit %s",
                    "slug": "%s",
                    "tenantCode": "%s",
                    "tipo": "VENDEDOR_RUA",
                    "telefone": "%s",
                    "email": "%s"
                  },
                  "owner": {
                    "nome": "Owner Template Audit",
                    "telefone": "%s",
                    "email": "%s",
                    "senhaTemporaria": "Alterar@123"
                  },
                  "ponto": {
                    "entregaManual": false,
                    "allowPickup": true
                  }
                }
                """.formatted(
                account.getId(),
                suffix,
                slug,
                tenantCode,
                UniqueTestData.uniqueTelefone(),
                ownerEmail,
                UniqueTestData.uniqueTelefone(),
                ownerEmail
        );

        mockMvc.perform(post("/platform/templates/CONSUMA_PONTO_V1/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        User owner = userRepository.findByEmail(ownerEmail).orElseThrow();
        BusinessAccount reloaded = businessAccountRepository.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getEstado()).isEqualTo(BusinessAccountEstado.ATIVA);
        assertThat(reloaded.getResponsavel().getId()).isEqualTo(owner.getId());
        assertThat(businessAccountMemberRepository
                .existsByBusinessAccountIdAndUserIdAndRoleInAndEstado(
                        account.getId(),
                        owner.getId(),
                        java.util.List.of(BusinessAccountRole.OWNER),
                        BusinessAccountMemberEstado.ATIVO
                )).isTrue();

        String diagnosticResponse = mockMvc.perform(get("/platform/business-accounts/{id}/governance-diagnostic", account.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode diagnostic = objectMapper.readTree(diagnosticResponse).path("data");
        assertThat(diagnostic.path("requiresBackfill").asBoolean()).isFalse();
        assertThat(diagnostic.path("hasValidBusinessOwner").asBoolean()).isTrue();
        assertThat(diagnostic.path("blockingReasons")).isEmpty();
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
        tenant.setTenantCode(UniqueTestData.uniqueTenantCode("AUD"));
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(TenantEstado.ATIVO);
        tenant.setBusinessAccount(account);
        return tenantRepository.saveAndFlush(tenant);
    }

    private User createUser(String email, Role role) {
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setNomeCompleto("Owner " + email);
        user.setTelefone(UniqueTestData.uniqueTelefone());
        user.setPassword("encodedPassword");
        user.setAtivo(true);
        user.setRoles(Set.of(role));
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

    private String suffix() {
        return String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
    }
}
