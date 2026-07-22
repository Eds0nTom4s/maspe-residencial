package com.restaurante.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.SelectTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.BusinessAccount;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.BusinessAccountRepository;
import com.restaurante.repository.SubscricaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UserRepository;
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
class OperationalTenantEligibilityPostgresIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioning;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired BusinessAccountRepository accounts;
    @Autowired SubscricaoRepository subscriptions;
    @Autowired JwtTokenProvider tokens;

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void canonicalTenant_requiresActiveBusinessAccountForListAndSelect() throws Exception {
        ProvisionarTenantResponse provisioned = provision("account-inactive");
        User owner = users.findById(provisioned.getOwnerUserId()).orElseThrow();
        attachAccount(provisioned.getTenantId(), owner, BusinessAccountEstado.RASCUNHO);

        assertNotListedAndSelectDenied(owner, provisioned.getTenantId(), "BUSINESS_ACCOUNT_NOT_OPERATIONAL");
    }

    @Test
    void canonicalTenant_requiresActiveSubscriptionForListAndSelect() throws Exception {
        ProvisionarTenantResponse provisioned = provision("subscription-inactive");
        User owner = users.findById(provisioned.getOwnerUserId()).orElseThrow();
        attachAccount(provisioned.getTenantId(), owner, BusinessAccountEstado.ATIVA);
        var subscription = subscriptions.findByTenantIdAndEstado(
                provisioned.getTenantId(), SubscricaoEstado.ATIVA).orElseThrow();
        subscription.setEstado(SubscricaoEstado.SUSPENSA);
        subscriptions.saveAndFlush(subscription);

        assertNotListedAndSelectDenied(owner, provisioned.getTenantId(), "SUBSCRIPTION_NOT_OPERATIONAL");
    }

    @Test
    void legacyTenant_withoutBusinessAccountPreservesListAndSelect() throws Exception {
        ProvisionarTenantResponse provisioned = provision("legacy-compatible");
        User owner = users.findById(provisioned.getOwnerUserId()).orElseThrow();
        String token = global(owner);

        JsonNode list = response(get("/auth/tenants")
                .header("Authorization", "Bearer " + token), 200).path("data");
        assertThat(list.findValuesAsText("tenantId")).contains(Long.toString(provisioned.getTenantId()));

        JsonNode select = response(post("/auth/tenant/select")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SelectTenantRequest(provisioned.getTenantId()))), 200);
        assertThat(select.at("/data/tenantId").asLong()).isEqualTo(provisioned.getTenantId());
    }

    @Test
    void platformAdminBypassRemainsExplicitForInactiveAccount() throws Exception {
        ProvisionarTenantResponse provisioned = provision("platform-bypass");
        User owner = users.findById(provisioned.getOwnerUserId()).orElseThrow();
        attachAccount(provisioned.getTenantId(), owner, BusinessAccountEstado.RASCUNHO);
        User platform = createUser("platform-eligibility", Role.ROLE_ADMIN);
        String token = global(platform);

        JsonNode list = response(get("/auth/tenants")
                .header("Authorization", "Bearer " + token), 200).path("data");
        assertThat(list.findValuesAsText("tenantId")).contains(Long.toString(provisioned.getTenantId()));

        JsonNode select = response(post("/auth/tenant/select")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SelectTenantRequest(provisioned.getTenantId()))), 200);
        assertThat(select.at("/data/tenantId").asLong()).isEqualTo(provisioned.getTenantId());
    }

    private void assertNotListedAndSelectDenied(User user, Long tenantId, String code) throws Exception {
        String token = global(user);
        JsonNode list = response(get("/auth/tenants")
                .header("Authorization", "Bearer " + token), 200).path("data");
        assertThat(list.findValuesAsText("tenantId")).doesNotContain(Long.toString(tenantId));

        JsonNode denied = response(post("/auth/tenant/select")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SelectTenantRequest(tenantId))), 403);
        assertThat(denied.path("code").asText()).isEqualTo(code);
    }

    private JsonNode response(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                              int expectedStatus) throws Exception {
        return objectMapper.readTree(mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString());
    }

    private BusinessAccount attachAccount(Long tenantId, User owner, BusinessAccountEstado estado) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        BusinessAccount account = new BusinessAccount();
        account.setNome("Eligibility " + suffix);
        account.setSlug("eligibility-" + suffix);
        account.setMaxTenants(5);
        account.setEstado(estado);
        account.setResponsavel(owner);
        account = accounts.saveAndFlush(account);
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        tenant.setBusinessAccount(account);
        tenants.saveAndFlush(tenant);
        return account;
    }

    private ProvisionarTenantResponse provision(String prefix) {
        String suffix = Long.toString(Math.abs(System.nanoTime() % 1_000_000_000L));
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false));
        ProvisionarTenantResponse response = provisioning.provisionar(ProvisionarTenantRequest.builder()
                .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                        .nome("Tenant " + prefix + " " + suffix)
                        .slug(prefix + "-" + suffix)
                        .tenantCode(("E" + suffix).substring(0, Math.min(10, suffix.length() + 1)))
                        .tipo(TenantTipo.VENDEDOR_RUA)
                        .build())
                .planoCodigo("PILOTO")
                .templateCodigo("VENDEDOR_RUA")
                .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                        .nome("Inst " + suffix)
                        .sigla(("I" + suffix).substring(0, Math.min(8, suffix.length() + 1)))
                        .build())
                .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                        .email(prefix + "-" + suffix + "@test.local")
                        .telefone("+2449" + (suffix + "000000000").substring(0, 8))
                        .criarUsuario(true)
                        .build())
                .build());
        TenantContextHolder.clear();
        return response;
    }

    private User createUser(String prefix, Role role) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        User user = new User();
        user.setUsername(prefix + "-" + suffix + "@test.local");
        user.setEmail(user.getUsername());
        user.setPassword("x");
        user.setTelefone("+2449" + suffix.substring(0, Math.min(8, suffix.length())));
        user.setRoles(Set.of(role));
        user.setAtivo(true);
        return users.saveAndFlush(user);
    }

    private String global(User user) {
        String roles = user.getRoles().stream().map(Enum::name).sorted()
                .collect(java.util.stream.Collectors.joining(","));
        return tokens.generateToken(user.getUsername(), roles, null, user.getId(), "GLOBAL");
    }
}
