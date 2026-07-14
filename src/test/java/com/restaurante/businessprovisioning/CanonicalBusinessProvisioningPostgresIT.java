package com.restaurante.businessprovisioning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.businesstemplate.BusinessTemplateService;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.BusinessAccount;
import com.restaurante.model.entity.BusinessProvisioningPreview;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.BusinessAccountMemberEstado;
import com.restaurante.model.enums.BusinessAccountRole;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.*;
import com.restaurante.security.JwtTokenProvider;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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
class CanonicalBusinessProvisioningPostgresIT extends PostgresTestcontainersConfig {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired UserRepository users;
    @Autowired TenantRepository tenants;
    @Autowired BusinessAccountRepository accounts;
    @Autowired BusinessAccountMemberRepository members;
    @Autowired BusinessProvisioningPreviewRepository previews;
    @Autowired BusinessProvisioningOperationRepository operations;
    @Autowired SubscricaoRepository subscricoes;
    @Autowired PlanoRepository planos;
    @Autowired InstituicaoRepository instituicoes;
    @Autowired UnidadeAtendimentoRepository unidades;
    @Autowired TenantUserRepository tenantUsers;
    @SpyBean BusinessTemplateService templateService;

    @Test
    void canonicalFlow_isAtomicRecoverableAndActivatesOnlyAfterReadiness() throws Exception {
        String suffix = suffix();
        User admin = user("platform-" + suffix, Role.ROLE_ADMIN);
        User owner = user("owner-" + suffix, Role.ROLE_GERENTE);
        String token = token(admin);
        JsonNode account = createAccount(token, owner, suffix, 1);
        long accountId = account.path("id").asLong();
        long accountVersion = account.path("version").asLong();
        long tenantsBefore = tenants.count();

        String previewPayload = previewPayload(accountVersion, suffix, "PILOTO");
        String previewBody = mockMvc.perform(post("/platform/business-accounts/{id}/businesses/preview", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "preview-" + suffix)
                        .header("X-Correlation-Id", "corr-preview-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(previewPayload))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode preview = objectMapper.readTree(previewBody).path("data");
        assertThat(preview.path("allowedToProvision").asBoolean()).isTrue();
        assertThat(preview.path("requestFingerprint").asText()).hasSize(64);
        assertThat(tenants.count()).isEqualTo(tenantsBefore);

        String previewReplay = mockMvc.perform(post("/platform/business-accounts/{id}/businesses/preview", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "preview-" + suffix)
                        .header("X-Correlation-Id", "corr-preview-replay-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(previewPayload))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(previewReplay).at("/data/previewId").asText())
                .isEqualTo(preview.path("previewId").asText());
        assertThat(objectMapper.readTree(previewReplay).at("/data/replay").asBoolean()).isTrue();

        String provisionPayload = provisionPayload(preview, accountVersion, true);
        String provisionBody = mockMvc.perform(post("/platform/business-accounts/{id}/businesses/provision", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "provision-" + suffix)
                        .header("X-Correlation-Id", "corr-provision-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(provisionPayload))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode operation = objectMapper.readTree(provisionBody).path("data");
        long tenantId = operation.path("tenantId").asLong();
        String operationId = operation.path("operationId").asText();
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        assertThat(tenant.getEstado()).isEqualTo(TenantEstado.RASCUNHO);
        assertThat(tenant.getBusinessAccount().getId()).isEqualTo(accountId);
        Long planoId = subscricoes.findByTenantIdAndEstado(tenantId, SubscricaoEstado.ATIVA).orElseThrow()
                .getPlano().getId();
        assertThat(planos.findById(planoId).orElseThrow().getCodigo()).isEqualTo("PILOTO");
        assertThat(instituicoes.countByTenantId(tenantId)).isPositive();
        assertThat(unidades.countByTenantId(tenantId)).isPositive();
        assertThat(tenantUsers.countByTenantIdAndRoleAndEstado(tenantId, TenantUserRole.TENANT_OWNER,
                TenantUserEstado.ATIVO)).isEqualTo(1);

        String replayBody = mockMvc.perform(post("/platform/business-accounts/{id}/businesses/provision", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "provision-" + suffix)
                        .header("X-Correlation-Id", "corr-provision-replay-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(provisionPayload))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(replayBody).at("/data/operationId").asText()).isEqualTo(operationId);
        assertThat(tenants.countByBusinessAccountId(accountId)).isEqualTo(1);

        mockMvc.perform(post("/platform/business-accounts/{id}/businesses/provision", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "provision-" + suffix)
                        .header("X-Correlation-Id", "corr-provision-conflict-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(provisionPayload(preview, accountVersion, false)))
                .andExpect(status().isConflict());

        String lookup = mockMvc.perform(get("/platform/provisioning-operations/{id}", operationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(lookup).at("/data/status").asText()).isEqualTo("SUCCEEDED");
        String lookupByKey = mockMvc.perform(get("/platform/provisioning-operations")
                        .param("businessAccountId", Long.toString(accountId))
                        .param("idempotencyKey", "provision-" + suffix)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(lookupByKey).at("/data/operationId").asText()).isEqualTo(operationId);

        String readinessBefore = mockMvc.perform(get("/platform/business-accounts/{accountId}/businesses/{tenantId}/readiness", accountId, tenantId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(readinessBefore).at("/data/ready").asBoolean()).isFalse();
        assertThat(readinessBefore).contains("ACCOUNT_ACTIVE");

        String activatedAccount = mockMvc.perform(post("/platform/business-accounts/{id}/activate", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "activate-account-" + suffix)
                        .header("X-Correlation-Id", "corr-activate-account-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountVersion\":" + accountVersion + ",\"reason\":\"readiness validado\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long activeAccountVersion = objectMapper.readTree(activatedAccount).at("/data/version").asLong();
        tenant = tenants.findById(tenantId).orElseThrow();
        String activation = "{\"accountVersion\":" + activeAccountVersion
                + ",\"tenantVersion\":" + tenant.getVersion() + ",\"reason\":\"activar após readiness\"}";
        String activatedBusiness = mockMvc.perform(post("/platform/business-accounts/{accountId}/businesses/{tenantId}/activate", accountId, tenantId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "activate-business-" + suffix)
                        .header("X-Correlation-Id", "corr-activate-business-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(activation))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(activatedBusiness).at("/data/ready").asBoolean()).isTrue();
        assertThat(tenants.findById(tenantId).orElseThrow().getEstado()).isEqualTo(TenantEstado.ATIVO);
    }

    @Test
    void ownerReplacement_isAtomicAndManagerCannotBecomeOwner() throws Exception {
        String suffix = suffix();
        User admin = user("platform-owner-" + suffix, Role.ROLE_ADMIN);
        User owner = user("owner-old-" + suffix, Role.ROLE_GERENTE);
        User replacement = user("owner-new-" + suffix, Role.ROLE_GERENTE);
        String token = token(admin);
        JsonNode account = createAccount(token, owner, "owner-" + suffix, 2);
        long id = account.path("id").asLong();
        long version = account.path("version").asLong();
        String payload = "{\"accountVersion\":" + version + ",\"novoResponsavel\":{"
                + "\"strategy\":\"ASSOCIATE_EXISTING\",\"userId\":" + replacement.getId()
                + ",\"confirmExistingUser\":true},\"reason\":\"substituição governada\"}";
        String result = mockMvc.perform(post("/platform/business-accounts/{id}/owner/replace", id)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "replace-owner-" + suffix)
                        .header("X-Correlation-Id", "corr-replace-owner-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode replaced = objectMapper.readTree(result).path("data");
        assertThat(replaced.path("responsavelUserId").asLong()).isEqualTo(replacement.getId());
        assertThat(members.countByBusinessAccountIdAndRoleAndEstado(id, BusinessAccountRole.OWNER,
                BusinessAccountMemberEstado.ATIVO)).isEqualTo(1);

        mockMvc.perform(post("/platform/business-accounts/{id}/owner/replace", id)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "replace-owner-" + suffix)
                        .header("X-Correlation-Id", "corr-replace-owner-replay-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());

        String managerOwner = "{\"accountVersion\":" + replaced.path("version").asLong()
                + ",\"userId\":" + owner.getId() + ",\"role\":\"OWNER\",\"reason\":\"tentativa inválida\"}";
        mockMvc.perform(post("/platform/business-accounts/{id}/managers", id)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "manager-owner-" + suffix)
                        .header("X-Correlation-Id", "corr-manager-owner-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(managerOwner))
                .andExpect(status().isBadRequest());
        assertThat(accounts.findById(id).orElseThrow().getResponsavel().getId()).isEqualTo(replacement.getId());
    }

    @Test
    void previewRejectsInvalidPlanExpiryAndCrossAccountUse() throws Exception {
        String suffix = suffix();
        User admin = user("platform-preview-" + suffix, Role.ROLE_ADMIN);
        User ownerA = user("owner-a-" + suffix, Role.ROLE_GERENTE);
        User ownerB = user("owner-b-" + suffix, Role.ROLE_GERENTE);
        String token = token(admin);
        JsonNode a = createAccount(token, ownerA, "a-" + suffix, 2);
        JsonNode b = createAccount(token, ownerB, "b-" + suffix, 2);

        mockMvc.perform(post("/platform/business-accounts/{id}/businesses/preview", a.path("id").asLong())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "invalid-plan-" + suffix)
                        .header("X-Correlation-Id", "corr-invalid-plan-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewPayload(a.path("version").asLong(), "invalid-" + suffix, "NO_SUCH_PLAN")))
                .andExpect(status().isBadRequest());

        String body = mockMvc.perform(post("/platform/business-accounts/{id}/businesses/preview", a.path("id").asLong())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "expiring-preview-" + suffix)
                        .header("X-Correlation-Id", "corr-expiring-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewPayload(a.path("version").asLong(), "expiring-" + suffix, "PILOTO")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode preview = objectMapper.readTree(body).path("data");
        BusinessProvisioningPreview entity = previews.findByPreviewId(preview.path("previewId").asText()).orElseThrow();
        entity.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        previews.saveAndFlush(entity);
        mockMvc.perform(post("/platform/business-accounts/{id}/businesses/provision", a.path("id").asLong())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "expired-provision-" + suffix)
                        .header("X-Correlation-Id", "corr-expired-provision-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(provisionPayload(preview, a.path("version").asLong(), true)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/platform/business-accounts/{id}/businesses/provision", b.path("id").asLong())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "cross-account-" + suffix)
                        .header("X-Correlation-Id", "corr-cross-account-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(provisionPayload(preview, b.path("version").asLong(), true)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentReplayCreatesOneOperationAndOneTenant() throws Exception {
        String suffix = suffix();
        User admin = user("platform-concurrent-" + suffix, Role.ROLE_ADMIN);
        User owner = user("owner-concurrent-" + suffix, Role.ROLE_GERENTE);
        String token = token(admin);
        JsonNode account = createAccount(token, owner, "concurrent-" + suffix, 1);
        long accountId = account.path("id").asLong();
        long version = account.path("version").asLong();
        String body = mockMvc.perform(post("/platform/business-accounts/{id}/businesses/preview", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "concurrent-preview-" + suffix)
                        .header("X-Correlation-Id", "corr-concurrent-preview-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(previewPayload(version, "concurrent-" + suffix, "PILOTO")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode preview = objectMapper.readTree(body).path("data");
        String provision = provisionPayload(preview, version, true);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> call = () -> mockMvc.perform(post("/platform/business-accounts/{id}/businesses/provision", accountId)
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "concurrent-provision-" + suffix)
                            .header("X-Correlation-Id", "corr-concurrent-provision-" + Thread.currentThread().getId())
                            .contentType(MediaType.APPLICATION_JSON).content(provision))
                    .andReturn().getResponse().getStatus();
            Future<Integer> one = executor.submit(call);
            Future<Integer> two = executor.submit(call);
            List<Integer> statuses = new ArrayList<>(List.of(one.get(), two.get()));
            Collections.sort(statuses);
            assertThat(statuses).containsExactly(200, 201);
            assertThat(tenants.countByBusinessAccountId(accountId)).isEqualTo(1);
            assertThat(operations.findByBusinessAccountIdAndIdempotencyKey(accountId,
                    "concurrent-provision-" + suffix)).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void ownerCapacityPreviewAndPrematureActivationAreGoverned() throws Exception {
        String suffix = suffix();
        User admin = user("platform-governance-" + suffix, Role.ROLE_ADMIN);
        User owner = user("owner-governance-" + suffix, Role.ROLE_GERENTE);
        String token = token(admin);

        mockMvc.perform(post("/platform/business-accounts")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "missing-owner-" + suffix)
                        .header("X-Correlation-Id", "corr-missing-owner-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Sem owner\",\"slug\":\"missing-owner-" + suffix + "\"}"))
                .andExpect(status().isBadRequest());

        BusinessAccount legacyWithoutOwner = new BusinessAccount();
        legacyWithoutOwner.setNome("Legacy sem owner " + suffix);
        legacyWithoutOwner.setSlug("legacy-without-owner-" + suffix);
        legacyWithoutOwner.setMaxTenants(1);
        legacyWithoutOwner.setEstado(BusinessAccountEstado.RASCUNHO);
        legacyWithoutOwner = accounts.saveAndFlush(legacyWithoutOwner);
        mockMvc.perform(post("/platform/business-accounts/{id}/activate", legacyWithoutOwner.getId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "activate-without-owner-" + suffix)
                        .header("X-Correlation-Id", "corr-activate-without-owner-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountVersion\":" + legacyWithoutOwner.getVersion()
                                + ",\"reason\":\"activacao deve ser recusada\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/platform/business-accounts/{id}/businesses/preview", Long.MAX_VALUE)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "missing-account-" + suffix)
                        .header("X-Correlation-Id", "corr-missing-account-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewPayload(0, "missing-account-" + suffix, "PILOTO")))
                .andExpect(status().isBadRequest());

        JsonNode account = createAccount(token, owner, "governance-" + suffix, 1);
        long accountId = account.path("id").asLong();
        long version = account.path("version").asLong();
        String previewBody = mockMvc.perform(post("/platform/business-accounts/{id}/businesses/preview", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "governance-preview-" + suffix)
                        .header("X-Correlation-Id", "corr-governance-preview-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewPayload(version, "governance-" + suffix, "PILOTO")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode preview = objectMapper.readTree(previewBody).path("data");

        mockMvc.perform(post("/platform/business-accounts/{id}/businesses/provision", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "missing-preview-" + suffix)
                        .header("X-Correlation-Id", "corr-missing-preview-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"previewId\":\"00000000-0000-0000-0000-000000000000\","
                                + "\"requestFingerprint\":\"" + preview.path("requestFingerprint").asText() + "\","
                                + "\"accountVersion\":" + version + ",\"confirmed\":true}"))
                .andExpect(status().isBadRequest());

        String operationBody = mockMvc.perform(post("/platform/business-accounts/{id}/businesses/provision", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "governance-provision-" + suffix)
                        .header("X-Correlation-Id", "corr-governance-provision-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(provisionPayload(preview, version, true)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long tenantId = objectMapper.readTree(operationBody).at("/data/tenantId").asLong();
        Tenant tenant = tenants.findById(tenantId).orElseThrow();

        mockMvc.perform(post("/platform/business-accounts/{accountId}/businesses/{tenantId}/activate", accountId, tenantId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "premature-activation-" + suffix)
                        .header("X-Correlation-Id", "corr-premature-activation-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountVersion\":" + version + ",\"tenantVersion\":" + tenant.getVersion()
                                + ",\"reason\":\"tentativa prematura\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/platform/business-accounts/{id}/businesses/preview", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "over-capacity-" + suffix)
                        .header("X-Correlation-Id", "corr-over-capacity-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewPayload(version, "over-capacity-" + suffix, "PILOTO")))
                .andExpect(status().isConflict());
    }

    @Test
    void failedProvisionPersistsSanitizedResultAndRollsBackOperationalEntities() throws Exception {
        String suffix = suffix();
        User admin = user("platform-failure-" + suffix, Role.ROLE_ADMIN);
        User owner = user("owner-failure-" + suffix, Role.ROLE_GERENTE);
        String token = token(admin);
        JsonNode account = createAccount(token, owner, "failure-" + suffix, 1);
        long accountId = account.path("id").asLong();
        long version = account.path("version").asLong();
        String previewBody = mockMvc.perform(post("/platform/business-accounts/{id}/businesses/preview", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "failure-preview-" + suffix)
                        .header("X-Correlation-Id", "corr-failure-preview-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewPayload(version, "failure-" + suffix, "PILOTO")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode preview = objectMapper.readTree(previewBody).path("data");
        long tenantsBefore = tenants.count();
        long institutionsBefore = instituicoes.count();
        long unitsBefore = unidades.count();
        String businessSlug = "business-failure-" + suffix;
        doThrow(new BusinessException("CONTROLLED_TEMPLATE_FAILURE"))
                .when(templateService).provision(eq("CONSUMA_PONTO_V1"),
                        argThat(request -> businessSlug.equals(request.getTenant().getSlug())));

        mockMvc.perform(post("/platform/business-accounts/{id}/businesses/provision", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "failure-provision-" + suffix)
                        .header("X-Correlation-Id", "corr-failure-provision-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(provisionPayload(preview, version, true)))
                .andExpect(status().isBadRequest());

        assertThat(tenants.count()).isEqualTo(tenantsBefore);
        assertThat(instituicoes.count()).isEqualTo(institutionsBefore);
        assertThat(unidades.count()).isEqualTo(unitsBefore);
        var failed = operations.findByBusinessAccountIdAndIdempotencyKey(accountId,
                "failure-provision-" + suffix).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo("FAILED_FINAL");
        assertThat(failed.getTenant()).isNull();
        assertThat(failed.getErrorCode()).isEqualTo("CONTROLLED_TEMPLATE_FAILURE");
        assertThat(failed.getErrorMessage()).isEqualTo("CONTROLLED_TEMPLATE_FAILURE");

        String replay = mockMvc.perform(post("/platform/business-accounts/{id}/businesses/provision", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "failure-provision-" + suffix)
                        .header("X-Correlation-Id", "corr-failure-replay-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(provisionPayload(preview, version, true)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(replay).at("/data/status").asText()).isEqualTo("FAILED_FINAL");
        assertThat(objectMapper.readTree(replay).at("/data/effectsCommitted").asBoolean()).isFalse();
        verify(templateService).provision(eq("CONSUMA_PONTO_V1"),
                argThat(request -> businessSlug.equals(request.getTenant().getSlug())));
    }

    private JsonNode createAccount(String token, User owner, String suffix, int maxTenants) throws Exception {
        String payload = "{\"nome\":\"Conta " + suffix + "\",\"slug\":\"account-" + suffix
                + "\",\"maxTenants\":" + maxTenants + ",\"responsavelPrincipal\":{"
                + "\"strategy\":\"ASSOCIATE_EXISTING\",\"userId\":" + owner.getId()
                + ",\"confirmExistingUser\":true}}";
        String body = mockMvc.perform(post("/platform/business-accounts")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "create-account-" + suffix)
                        .header("X-Correlation-Id", "corr-create-account-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private String previewPayload(long accountVersion, String suffix, String plan) {
        return """
                {
                  "accountVersion": %d,
                  "planoCodigo": "%s",
                  "vertical": "CONSUMA_PONTO",
                  "negocio": {
                    "nomeNegocio": "Negocio %s",
                    "slug": "business-%s",
                    "tenantCode": "B%s",
                    "tipo": "VENDEDOR_RUA",
                    "nif": "NIF-%s",
                    "telefone": "+24492%s"
                  },
                  "ponto": {"entregaManual": false, "allowPickup": true},
                  "acessos": {"strategy": "ACCOUNT_OWNER_AS_TENANT_OWNER", "additionalAccesses": []}
                }
                """.formatted(accountVersion, plan, suffix, suffix, digits(suffix, 8), suffix, digits(suffix, 9));
    }

    private String provisionPayload(JsonNode preview, long accountVersion, boolean confirmed) {
        return "{\"previewId\":\"" + preview.path("previewId").asText()
                + "\",\"requestFingerprint\":\"" + preview.path("requestFingerprint").asText()
                + "\",\"accountVersion\":" + accountVersion + ",\"confirmed\":" + confirmed + "}";
    }

    private User user(String name, Role role) {
        User user = new User();
        user.setUsername(name + "@test.local");
        user.setPassword("x");
        user.setEmail(name + "@test.local");
        user.setNomeCompleto(name);
        user.setTelefone("+2449" + digits(name, 8));
        user.setRoles(Set.of(role));
        user.setAtivo(true);
        return users.saveAndFlush(user);
    }

    private String token(User user) {
        return jwtTokenProvider.generateToken(user.getUsername(), "ROLE_ADMIN", null, user.getId(), "GLOBAL");
    }

    private String suffix() { return Long.toString(Math.abs(System.nanoTime() % 100_000_000L)); }
    private static String digits(String value, int length) {
        String digits = Integer.toUnsignedString(value.hashCode()).replaceAll("\\D", "");
        return (digits + "0000000000000000").substring(0, length);
    }
}
