package com.restaurante.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.billing.repository.BillingPlanRepository;
import com.restaurante.billing.service.TenantSubscriptionService;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.SelectTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.BillingPlan;
import com.restaurante.model.entity.TenantSubscription;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.BillingInterval;
import com.restaurante.model.enums.BillingPlanStatus;
import com.restaurante.model.enums.TenantSubscriptionStatus;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.JwtTokenProvider;
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

import java.math.BigDecimal;
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
class BusinessAccountFoundationIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository userRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired BillingPlanRepository billingPlanRepository;
    @Autowired TenantSubscriptionService tenantSubscriptionService;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void platformAdmin_canCreateAssociateManageAndKeepAuthBillingStable() throws Exception {
        String suffix = suffix();
        User platformAdmin = createUser("platform-ba-" + suffix + "@test.com", "ROLE_ADMIN");
        ProvisionarTenantResponse provisioned = provisionTenant(platformAdmin, "ba-tenant-" + suffix, "BAT" + suffix, "owner-ba-" + suffix + "@test.com");
        User owner = userRepository.findById(provisioned.getOwnerUserId()).orElseThrow();
        User member = createUser("member-ba-" + suffix + "@test.com", "ROLE_GERENTE");

        withPlatformAdminContext(platformAdmin, () -> {
            BillingPlan plan = new BillingPlan();
            plan.setCode("BA-PLAN-" + suffix.toUpperCase());
            plan.setName("Business Account Test Plan");
            plan.setDescription("Plan used by BusinessAccountFoundationIT");
            plan.setStatus(BillingPlanStatus.ACTIVE);
            plan.setBillingInterval(BillingInterval.MONTHLY);
            plan.setCurrency("AOA");
            plan.setBasePrice(BigDecimal.ZERO);
            plan.setIncludedTransactions(100L);
            plan.setIncludedDevices(10L);
            plan.setIncludedUnits(3L);
            plan.setOveragePricePerTransaction(BigDecimal.ZERO);
            plan.setOveragePricePerDevice(BigDecimal.ZERO);
            plan.setOveragePricePerUnit(BigDecimal.ZERO);
            plan.setTransactionFeePercentage(BigDecimal.ZERO);
            plan.setMinimumMonthlyFee(BigDecimal.ZERO);
            BillingPlan savedPlan = billingPlanRepository.saveAndFlush(plan);
            tenantSubscriptionService.createOrReplaceForTenant(
                    provisioned.getTenantId(),
                    savedPlan.getId(),
                    TenantSubscriptionStatus.ACTIVE,
                    5
            );
        });

        String adminToken = globalToken(platformAdmin, "ROLE_ADMIN");
        String createPayload = """
                {
                  "nome": "Conta Empresarial %s",
                  "slug": "conta-empresarial-%s",
                  "maxTenants": 2,
                  "responsavelPrincipal": {
                    "strategy": "ASSOCIATE_EXISTING",
                    "userId": %d,
                    "confirmExistingUser": true
                  }
                }
                """.formatted(suffix, suffix, owner.getId());

        String createResp = mockMvc.perform(post("/platform/business-accounts")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "foundation-create-" + suffix)
                        .header("X-Correlation-Id", "foundation-create-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode created = objectMapper.readTree(createResp).at("/data");
        long businessAccountId = created.at("/id").asLong();
        mockMvc.perform(post("/platform/business-accounts/{id}/tenants/{tenantId}", businessAccountId, provisioned.getTenantId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated());
        created = objectMapper.readTree(mockMvc.perform(get("/platform/business-accounts/{id}", businessAccountId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).at("/data");
        assertThat(created.at("/tenantCount").asLong()).isEqualTo(1L);
        assertThat(tenantRepository.findById(provisioned.getTenantId()).orElseThrow().getBusinessAccount().getId())
                .isEqualTo(businessAccountId);

        String listResp = mockMvc.perform(get("/platform/business-accounts")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(listResp).contains("conta-empresarial-" + suffix);

        String tenantsResp = mockMvc.perform(get("/platform/business-accounts/{id}/tenants", businessAccountId)
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(tenantsResp).at("/data/0/tenantId").asLong()).isEqualTo(provisioned.getTenantId());

        String memberResp = mockMvc.perform(post("/platform/business-accounts/{id}/members", businessAccountId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d,
                                  "role": "ADMIN"
                                }
                                """.formatted(member.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long memberId = objectMapper.readTree(memberResp).at("/data/id").asLong();
        String membersResp = mockMvc.perform(get("/platform/business-accounts/{id}/members", businessAccountId)
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(membersResp).contains("OWNER");
        assertThat(membersResp).contains("ADMIN");

        mockMvc.perform(patch("/platform/business-accounts/{id}/members/{memberId}/estado", businessAccountId, memberId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "estado": "SUSPENSO"
                                }
                                """))
                .andExpect(status().isOk());

        String stateResp = mockMvc.perform(patch("/platform/business-accounts/{id}/estado", businessAccountId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "estado": "SUSPENSA"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(stateResp).at("/data/estado").asText()).isEqualTo("SUSPENSA");

        TenantSubscription subscription = withPlatformAdminContext(platformAdmin,
                () -> tenantSubscriptionService.getCurrentForTenant(provisioned.getTenantId()));
        assertThat(subscription).isNotNull();
        assertThat(subscription.getTenant().getId()).isEqualTo(provisioned.getTenantId());

        String ownerToken = globalToken(owner, "ROLE_GERENTE");
        mockMvc.perform(post("/auth/tenant/select")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SelectTenantRequest(provisioned.getTenantId()))))
                .andExpect(status().isForbidden());
    }

    private ProvisionarTenantResponse provisionTenant(User platformAdmin, String slug, String tenantCode, String ownerEmail) {
        return withPlatformAdminContext(platformAdmin, () -> {
            String code = tenantCode.replaceAll("[^A-Z0-9]", "");
            if (code.length() > 20) {
                code = code.substring(0, 20);
            }
            String phone = "+24493" + String.format("%07d", Math.abs(slug.hashCode() % 10_000_000L));
            return provisioningService.provisionar(
                    ProvisionarTenantRequest.builder()
                            .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                    .nome("Tenant " + slug)
                                    .slug(slug)
                                    .tenantCode(code)
                                    .tipo(TenantTipo.RESTAURANTE)
                                    .build())
                            .planoCodigo("PILOTO")
                            .templateCodigo("RESTAURANTE_SIMPLES")
                            .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                    .nome("Inst " + slug)
                                    .sigla(code.substring(0, Math.min(4, code.length())))
                                    .build())
                            .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                    .email(ownerEmail)
                                    .telefone(phone)
                                    .criarUsuario(true)
                                    .build())
                            .opcoes(ProvisionarTenantRequest.OpcoesProvisionamento.builder()
                                    .criarQrPrincipal(true)
                                    .criarMesas(false)
                                    .criarQrPorMesa(false)
                                    .build())
                            .build()
            );
        });
    }

    private User createUser(String email, String role) {
        User user = new User();
        user.setUsername(email);
        user.setPassword("x");
        user.setEmail(email);
        user.setTelefone("+24494" + String.format("%07d", Math.abs(email.hashCode() % 10_000_000L)));
        user.setRoles(Set.of(com.restaurante.model.enums.Role.valueOf(role)));
        user.setAtivo(true);
        return userRepository.saveAndFlush(user);
    }

    private String globalToken(User user, String roles) {
        return jwtTokenProvider.generateToken(user.getUsername(), roles, null, user.getId(), "GLOBAL");
    }

    private String suffix() {
        return String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
    }

    private void withPlatformAdminContext(User platformAdmin, Runnable runnable) {
        TenantContextHolder.set(new TenantContext(
                null, null, platformAdmin.getId(), Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));
        try {
            runnable.run();
        } finally {
            TenantContextHolder.clear();
        }
    }

    private <T> T withPlatformAdminContext(User platformAdmin, SupplierWithException<T> supplier) {
        TenantContextHolder.set(new TenantContext(
                null, null, platformAdmin.getId(), Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));
        try {
            return supplier.get();
        } finally {
            TenantContextHolder.clear();
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get();
    }
}
