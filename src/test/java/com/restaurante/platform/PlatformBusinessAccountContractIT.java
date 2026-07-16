package com.restaurante.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.billing.repository.BillingPlanRepository;
import com.restaurante.billing.service.TenantSubscriptionService;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.BillingPlan;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.BillingInterval;
import com.restaurante.model.enums.BillingPlanStatus;
import com.restaurante.model.enums.TenantSubscriptionStatus;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.UserRepository;
import com.restaurante.repository.BusinessAccountRepository;
import com.restaurante.repository.BusinessAccountMemberRepository;
import com.restaurante.repository.OnboardingRequestRepository;
import com.restaurante.model.enums.BusinessAccountRole;
import com.restaurante.model.enums.BusinessAccountMemberEstado;
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
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class PlatformBusinessAccountContractIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository userRepository;
    @Autowired BillingPlanRepository billingPlanRepository;
    @Autowired TenantSubscriptionService tenantSubscriptionService;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired BusinessAccountRepository businessAccounts;
    @Autowired BusinessAccountMemberRepository businessMembers;
    @Autowired OnboardingRequestRepository onboardingRequests;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void platformAdmin_canUseBusinessAccountContractEndpoints() throws Exception {
        String suffix = suffix();
        User platformAdmin = createUser("platform-contract-" + suffix + "@test.com", "ROLE_ADMIN");
        ProvisionarTenantResponse tenantOne = provisionTenant(platformAdmin, "contract-one-" + suffix, "CTA" + suffix, "owner-contract-1-" + suffix + "@test.com");
        ProvisionarTenantResponse tenantTwo = provisionTenant(platformAdmin, "contract-two-" + suffix, "CTB" + suffix, "owner-contract-2-" + suffix + "@test.com");
        User ownerOne = userRepository.findById(tenantOne.getOwnerUserId()).orElseThrow();
        User member = createUser("member-contract-" + suffix + "@test.com", "ROLE_GERENTE");

        withPlatformAdminContext(platformAdmin, () -> {
            BillingPlan plan = new BillingPlan();
            plan.setCode("BA-CONTRACT-" + suffix.toUpperCase());
            plan.setName("Business Account Contract Plan " + suffix);
            plan.setDescription("Plan used by PlatformBusinessAccountContractIT");
            plan.setStatus(BillingPlanStatus.ACTIVE);
            plan.setBillingInterval(BillingInterval.MONTHLY);
            plan.setCurrency("AOA");
            plan.setBasePrice(new BigDecimal("12345.00"));
            plan.setIncludedTransactions(1000L);
            plan.setIncludedDevices(20L);
            plan.setIncludedUnits(5L);
            plan.setOveragePricePerTransaction(BigDecimal.ZERO);
            plan.setOveragePricePerDevice(BigDecimal.ZERO);
            plan.setOveragePricePerUnit(BigDecimal.ZERO);
            plan.setTransactionFeePercentage(BigDecimal.ZERO);
            plan.setMinimumMonthlyFee(BigDecimal.ZERO);
            BillingPlan savedPlan = billingPlanRepository.saveAndFlush(plan);
            tenantSubscriptionService.createOrReplaceForTenant(tenantOne.getTenantId(), savedPlan.getId(), TenantSubscriptionStatus.ACTIVE, 10);
            tenantSubscriptionService.createOrReplaceForTenant(tenantTwo.getTenantId(), savedPlan.getId(), TenantSubscriptionStatus.ACTIVE, 10);
        });

        String adminToken = globalToken(platformAdmin, "ROLE_ADMIN");
        String createResp = mockMvc.perform(post("/platform/business-accounts")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "contract-create-" + suffix)
                        .header("X-Correlation-Id", "contract-create-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Conta Contract %s",
                                  "slug": "conta-contract-%s",
                                  "maxTenants": 2,
                                  "responsavelPrincipal": {
                                    "strategy": "ASSOCIATE_EXISTING",
                                    "userId": %d,
                                    "confirmExistingUser": true
                                  }
                                }
                                """.formatted(suffix, suffix, ownerOne.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode created = objectMapper.readTree(createResp).path("data");
        long businessAccountId = created.path("id").asLong();
        long accountVersion = created.path("version").asLong();
        mockMvc.perform(post("/platform/business-accounts/{id}/tenants/{tenantId}", businessAccountId, tenantOne.getTenantId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated());
        created = objectMapper.readTree(mockMvc.perform(get("/platform/business-accounts/{id}", businessAccountId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(created.path("maxTenants").asInt()).isEqualTo(2);
        assertThat(created.path("tenantCount").asLong()).isEqualTo(1L);
        assertThat(created.path("version").asLong()).isGreaterThan(accountVersion);
        accountVersion = created.path("version").asLong();

        String listResp = mockMvc.perform(get("/platform/business-accounts")
                        .param("hasTenants", "true")
                        .param("responsavelUserId", String.valueOf(ownerOne.getId()))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(listResp).contains("conta-contract-" + suffix);

        String detailResp = mockMvc.perform(get("/platform/business-accounts/{id}", businessAccountId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(detailResp).at("/data/maxTenants").asInt()).isEqualTo(2);

        String limitsResp = mockMvc.perform(get("/platform/business-accounts/{id}/limits", businessAccountId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode limitsBefore = objectMapper.readTree(limitsResp).path("data");
        assertThat(limitsBefore.path("basePlanoCodigo").asText()).contains("PILOTO");
        assertThat(limitsBefore.path("origin").asText()).isIn("PLAN", "DEFAULT");

        String patchedLimitsResp = mockMvc.perform(patch("/platform/business-accounts/{id}/limits", businessAccountId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "maxTenants": 5,
                                  "maxProdutos": 999,
                                  "maxUsuarios": 50,
                                  "maxQrCodes": 60,
                                  "observacao": "override comercial"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode limitsAfter = objectMapper.readTree(patchedLimitsResp).path("data");
        assertThat(limitsAfter.path("origin").asText()).isEqualTo("OVERRIDE");
        assertThat(limitsAfter.path("maxTenants").asInt()).isEqualTo(5);
        assertThat(limitsAfter.path("maxProdutos").asInt()).isEqualTo(999);

        String billingResp = mockMvc.perform(get("/platform/business-accounts/{id}/billing", businessAccountId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode billing = objectMapper.readTree(billingResp).path("data");
        assertThat(billing.path("billingPlanCodes").toString()).contains("BA-CONTRACT-" + suffix.toUpperCase());
        assertThat(billing.path("linkedTenantCount").asLong()).isEqualTo(1L);

        mockMvc.perform(post("/platform/business-accounts/{id}/tenants/{tenantId}", businessAccountId, tenantTwo.getTenantId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated());

        String tenantsResp = mockMvc.perform(get("/platform/business-accounts/{id}/tenants", businessAccountId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode tenants = objectMapper.readTree(tenantsResp).path("data");
        assertThat(tenants).hasSize(2);

        String tenantPlatformResp = mockMvc.perform(get("/platform/tenants/{tenantId}", tenantOne.getTenantId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode tenantData = objectMapper.readTree(tenantPlatformResp).path("data");
        assertThat(tenantData.path("businessAccountId").asLong()).isEqualTo(businessAccountId);
        assertThat(tenantData.path("businessAccountNome").asText()).contains("Conta Contract");
        assertThat(tenantData.path("billingPlanCode").asText()).isEqualTo("BA-CONTRACT-" + suffix.toUpperCase());
        assertThat(tenantData.path("modulos").isObject()).isTrue();
        assertThat(tenantData.path("sessaoConsumo").isObject()).isTrue();

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
        JsonNode afterLegacyMemberCreate = objectMapper.readTree(mockMvc.perform(
                        get("/platform/business-accounts/{id}", businessAccountId)
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(afterLegacyMemberCreate.path("version").asLong()).isGreaterThan(accountVersion);
        accountVersion = afterLegacyMemberCreate.path("version").asLong();

        mockMvc.perform(patch("/platform/business-accounts/{id}/members/{memberId}/role", businessAccountId, memberId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "BILLING_MANAGER"
                                }
                                """))
                .andExpect(status().isOk());
        JsonNode afterLegacyRoleChange = objectMapper.readTree(mockMvc.perform(
                        get("/platform/business-accounts/{id}", businessAccountId)
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(afterLegacyRoleChange.path("version").asLong()).isGreaterThan(accountVersion);

        String membersResp = mockMvc.perform(get("/platform/business-accounts/{id}/members", businessAccountId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode members = objectMapper.readTree(membersResp).path("data");
        long ownerMemberId = -1L;
        for (JsonNode item : members) {
            if (item.path("role").asText().equals("OWNER")) {
                ownerMemberId = item.path("id").asLong();
                break;
            }
        }
        assertThat(ownerMemberId).isPositive();

        mockMvc.perform(patch("/platform/business-accounts/{id}/members/{memberId}/estado", businessAccountId, ownerMemberId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "estado": "SUSPENSO"
                                }
                                """))
                .andExpect(status().isBadRequest());

        String plansResp = mockMvc.perform(get("/platform/plans")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(plansResp).contains("PILOTO");

        String onboardingCreateResp = mockMvc.perform(post("/platform/onboarding-requests")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nomeSolicitante": "Comercial %s",
                                  "telefone": "+244955000111",
                                  "email": "onboarding-%s@test.com",
                                  "nomeNegocio": "Negocio %s",
                                  "tipoNegocio": "RESTAURANTE",
                                  "planoCodigo": "PILOTO",
                                  "valor": 5000
                                }
                                """.formatted(suffix, suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long onboardingId = objectMapper.readTree(onboardingCreateResp).at("/data/id").asLong();

        String onboardingApproveResp = mockMvc.perform(patch("/platform/onboarding-requests/{id}/approve", onboardingId)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "onboarding-approve-" + suffix)
                        .header("X-Correlation-Id", "corr-onboarding-approve-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "criarBusinessAccountSeAusente": true,
                                  "responsavelUserId": %d,
                                  "statusPagamento": "PAGO",
                                  "observacao": "aprovado pelo comercial"
                                }
                                """.formatted(ownerOne.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode onboardingApproved = objectMapper.readTree(onboardingApproveResp).path("data");
        assertThat(onboardingApproved.path("status").asText()).isEqualTo("APROVADO");
        assertThat(onboardingApproved.path("businessAccountId").asLong()).isPositive();

        String onboardingReplay = mockMvc.perform(patch("/platform/onboarding-requests/{id}/approve", onboardingId)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "onboarding-approve-" + suffix)
                        .header("X-Correlation-Id", "corr-onboarding-replay-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "criarBusinessAccountSeAusente": true,
                                  "responsavelUserId": %d,
                                  "statusPagamento": "PAGO",
                                  "observacao": "aprovado pelo comercial"
                                }
                                """.formatted(ownerOne.getId())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(onboardingReplay).at("/data/businessAccountId").asLong())
                .isEqualTo(onboardingApproved.path("businessAccountId").asLong());

        String onboardingRejectResp = mockMvc.perform(post("/platform/onboarding-requests")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nomeSolicitante": "Comercial 2 %s",
                                  "telefone": "+244955000222",
                                  "email": "onboarding-reject-%s@test.com",
                                  "nomeNegocio": "Negocio Reject %s",
                                  "tipoNegocio": "BAR",
                                  "planoCodigo": "PILOTO"
                                }
                                """.formatted(suffix, suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long rejectedOnboardingId = objectMapper.readTree(onboardingRejectResp).at("/data/id").asLong();

        String rejectedResp = mockMvc.perform(patch("/platform/onboarding-requests/{id}/reject", rejectedOnboardingId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "motivo": "documentacao incompleta"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(rejectedResp).at("/data/status").asText()).isEqualTo("REJEITADO");

        String onboardingListResp = mockMvc.perform(get("/platform/onboarding-requests")
                        .param("status", "APROVADO")
                        .param("search", "Negocio " + suffix)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(onboardingListResp).contains("Negocio " + suffix);

        mockMvc.perform(delete("/platform/business-accounts/{id}/tenants/{tenantId}", businessAccountId, tenantTwo.getTenantId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void twoConcurrentOnboardingApprovalsCreateOneCanonicalAccountAndOwner() throws Exception {
        String suffix = suffix();
        User admin = createUser("platform-onboarding-race-" + suffix + "@test.com", "ROLE_ADMIN");
        User owner = createUser("owner-onboarding-race-" + suffix + "@test.com", "ROLE_GERENTE");
        String token = globalToken(admin, "ROLE_ADMIN");
        String businessName = "Onboarding Race " + suffix;
        String created = mockMvc.perform(post("/platform/onboarding-requests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomeSolicitante\":\"Race\",\"telefone\":\"+244955123456\","
                                + "\"email\":\"race-" + suffix + "@test.com\",\"nomeNegocio\":\"" + businessName
                                + "\",\"tipoNegocio\":\"RESTAURANTE\",\"planoCodigo\":\"PILOTO\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long onboardingId = objectMapper.readTree(created).at("/data/id").asLong();
        String approval = "{\"criarBusinessAccountSeAusente\":true,\"responsavelUserId\":" + owner.getId()
                + ",\"statusPagamento\":\"NAO_APLICAVEL\"}";
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> approve = () -> mockMvc.perform(patch("/platform/onboarding-requests/{id}/approve", onboardingId)
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "approve-race-" + suffix)
                            .header("X-Correlation-Id", "corr-approve-race-" + Thread.currentThread().getId())
                            .contentType(MediaType.APPLICATION_JSON).content(approval))
                    .andReturn().getResponse().getStatus();
            Future<Integer> one = executor.submit(approve);
            Future<Integer> two = executor.submit(approve);
            assertThat(one.get()).isEqualTo(200);
            assertThat(two.get()).isEqualTo(200);
        } finally {
            executor.shutdownNow();
        }
        var onboarding = onboardingRequests.findById(onboardingId).orElseThrow();
        assertThat(onboarding.getBusinessAccount()).isNotNull();
        long accountId = onboarding.getBusinessAccount().getId();
        assertThat(businessAccounts.findById(accountId)).isPresent();
        assertThat(businessMembers.countByBusinessAccountIdAndRoleAndEstado(accountId,
                BusinessAccountRole.OWNER, BusinessAccountMemberEstado.ATIVO)).isEqualTo(1);
        assertThat(businessMembers.countByBusinessAccountId(accountId)).isEqualTo(1);
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
