package com.restaurante.businessprovisioning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.businesstemplate.BusinessTemplateService;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.BusinessAccount;
import com.restaurante.model.entity.BusinessProvisioningPreview;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.BusinessAccountMemberEstado;
import com.restaurante.model.enums.BusinessAccountRole;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserAccessOrigin;
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
import org.springframework.dao.TransientDataAccessResourceException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    @Autowired BusinessAccountGovernanceEventRepository governanceEvents;
    @Autowired CanonicalBusinessAccountNifRepository canonicalNifs;
    @Autowired SubscricaoRepository subscricoes;
    @Autowired PlanoRepository planos;
    @Autowired InstituicaoRepository instituicoes;
    @Autowired UnidadeAtendimentoRepository unidades;
    @Autowired TenantUserRepository tenantUsers;
    @Autowired TenantUserAccessVersionRepository accessVersions;
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

        mockMvc.perform(delete("/platform/business-accounts/{id}/tenants/{tenantId}", accountId, tenantId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

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

        String tenantDetailBefore = mockMvc.perform(get("/platform/tenants/{tenantId}", tenantId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode tenantDataBefore = objectMapper.readTree(tenantDetailBefore).path("data");
        assertThat(tenantDataBefore.path("version").isIntegralNumber()).isTrue();
        assertThat(tenantDataBefore.path("version").asLong()).isEqualTo(tenant.getVersion());
        assertThat(tenantDataBefore.path("estado").asText()).isEqualTo("RASCUNHO");

        String readinessBefore = mockMvc.perform(get("/platform/business-accounts/{accountId}/businesses/{tenantId}/readiness", accountId, tenantId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(readinessBefore).at("/data/ready").asBoolean()).isFalse();
        assertThat(readinessBefore).contains("ACCOUNT_ACTIVE");

        String accountActivation = "{\"accountVersion\":" + accountVersion
                + ",\"reason\":\"  readiness validado  \"}";
        String accountActivationKey = "activate-account-" + suffix;
        String activatedAccount = mockMvc.perform(post("/platform/business-accounts/{id}/activate", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", accountActivationKey)
                        .header("X-Correlation-Id", "corr-activate-account-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountActivation))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long activeAccountVersion = objectMapper.readTree(activatedAccount).at("/data/version").asLong();
        assertThat(activeAccountVersion).isGreaterThan(accountVersion);
        assertThat(objectMapper.readTree(activatedAccount).at("/data/estado").asText()).isEqualTo("ATIVA");
        tenant = tenants.findById(tenantId).orElseThrow();
        assertThat(tenant.getEstado()).isEqualTo(TenantEstado.RASCUNHO);
        assertThat(tenant.getVersion()).isEqualTo(tenantDataBefore.path("version").asLong());
        assertThat(governanceEvents.findAll().stream()
                .filter(e -> accountActivationKey.equals(e.getIdempotencyKey())
                        && "ACCOUNT_ACTIVATED".equals(e.getAction())).count()).isEqualTo(1);

        String accountReplay = mockMvc.perform(post("/platform/business-accounts/{id}/activate", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", accountActivationKey)
                        .header("X-Correlation-Id", "corr-activate-account-replay-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(accountActivation))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(accountReplay).at("/data/version").asLong()).isEqualTo(activeAccountVersion);
        assertThat(accounts.findById(accountId).orElseThrow().getVersion()).isEqualTo(activeAccountVersion);
        mockMvc.perform(post("/platform/business-accounts/{id}/activate", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", accountActivationKey)
                        .header("X-Correlation-Id", "corr-activate-account-conflict-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountVersion\":" + accountVersion + ",\"reason\":\"intenção diferente\"}"))
                .andExpect(status().isConflict());

        String readinessAfterAccount = mockMvc.perform(get("/platform/business-accounts/{accountId}/businesses/{tenantId}/readiness", accountId, tenantId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(readinessAfterAccount).at("/data/ready").asBoolean()).isTrue();
        assertThat(objectMapper.readTree(readinessAfterAccount).at("/data/blockers").toString())
                .doesNotContain("ACCOUNT_ACTIVE");

        Long tenantVersionBeforeActivation = tenant.getVersion();
        Long ownerIdBeforeActivation = accounts.findById(accountId).orElseThrow().getResponsavel().getId();
        String activation = "{\"accountVersion\":" + activeAccountVersion
                + ",\"tenantVersion\":" + tenantVersionBeforeActivation
                + ",\"reason\":\"  activar após readiness  \"}";
        String businessActivationKey = "activate-business-" + suffix;
        String activatedBusiness = mockMvc.perform(post("/platform/business-accounts/{accountId}/businesses/{tenantId}/activate", accountId, tenantId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", businessActivationKey)
                        .header("X-Correlation-Id", "corr-activate-business-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(activation))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode activatedBusinessData = objectMapper.readTree(activatedBusiness).path("data");
        assertThat(activatedBusinessData.path("ready").asBoolean()).isTrue();
        assertThat(activatedBusinessData.path("estado").asText()).isEqualTo("ATIVO");
        assertThat(activatedBusinessData.path("accountVersion").asLong()).isEqualTo(activeAccountVersion);
        assertThat(activatedBusinessData.path("tenantVersion").asLong()).isGreaterThan(tenantVersionBeforeActivation);
        Long activeTenantVersion = activatedBusinessData.path("tenantVersion").asLong();
        assertThat(tenants.findById(tenantId).orElseThrow().getEstado()).isEqualTo(TenantEstado.ATIVO);
        assertThat(accounts.findById(accountId).orElseThrow().getResponsavel().getId()).isEqualTo(ownerIdBeforeActivation);
        assertThat(subscricoes.findByTenantIdAndEstado(tenantId, SubscricaoEstado.ATIVA).orElseThrow()
                .getPlano().getId()).isEqualTo(planoId);
        assertThat(governanceEvents.findAll().stream()
                .filter(e -> businessActivationKey.equals(e.getIdempotencyKey())
                        && "BUSINESS_ACTIVATED".equals(e.getAction())).count()).isEqualTo(1);

        String businessReplay = mockMvc.perform(post("/platform/business-accounts/{accountId}/businesses/{tenantId}/activate", accountId, tenantId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", businessActivationKey)
                        .header("X-Correlation-Id", "corr-activate-business-replay-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(activation))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(businessReplay).at("/data/tenantVersion").asLong()).isEqualTo(activeTenantVersion);
        assertThat(tenants.findById(tenantId).orElseThrow().getVersion()).isEqualTo(activeTenantVersion);
        mockMvc.perform(post("/platform/business-accounts/{accountId}/businesses/{tenantId}/activate", accountId, tenantId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", businessActivationKey)
                        .header("X-Correlation-Id", "corr-activate-business-conflict-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activation.replace("activar após readiness", "intenção diferente")))
                .andExpect(status().isConflict());

        String tenantDetailAfter = mockMvc.perform(get("/platform/tenants/{tenantId}", tenantId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(tenantDetailAfter).at("/data/estado").asText()).isEqualTo("ATIVO");
        assertThat(objectMapper.readTree(tenantDetailAfter).at("/data/version").asLong()).isEqualTo(activeTenantVersion);
    }

    @Test
    void activationRejectsStaleVersionsCrossAccountAndChangedReadiness() throws Exception {
        String suffix = "activation-" + suffix();
        User admin = user("platform-" + suffix, Role.ROLE_ADMIN);
        User owner = user("owner-" + suffix, Role.ROLE_GERENTE);
        String token = token(admin);
        PreparedLifecycle prepared = prepareDraftBusiness(token, owner, suffix);

        mockMvc.perform(post("/platform/business-accounts/{id}/activate", prepared.accountId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "stale-account-" + suffix)
                        .header("X-Correlation-Id", "corr-stale-account-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountVersion\":" + (prepared.accountVersion() + 1)
                                + ",\"reason\":\"versão stale\"}"))
                .andExpect(status().isConflict());
        assertThat(accounts.findById(prepared.accountId()).orElseThrow().getEstado())
                .isEqualTo(BusinessAccountEstado.RASCUNHO);

        mockMvc.perform(post("/platform/business-accounts/{id}/activate", prepared.accountId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "blank-account-reason-" + suffix)
                        .header("X-Correlation-Id", "corr-blank-account-reason-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountVersion\":" + prepared.accountVersion() + ",\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());

        JsonNode activated = activateAccount(token, prepared.accountId(), prepared.accountVersion(),
                "valid-account-" + suffix, "activação explícita");
        long activeAccountVersion = activated.path("version").asLong();
        long tenantVersion = tenants.findById(prepared.tenantId()).orElseThrow().getVersion();

        mockMvc.perform(post("/platform/business-accounts/{accountId}/businesses/{tenantId}/activate",
                        prepared.accountId(), prepared.tenantId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "stale-business-account-" + suffix)
                        .header("X-Correlation-Id", "corr-stale-business-account-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessActivation(activeAccountVersion + 1, tenantVersion, "stale account")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/platform/business-accounts/{accountId}/businesses/{tenantId}/activate",
                        prepared.accountId(), prepared.tenantId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "stale-tenant-" + suffix)
                        .header("X-Correlation-Id", "corr-stale-tenant-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessActivation(activeAccountVersion, tenantVersion + 1, "stale tenant")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/platform/business-accounts/{accountId}/businesses/{tenantId}/activate",
                        prepared.accountId(), prepared.tenantId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "both-stale-" + suffix)
                        .header("X-Correlation-Id", "corr-both-stale-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessActivation(activeAccountVersion + 1, tenantVersion + 1, "ambas stale")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/platform/business-accounts/{accountId}/businesses/{tenantId}/activate",
                        prepared.accountId(), prepared.tenantId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "blank-business-reason-" + suffix)
                        .header("X-Correlation-Id", "corr-blank-business-reason-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessActivation(activeAccountVersion, tenantVersion, "   ")))
                .andExpect(status().isBadRequest());
        assertThat(tenants.findById(prepared.tenantId()).orElseThrow().getEstado()).isEqualTo(TenantEstado.RASCUNHO);

        User otherOwner = user("other-owner-" + suffix, Role.ROLE_GERENTE);
        JsonNode otherAccount = createAccount(token, otherOwner, "other-" + suffix, 1);
        JsonNode otherActive = activateAccount(token, otherAccount.path("id").asLong(),
                otherAccount.path("version").asLong(), "activate-other-" + suffix, "activar outra conta");
        mockMvc.perform(post("/platform/business-accounts/{accountId}/businesses/{tenantId}/activate",
                        otherAccount.path("id").asLong(), prepared.tenantId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "cross-account-activation-" + suffix)
                        .header("X-Correlation-Id", "corr-cross-account-activation-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessActivation(otherActive.path("version").asLong(), tenantVersion,
                                "tenant de outra conta")))
                .andExpect(status().isConflict());

        String readyBeforeChange = mockMvc.perform(get("/platform/business-accounts/{accountId}/businesses/{tenantId}/readiness",
                        prepared.accountId(), prepared.tenantId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(readyBeforeChange).at("/data/ready").asBoolean()).isTrue();
        owner.setAtivo(false);
        users.saveAndFlush(owner);
        String changedReadiness = mockMvc.perform(post("/platform/business-accounts/{accountId}/businesses/{tenantId}/activate",
                        prepared.accountId(), prepared.tenantId())
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "changed-readiness-" + suffix)
                        .header("X-Correlation-Id", "corr-changed-readiness-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessActivation(activeAccountVersion, tenantVersion, "readiness deve ser reavaliado")))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertThat(changedReadiness).contains("BUSINESS_READINESS_INCOMPLETE", "ACCOUNT_OWNER");
        assertThat(tenants.findById(prepared.tenantId()).orElseThrow().getEstado()).isEqualTo(TenantEstado.RASCUNHO);
    }

    @Test
    void concurrentActivationsConvergeWithoutDuplicateMutationOrEvent() throws Exception {
        String suffix = "activation-race-" + suffix();
        User admin = user("platform-" + suffix, Role.ROLE_ADMIN);
        User owner = user("owner-" + suffix, Role.ROLE_GERENTE);
        String token = token(admin);
        JsonNode account = createAccount(token, owner, "account-" + suffix, 1);
        long accountId = account.path("id").asLong();
        long accountVersion = account.path("version").asLong();
        String accountKey = "account-race-" + suffix;
        String accountPayload = "{\"accountVersion\":" + accountVersion + ",\"reason\":\"corrida idempotente\"}";

        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> accountCall = () -> mockMvc.perform(post("/platform/business-accounts/{id}/activate", accountId)
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", accountKey)
                            .header("X-Correlation-Id", "corr-account-race-" + Thread.currentThread().getId())
                            .contentType(MediaType.APPLICATION_JSON).content(accountPayload))
                    .andReturn().getResponse().getStatus();
            Future<Integer> one = executor.submit(accountCall);
            Future<Integer> two = executor.submit(accountCall);
            assertThat(List.of(one.get(), two.get())).containsOnly(200);
        } finally {
            executor.shutdownNow();
        }
        BusinessAccount activeAccount = accounts.findById(accountId).orElseThrow();
        assertThat(activeAccount.getEstado()).isEqualTo(BusinessAccountEstado.ATIVA);
        assertThat(activeAccount.getVersion()).isEqualTo(accountVersion + 1);
        assertThat(governanceEvents.findAll().stream()
                .filter(e -> accountKey.equals(e.getIdempotencyKey()) && "ACCOUNT_ACTIVATED".equals(e.getAction())).count())
                .isEqualTo(1);

        User conflictingOwner = user("conflicting-owner-" + suffix, Role.ROLE_GERENTE);
        JsonNode conflictingAccount = createAccount(token, conflictingOwner, "conflicting-" + suffix, 1);
        long conflictingId = conflictingAccount.path("id").asLong();
        long conflictingVersion = conflictingAccount.path("version").asLong();
        String conflictingKey = "account-conflicting-race-" + suffix;
        var conflictExecutor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> firstIntent = () -> accountActivationStatus(token, conflictingId, conflictingVersion,
                    conflictingKey, "primeira intenção");
            Callable<Integer> secondIntent = () -> accountActivationStatus(token, conflictingId, conflictingVersion,
                    conflictingKey, "segunda intenção");
            Future<Integer> one = conflictExecutor.submit(firstIntent);
            Future<Integer> two = conflictExecutor.submit(secondIntent);
            List<Integer> statuses = new ArrayList<>(List.of(one.get(), two.get()));
            Collections.sort(statuses);
            assertThat(statuses).containsExactly(200, 409);
        } finally {
            conflictExecutor.shutdownNow();
        }
        assertThat(governanceEvents.findAll().stream()
                .filter(e -> conflictingKey.equals(e.getIdempotencyKey()) && "ACCOUNT_ACTIVATED".equals(e.getAction())).count())
                .isEqualTo(1);

        User businessOwner = user("business-owner-" + suffix, Role.ROLE_GERENTE);
        String businessFixtureSuffix = "br-" + this.suffix();
        PreparedLifecycle prepared = prepareDraftBusiness(token, businessOwner, businessFixtureSuffix);
        JsonNode businessAccount = activateAccount(token, prepared.accountId(), prepared.accountVersion(),
                "business-account-active-" + suffix, "activar conta para corrida");
        long activeAccountVersion = businessAccount.path("version").asLong();
        long tenantVersion = tenants.findById(prepared.tenantId()).orElseThrow().getVersion();
        String businessKey = "business-race-" + suffix;
        String businessPayload = businessActivation(activeAccountVersion, tenantVersion, "corrida idempotente do negócio");
        var businessExecutor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> businessCall = () -> mockMvc.perform(post(
                            "/platform/business-accounts/{accountId}/businesses/{tenantId}/activate",
                            prepared.accountId(), prepared.tenantId())
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", businessKey)
                            .header("X-Correlation-Id", "corr-business-race-" + Thread.currentThread().getId())
                            .contentType(MediaType.APPLICATION_JSON).content(businessPayload))
                    .andReturn().getResponse().getStatus();
            Future<Integer> one = businessExecutor.submit(businessCall);
            Future<Integer> two = businessExecutor.submit(businessCall);
            assertThat(List.of(one.get(), two.get())).containsOnly(200);
        } finally {
            businessExecutor.shutdownNow();
        }
        Tenant activeTenant = tenants.findById(prepared.tenantId()).orElseThrow();
        assertThat(activeTenant.getEstado()).isEqualTo(TenantEstado.ATIVO);
        assertThat(activeTenant.getVersion()).isEqualTo(tenantVersion + 1);
        assertThat(governanceEvents.findAll().stream()
                .filter(e -> businessKey.equals(e.getIdempotencyKey()) && "BUSINESS_ACTIVATED".equals(e.getAction())).count())
                .isEqualTo(1);
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
    void ownerReplacementSynchronizesExistingBusinessesPreservesExplicitRolesAndInvalidatesStaleToken() throws Exception {
        String suffix = "owner-sync-" + suffix();
        User admin = user("platform-" + suffix, Role.ROLE_ADMIN);
        User oldOwner = user("old-" + suffix, Role.ROLE_GERENTE);
        User newOwner = user("new-" + suffix, Role.ROLE_GERENTE);
        String platformToken = token(admin);
        JsonNode account = createAccount(platformToken, oldOwner, suffix, 3);
        long accountId = account.path("id").asLong();
        long initialVersion = account.path("version").asLong();
        long activeTenantId = provisionDraftBusiness(
                platformToken, accountId, initialVersion, suffix + "-active", "owner-sync-a-");
        long draftTenantId = provisionDraftBusiness(
                platformToken, accountId, initialVersion, suffix + "-draft", "owner-sync-b-");

        JsonNode activeAccount = activateAccount(
                platformToken, accountId, initialVersion, "activate-" + suffix, "activar conta");
        long accountVersion = activeAccount.path("version").asLong();
        Tenant activeTenant = tenants.findById(activeTenantId).orElseThrow();
        mockMvc.perform(post("/platform/business-accounts/{accountId}/businesses/{tenantId}/activate",
                        accountId, activeTenantId)
                        .header("Authorization", "Bearer " + platformToken)
                        .header("Idempotency-Key", "activate-business-" + suffix)
                        .header("X-Correlation-Id", "corr-activate-business-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessActivation(accountVersion, activeTenant.getVersion(), "activar negócio")))
                .andExpect(status().isOk());

        TenantUser explicitOperator = new TenantUser();
        explicitOperator.setTenant(tenants.findById(activeTenantId).orElseThrow());
        explicitOperator.setUser(oldOwner);
        explicitOperator.setRole(TenantUserRole.TENANT_OPERATOR);
        explicitOperator.setEstado(TenantUserEstado.ATIVO);
        explicitOperator.setAccessOrigin(TenantUserAccessOrigin.EXPLICIT);
        tenantUsers.saveAndFlush(explicitOperator);

        String oldGlobal = userToken(oldOwner);
        String oldTenantToken = selectTenantToken(oldGlobal, activeTenantId);
        String replacePayload = "{\"accountVersion\":" + accountVersion + ",\"novoResponsavel\":{"
                + "\"strategy\":\"ASSOCIATE_EXISTING\",\"userId\":" + newOwner.getId()
                + ",\"confirmExistingUser\":true},\"reason\":\"sincronização owner\"}";
        String replacement = mockMvc.perform(post("/platform/business-accounts/{id}/owner/replace", accountId)
                        .header("Authorization", "Bearer " + platformToken)
                        .header("Idempotency-Key", "replace-" + suffix)
                        .header("X-Correlation-Id", "corr-replace-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(replacePayload))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long replacedVersion = objectMapper.readTree(replacement).at("/data/version").asLong();

        for (Long tenantId : List.of(activeTenantId, draftTenantId)) {
            assertThat(tenantUsers.existsByTenantIdAndUserIdAndRoleAndEstadoAndAccessOrigin(
                    tenantId, newOwner.getId(), TenantUserRole.TENANT_OWNER, TenantUserEstado.ATIVO,
                    TenantUserAccessOrigin.BUSINESS_ACCOUNT_OWNER)).isTrue();
            assertThat(tenantUsers.findByTenantIdAndUserIdAndRoleAndAccessOrigin(
                    tenantId, oldOwner.getId(), TenantUserRole.TENANT_OWNER,
                    TenantUserAccessOrigin.BUSINESS_ACCOUNT_OWNER).orElseThrow().getEstado())
                    .isEqualTo(TenantUserEstado.REMOVIDO);
        }
        assertThat(tenantUsers.existsByTenantIdAndUserIdAndRoleAndEstado(
                activeTenantId, oldOwner.getId(), TenantUserRole.TENANT_OPERATOR, TenantUserEstado.ATIVO)).isTrue();

        mockMvc.perform(get("/tenant/me").header("Authorization", "Bearer " + oldTenantToken))
                .andExpect(status().isUnauthorized());

        String newGlobal = userToken(newOwner);
        JsonNode visible = objectMapper.readTree(mockMvc.perform(get("/auth/tenants")
                        .header("Authorization", "Bearer " + newGlobal))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(visible.findValuesAsText("tenantId")).contains(Long.toString(activeTenantId));
        assertThat(visible.findValuesAsText("tenantId")).doesNotContain(Long.toString(draftTenantId));
        JsonNode pontoOption = java.util.stream.StreamSupport.stream(visible.spliterator(), false)
                .filter(node -> node.path("tenantId").asLong() == activeTenantId)
                .findFirst().orElseThrow();
        assertThat(pontoOption.path("templateCode").asText()).isEqualTo("CONSUMA_PONTO");
        String newTenantToken = selectTenantToken(newGlobal, activeTenantId);
        mockMvc.perform(get("/tenant/me").header("Authorization", "Bearer " + newTenantToken))
                .andExpect(status().isOk());

        String readiness = mockMvc.perform(get(
                        "/platform/business-accounts/{accountId}/businesses/{tenantId}/readiness",
                        accountId, draftTenantId)
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(readiness).at("/data/blockers").toString())
                .doesNotContain("OPERATIONAL_ADMIN_ACCESS");

        int oldVersionBeforeReplay = accessVersions.findAccessVersion(activeTenantId, oldOwner.getId()).orElse(1);
        int newVersionBeforeReplay = accessVersions.findAccessVersion(activeTenantId, newOwner.getId()).orElse(1);
        mockMvc.perform(post("/platform/business-accounts/{id}/owner/replace", accountId)
                        .header("Authorization", "Bearer " + platformToken)
                        .header("Idempotency-Key", "replace-" + suffix)
                        .header("X-Correlation-Id", "corr-replace-replay-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(replacePayload))
                .andExpect(status().isOk());
        assertThat(accessVersions.findAccessVersion(activeTenantId, oldOwner.getId()).orElse(1))
                .isEqualTo(oldVersionBeforeReplay);
        assertThat(accessVersions.findAccessVersion(activeTenantId, newOwner.getId()).orElse(1))
                .isEqualTo(newVersionBeforeReplay);

        long newTenantId = provisionDraftBusiness(
                platformToken, accountId, replacedVersion, suffix + "-new", "owner-sync-c-");
        assertThat(tenantUsers.existsByTenantIdAndUserIdAndRoleAndEstadoAndAccessOrigin(
                newTenantId, newOwner.getId(), TenantUserRole.TENANT_OWNER, TenantUserEstado.ATIVO,
                TenantUserAccessOrigin.BUSINESS_ACCOUNT_OWNER)).isTrue();
        assertThat(tenantUsers.existsByTenantIdAndUserIdAndRoleAndEstadoAndAccessOrigin(
                newTenantId, oldOwner.getId(), TenantUserRole.TENANT_OWNER, TenantUserEstado.ATIVO,
                TenantUserAccessOrigin.BUSINESS_ACCOUNT_OWNER)).isFalse();
    }

    @Test
    void managerGovernanceDoesNotGrantOrRevokeExplicitOperationalAccess() throws Exception {
        String suffix = "manager-separation-" + suffix();
        User admin = user("platform-" + suffix, Role.ROLE_ADMIN);
        User owner = user("owner-" + suffix, Role.ROLE_GERENTE);
        User manager = user("manager-" + suffix, Role.ROLE_GERENTE);
        String platformToken = token(admin);
        JsonNode account = createAccount(platformToken, owner, suffix, 1);
        long accountId = account.path("id").asLong();
        long initialVersion = account.path("version").asLong();
        String businessSuffix = "mgr-" + suffix();
        long tenantId = provisionDraftBusiness(
                platformToken, accountId, initialVersion, businessSuffix, "manager-separation-");
        long activeAccountVersion = activateAccount(
                platformToken, accountId, initialVersion, "activate-" + suffix, "activar conta")
                .path("version").asLong();
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        mockMvc.perform(post("/platform/business-accounts/{accountId}/businesses/{tenantId}/activate",
                        accountId, tenantId)
                        .header("Authorization", "Bearer " + platformToken)
                        .header("Idempotency-Key", "activate-business-" + suffix)
                        .header("X-Correlation-Id", "corr-activate-business-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessActivation(activeAccountVersion, tenant.getVersion(), "activar negócio")))
                .andExpect(status().isOk());

        String addManager = "{\"accountVersion\":" + activeAccountVersion
                + ",\"userId\":" + manager.getId()
                + ",\"role\":\"ADMIN\",\"estado\":\"ATIVO\",\"reason\":\"gestão empresarial\"}";
        mockMvc.perform(post("/platform/business-accounts/{id}/managers", accountId)
                        .header("Authorization", "Bearer " + platformToken)
                        .header("Idempotency-Key", "add-manager-" + suffix)
                        .header("X-Correlation-Id", "corr-add-manager-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(addManager))
                .andExpect(status().isOk());
        assertThat(tenantUsers.existsByTenantIdAndUserId(tenantId, manager.getId())).isFalse();

        String managerGlobal = userToken(manager);
        JsonNode empty = objectMapper.readTree(mockMvc.perform(get("/auth/tenants")
                        .header("Authorization", "Bearer " + managerGlobal))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(empty.size()).isZero();
        mockMvc.perform(post("/auth/tenant/select")
                        .header("Authorization", "Bearer " + managerGlobal)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tenantId\":" + tenantId + "}"))
                .andExpect(status().isForbidden());

        TenantUser explicit = new TenantUser();
        explicit.setTenant(tenants.findById(tenantId).orElseThrow());
        explicit.setUser(manager);
        explicit.setRole(TenantUserRole.TENANT_OPERATOR);
        explicit.setEstado(TenantUserEstado.ATIVO);
        explicit.setAccessOrigin(TenantUserAccessOrigin.EXPLICIT);
        tenantUsers.saveAndFlush(explicit);

        long versionAfterAdd = accounts.findById(accountId).orElseThrow().getVersion();
        String removeManager = "{\"accountVersion\":" + versionAfterAdd
                + ",\"userId\":" + manager.getId()
                + ",\"role\":\"ADMIN\",\"estado\":\"REMOVIDO\",\"reason\":\"fim da governance\"}";
        mockMvc.perform(post("/platform/business-accounts/{id}/managers", accountId)
                        .header("Authorization", "Bearer " + platformToken)
                        .header("Idempotency-Key", "remove-manager-" + suffix)
                        .header("X-Correlation-Id", "corr-remove-manager-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(removeManager))
                .andExpect(status().isOk());

        assertThat(tenantUsers.existsByTenantIdAndUserIdAndRoleAndEstado(
                tenantId, manager.getId(), TenantUserRole.TENANT_OPERATOR, TenantUserEstado.ATIVO)).isTrue();
        JsonNode visible = objectMapper.readTree(mockMvc.perform(get("/auth/tenants")
                        .header("Authorization", "Bearer " + managerGlobal))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(visible.findValuesAsText("tenantId")).contains(Long.toString(tenantId));
        selectTenantToken(managerGlobal, tenantId);
    }

    @Test
    void concurrentOwnerReplacementsLeaveOneGovernanceAndDerivedOwner() throws Exception {
        String suffix = suffix();
        User admin = user("platform-owner-race-" + suffix, Role.ROLE_ADMIN);
        User ownerA = user("owner-race-a-" + suffix, Role.ROLE_GERENTE);
        User ownerB = user("owner-race-b-" + suffix, Role.ROLE_GERENTE);
        User ownerC = user("owner-race-c-" + suffix, Role.ROLE_GERENTE);
        String platformToken = token(admin);
        JsonNode account = createAccount(platformToken, ownerA, "owner-race-" + suffix, 1);
        long accountId = account.path("id").asLong();
        long version = account.path("version").asLong();
        long tenantId = provisionDraftBusiness(
                platformToken, accountId, version, "or-" + suffix, "owner-race-");

        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> replaceB = () -> replaceOwnerStatus(
                    platformToken, accountId, version, ownerB, "owner-race-b-" + suffix);
            Callable<Integer> replaceC = () -> replaceOwnerStatus(
                    platformToken, accountId, version, ownerC, "owner-race-c-" + suffix);
            Future<Integer> futureB = executor.submit(replaceB);
            Future<Integer> futureC = executor.submit(replaceC);
            List<Integer> statuses = new ArrayList<>(List.of(futureB.get(), futureC.get()));
            Collections.sort(statuses);
            assertThat(statuses).containsExactly(200, 409);
        } finally {
            executor.shutdownNow();
        }

        User finalOwner = accounts.findById(accountId).orElseThrow().getResponsavel();
        assertThat(finalOwner.getId()).isIn(ownerB.getId(), ownerC.getId());
        assertThat(tenantUsers.countByTenantIdAndRoleAndEstadoAndAccessOrigin(
                tenantId, TenantUserRole.TENANT_OWNER, TenantUserEstado.ATIVO,
                TenantUserAccessOrigin.BUSINESS_ACCOUNT_OWNER)).isEqualTo(1);
        assertThat(tenantUsers.existsByTenantIdAndUserIdAndRoleAndEstadoAndAccessOrigin(
                tenantId, finalOwner.getId(), TenantUserRole.TENANT_OWNER, TenantUserEstado.ATIVO,
                TenantUserAccessOrigin.BUSINESS_ACCOUNT_OWNER)).isTrue();
    }

    @Test
    void concurrentOwnerReplacementAndProvisioningNeverLeaveStaleDerivedOwner() throws Exception {
        String suffix = suffix();
        User admin = user("platform-owner-provision-race-" + suffix, Role.ROLE_ADMIN);
        User oldOwner = user("owner-provision-old-" + suffix, Role.ROLE_GERENTE);
        User newOwner = user("owner-provision-new-" + suffix, Role.ROLE_GERENTE);
        String platformToken = token(admin);
        JsonNode account = createAccount(platformToken, oldOwner, "owner-provision-race-" + suffix, 1);
        long accountId = account.path("id").asLong();
        long version = account.path("version").asLong();
        JsonNode preview = preview(
                platformToken, accountId, version, "opr-" + suffix, "owner-provision-preview-" + suffix);
        String provisionPayload = provisionPayload(preview, version, true);

        var executor = Executors.newFixedThreadPool(2);
        int replaceStatus;
        int provisionStatus;
        try {
            Future<Integer> replace = executor.submit(() -> replaceOwnerStatus(
                    platformToken, accountId, version, newOwner, "owner-provision-replace-" + suffix));
            Future<Integer> provision = executor.submit(() -> mockMvc.perform(post(
                            "/platform/business-accounts/{id}/businesses/provision", accountId)
                            .header("Authorization", "Bearer " + platformToken)
                            .header("Idempotency-Key", "owner-provision-command-" + suffix)
                            .header("X-Correlation-Id", "corr-owner-provision-" + suffix)
                            .contentType(MediaType.APPLICATION_JSON).content(provisionPayload))
                    .andReturn().getResponse().getStatus());
            replaceStatus = replace.get();
            provisionStatus = provision.get();
        } finally {
            executor.shutdownNow();
        }

        assertThat(replaceStatus).isEqualTo(200);
        assertThat(provisionStatus).isIn(201, 409);
        assertThat(accounts.findById(accountId).orElseThrow().getResponsavel().getId())
                .isEqualTo(newOwner.getId());
        List<Tenant> created = tenants.findByBusinessAccountIdOrderByIdAsc(accountId);
        if (!created.isEmpty()) {
            Long tenantId = created.getFirst().getId();
            assertThat(tenantUsers.existsByTenantIdAndUserIdAndRoleAndEstadoAndAccessOrigin(
                    tenantId, newOwner.getId(), TenantUserRole.TENANT_OWNER, TenantUserEstado.ATIVO,
                    TenantUserAccessOrigin.BUSINESS_ACCOUNT_OWNER)).isTrue();
            assertThat(tenantUsers.existsByTenantIdAndUserIdAndRoleAndEstadoAndAccessOrigin(
                    tenantId, oldOwner.getId(), TenantUserRole.TENANT_OWNER, TenantUserEstado.ATIVO,
                    TenantUserAccessOrigin.BUSINESS_ACCOUNT_OWNER)).isFalse();
        }
    }

    @Test
    void canonicalRestBusinessIssuesTenantJwtAndKeepsTemplateRouting() throws Exception {
        String suffix = suffix();
        User admin = user("platform-rest-auth-" + suffix, Role.ROLE_ADMIN);
        User owner = user("owner-rest-auth-" + suffix, Role.ROLE_GERENTE);
        String platformToken = token(admin);
        JsonNode account = createAccount(platformToken, owner, "rest-auth-" + suffix, 1);
        long accountId = account.path("id").asLong();
        long accountVersion = account.path("version").asLong();
        JsonNode preview = previewRest(platformToken, accountId, accountVersion, "rest-" + suffix);
        String provisioned = mockMvc.perform(post("/platform/business-accounts/{id}/businesses/provision", accountId)
                        .header("Authorization", "Bearer " + platformToken)
                        .header("Idempotency-Key", "rest-provision-" + suffix)
                        .header("X-Correlation-Id", "corr-rest-provision-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(provisionPayload(preview, accountVersion, true)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long tenantId = objectMapper.readTree(provisioned).at("/data/tenantId").asLong();
        long activeAccountVersion = activateAccount(
                platformToken, accountId, accountVersion, "rest-account-activate-" + suffix, "activar REST")
                .path("version").asLong();
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        mockMvc.perform(post("/platform/business-accounts/{accountId}/businesses/{tenantId}/activate",
                        accountId, tenantId)
                        .header("Authorization", "Bearer " + platformToken)
                        .header("Idempotency-Key", "rest-business-activate-" + suffix)
                        .header("X-Correlation-Id", "corr-rest-business-activate-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(businessActivation(activeAccountVersion, tenant.getVersion(), "activar REST")))
                .andExpect(status().isOk());

        assertThat(tenants.findById(tenantId).orElseThrow().getTemplateCode()).isEqualTo("CONSUMA_REST");
        String ownerGlobal = userToken(owner);
        JsonNode options = objectMapper.readTree(mockMvc.perform(get("/auth/tenants")
                        .header("Authorization", "Bearer " + ownerGlobal))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        JsonNode option = java.util.stream.StreamSupport.stream(options.spliterator(), false)
                .filter(node -> node.path("tenantId").asLong() == tenantId)
                .findFirst().orElseThrow();
        assertThat(option.path("templateCode").asText()).isEqualTo("CONSUMA_REST");
        String tenantToken = selectTenantToken(ownerGlobal, tenantId);
        mockMvc.perform(get("/tenant/me").header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isOk());
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
                .andExpect(status().isNotFound());

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

    @Test
    void abandonedPendingIsRecoveredWithSameOperationAndIncrementedAttempt() throws Exception {
        String suffix = suffix();
        User admin = user("platform-pending-" + suffix, Role.ROLE_ADMIN);
        User owner = user("owner-pending-" + suffix, Role.ROLE_GERENTE);
        String token = token(admin);
        JsonNode account = createAccount(token, owner, "pending-" + suffix, 1);
        long accountId = account.path("id").asLong();
        long version = account.path("version").asLong();
        JsonNode preview = preview(token, accountId, version, "pending-" + suffix, "pending-preview-" + suffix);
        String payload = provisionPayload(preview, version, true);
        String slug = "business-pending-" + suffix;
        doThrow(new BusinessException("SIMULATED_CRASH_AFTER_PENDING")).doCallRealMethod()
                .when(templateService).provision(eq("CONSUMA_PONTO_V1"),
                        argThat(request -> slug.equals(request.getTenant().getSlug())));
        mockMvc.perform(post("/platform/business-accounts/{id}/businesses/provision", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "pending-provision-" + suffix)
                        .header("X-Correlation-Id", "corr-pending-first-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
        var pending = operations.findByBusinessAccountIdAndIdempotencyKey(accountId, "pending-provision-" + suffix).orElseThrow();
        String operationId = pending.getOperationId();
        pending.setStatus("PENDING");
        pending.setAttemptCount(0);
        pending.setLeaseOwner("dead-worker");
        pending.setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        pending.setNextRetryAt(null);
        pending.setCompletedAt(null);
        operations.saveAndFlush(pending);

        String recovered = mockMvc.perform(post("/platform/business-accounts/{id}/businesses/provision", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "pending-provision-" + suffix)
                        .header("X-Correlation-Id", "corr-pending-recovery-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode result = objectMapper.readTree(recovered).path("data");
        assertThat(result.path("operationId").asText()).isEqualTo(operationId);
        assertThat(result.path("attemptCount").asInt()).isEqualTo(1);
        assertThat(result.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(result.path("terminal").asBoolean()).isTrue();
        assertThat(tenants.countByBusinessAccountId(accountId)).isEqualTo(1);
    }

    @Test
    void failedRetryableAllowsExactlyOneOfTwoConcurrentResumes() throws Exception {
        String suffix = suffix();
        User admin = user("platform-retry-" + suffix, Role.ROLE_ADMIN);
        User owner = user("owner-retry-" + suffix, Role.ROLE_GERENTE);
        String token = token(admin);
        JsonNode account = createAccount(token, owner, "retry-" + suffix, 1);
        long accountId = account.path("id").asLong();
        long version = account.path("version").asLong();
        JsonNode preview = preview(token, accountId, version, "retry-" + suffix, "retry-preview-" + suffix);
        String payload = provisionPayload(preview, version, true);
        String slug = "business-retry-" + suffix;
        doThrow(new TransientDataAccessResourceException("temporary database failure")).doCallRealMethod()
                .when(templateService).provision(eq("CONSUMA_PONTO_V1"),
                        argThat(request -> slug.equals(request.getTenant().getSlug())));
        mockMvc.perform(post("/platform/business-accounts/{id}/businesses/provision", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "retry-provision-" + suffix)
                        .header("X-Correlation-Id", "corr-retry-first-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().is5xxServerError());
        var failed = operations.findByBusinessAccountIdAndIdempotencyKey(accountId, "retry-provision-" + suffix).orElseThrow();
        String operationId = failed.getOperationId();
        assertThat(failed.getStatus()).isEqualTo("FAILED_RETRYABLE");
        assertThat(failed.getAttemptCount()).isEqualTo(1);

        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<String> resume = () -> mockMvc.perform(post("/platform/provisioning-operations/{id}/resume", operationId)
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", "retry-provision-" + suffix)
                            .header("X-Correlation-Id", "corr-resume-" + Thread.currentThread().getId()))
                    .andReturn().getResponse().getContentAsString();
            Future<String> one = executor.submit(resume);
            Future<String> two = executor.submit(resume);
            assertThat(objectMapper.readTree(one.get()).at("/data/operationId").asText()).isEqualTo(operationId);
            assertThat(objectMapper.readTree(two.get()).at("/data/operationId").asText()).isEqualTo(operationId);
        } finally {
            executor.shutdownNow();
        }
        var succeeded = operations.findByOperationId(operationId).orElseThrow();
        assertThat(succeeded.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(succeeded.getAttemptCount()).isEqualTo(2);
        assertThat(tenants.countByBusinessAccountId(accountId)).isEqualTo(1);
    }

    @Test
    void operationalAccessAndHeadersFollowCanonicalPolicy() throws Exception {
        String suffix = suffix();
        User admin = user("platform-access-" + suffix, Role.ROLE_ADMIN);
        User owner = user("owner-access-" + suffix, Role.ROLE_GERENTE);
        User viewer = user("viewer-access-" + suffix, Role.ROLE_GERENTE);
        User billing = user("billing-access-" + suffix, Role.ROLE_GERENTE);
        String token = token(admin);
        JsonNode account = createAccount(token, owner, "access-" + suffix, 1);
        long id = account.path("id").asLong();
        BusinessAccount entity = accounts.findById(id).orElseThrow();
        member(entity, viewer, BusinessAccountRole.VIEWER);
        member(entity, billing, BusinessAccountRole.BILLING_MANAGER);

        mockMvc.perform(post("/platform/business-accounts/{id}/businesses/preview", id)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "viewer-preview-" + suffix)
                        .header("X-Correlation-Id", "corr-viewer-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewPayloadWithAccess(account.path("version").asLong(), "viewer-" + suffix,
                                viewer.getId(), "TENANT_ADMIN")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/platform/business-accounts/{id}/businesses/preview", id)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "billing-preview-" + suffix)
                        .header("X-Correlation-Id", "corr-billing-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewPayloadWithAccess(account.path("version").asLong(), "billing-" + suffix,
                                billing.getId(), "TENANT_FINANCE")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/platform/business-accounts")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "long-correlation-" + suffix)
                        .header("X-Correlation-Id", "x".repeat(121))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Long corr\",\"slug\":\"long-corr-" + suffix
                                + "\",\"responsavelPrincipal\":{\"strategy\":\"ASSOCIATE_EXISTING\",\"userId\":"
                                + owner.getId() + ",\"confirmExistingUser\":true}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void normalizedNifIsUniqueUnderConcurrencyAndNeverReturns500() throws Exception {
        String suffix = suffix();
        User admin = user("platform-nif-" + suffix, Role.ROLE_ADMIN);
        User owner = user("owner-nif-" + suffix, Role.ROLE_GERENTE);
        String token = token(admin);
        long accountsBefore = accounts.count();
        long canonicalNifsBefore = canonicalNifs.count();
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> createAccountWithNif(token, owner, "nif-a-" + suffix,
                    "AO-123/" + suffix, "nif-key-a-" + suffix));
            Future<Integer> second = executor.submit(() -> createAccountWithNif(token, owner, "nif-b-" + suffix,
                    "ao 123 " + suffix, "nif-key-b-" + suffix));
            List<Integer> statuses = new ArrayList<>(List.of(first.get(), second.get()));
            Collections.sort(statuses);
            assertThat(statuses).containsExactly(201, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(accounts.count()).isEqualTo(accountsBefore + 1);
        assertThat(canonicalNifs.count()).isEqualTo(canonicalNifsBefore + 1);
    }

    @Test
    void canonicalCreationRejectsEquivalentFormattedLegacyNifWithoutOrphans() throws Exception {
        String suffix = suffix();
        User admin = user("platform-legacy-nif-" + suffix, Role.ROLE_ADMIN);
        User owner = user("owner-legacy-nif-" + suffix, Role.ROLE_GERENTE);
        String token = token(admin);
        BusinessAccount legacy = new BusinessAccount();
        legacy.setNome("Legacy NIF " + suffix);
        legacy.setSlug("legacy-nif-" + suffix);
        legacy.setNif("AO 987.654/" + suffix);
        legacy.setMaxTenants(1);
        legacy.setEstado(BusinessAccountEstado.RASCUNHO);
        legacy.setResponsavel(owner);
        accounts.saveAndFlush(legacy);
        long accountsBefore = accounts.count();
        long canonicalNifsBefore = canonicalNifs.count();

        mockMvc.perform(post("/platform/business-accounts")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "legacy-nif-conflict-" + suffix)
                        .header("X-Correlation-Id", "corr-legacy-nif-conflict-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Canonical conflict " + suffix
                                + "\",\"slug\":\"canonical-conflict-" + suffix
                                + "\",\"nif\":\"ao-987654-" + suffix
                                + "\",\"responsavelPrincipal\":{\"strategy\":\"ASSOCIATE_EXISTING\",\"userId\":"
                                + owner.getId() + ",\"confirmExistingUser\":true}}"))
                .andExpect(status().isConflict());

        assertThat(accounts.count()).isEqualTo(accountsBefore);
        assertThat(canonicalNifs.count()).isEqualTo(canonicalNifsBefore);
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

    private PreparedLifecycle prepareDraftBusiness(String token, User owner, String suffix) throws Exception {
        JsonNode account = createAccount(token, owner, suffix, 1);
        long accountId = account.path("id").asLong();
        long accountVersion = account.path("version").asLong();
        JsonNode preview = preview(token, accountId, accountVersion, suffix, "preview-" + suffix);
        String operation = mockMvc.perform(post("/platform/business-accounts/{id}/businesses/provision", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "provision-" + suffix)
                        .header("X-Correlation-Id", "corr-provision-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(provisionPayload(preview, accountVersion, true)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long tenantId = objectMapper.readTree(operation).at("/data/tenantId").asLong();
        assertThat(tenants.findById(tenantId).orElseThrow().getEstado()).isEqualTo(TenantEstado.RASCUNHO);
        return new PreparedLifecycle(accountId, accountVersion, tenantId);
    }

    private long provisionDraftBusiness(String token, long accountId, long accountVersion,
                                        String suffix, String keyPrefix) throws Exception {
        JsonNode preview = preview(token, accountId, accountVersion, suffix, keyPrefix + "preview" + suffix);
        String response = mockMvc.perform(post("/platform/business-accounts/{id}/businesses/provision", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", keyPrefix + "provision" + suffix)
                        .header("X-Correlation-Id", "corr-" + keyPrefix + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(provisionPayload(preview, accountVersion, true)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/tenantId").asLong();
    }

    private String selectTenantToken(String globalToken, long tenantId) throws Exception {
        String response = mockMvc.perform(post("/auth/tenant/select")
                        .header("Authorization", "Bearer " + globalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":" + tenantId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/accessToken").asText();
    }

    private JsonNode activateAccount(String token, long accountId, long version, String key, String reason) throws Exception {
        String response = mockMvc.perform(post("/platform/business-accounts/{id}/activate", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .header("X-Correlation-Id", "corr-" + key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountVersion\":" + version + ",\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private int accountActivationStatus(String token, long accountId, long version, String key, String reason) throws Exception {
        return mockMvc.perform(post("/platform/business-accounts/{id}/activate", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .header("X-Correlation-Id", "corr-" + key + "-" + Thread.currentThread().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountVersion\":" + version + ",\"reason\":\"" + reason + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    private int replaceOwnerStatus(String platformToken, long accountId, long accountVersion,
                                   User owner, String key) throws Exception {
        String payload = "{\"accountVersion\":" + accountVersion + ",\"novoResponsavel\":{"
                + "\"strategy\":\"ASSOCIATE_EXISTING\",\"userId\":" + owner.getId()
                + ",\"confirmExistingUser\":true},\"reason\":\"substituição concorrente\"}";
        return mockMvc.perform(post("/platform/business-accounts/{id}/owner/replace", accountId)
                        .header("Authorization", "Bearer " + platformToken)
                        .header("Idempotency-Key", key)
                        .header("X-Correlation-Id", "corr-" + key)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andReturn().getResponse().getStatus();
    }

    private String businessActivation(long accountVersion, long tenantVersion, String reason) {
        return "{\"accountVersion\":" + accountVersion + ",\"tenantVersion\":" + tenantVersion
                + ",\"reason\":\"" + reason + "\"}";
    }

    private record PreparedLifecycle(long accountId, long accountVersion, long tenantId) {}

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

    private JsonNode preview(String token, long accountId, long version, String suffix, String key) throws Exception {
        String body = mockMvc.perform(post("/platform/business-accounts/{id}/businesses/preview", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .header("X-Correlation-Id", "corr-" + key)
                        .contentType(MediaType.APPLICATION_JSON).content(previewPayload(version, suffix, "PILOTO")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private JsonNode previewRest(String token, long accountId, long version, String suffix) throws Exception {
        String payload = """
                {
                  "accountVersion": %d,
                  "planoCodigo": "PILOTO",
                  "vertical": "CONSUMA_REST",
                  "negocio": {
                    "nomeNegocio": "Rest %s",
                    "slug": "business-%s",
                    "tenantCode": "R%s",
                    "tipo": "RESTAURANTE",
                    "nif": "NIF-%s",
                    "telefone": "+24492%s"
                  },
                  "rest": {
                    "temMesas": true,
                    "quantidadeMesas": 2,
                    "gerarQrPorMesa": true,
                    "temBarSeparado": false,
                    "exigeTurnoAberto": true,
                    "entrega": "NONE"
                  },
                  "acessos": {"strategy": "ACCOUNT_OWNER_AS_TENANT_OWNER", "additionalAccesses": []}
                }
                """.formatted(version, suffix, suffix, digits(suffix, 8), suffix, digits(suffix, 9));
        String response = mockMvc.perform(post("/platform/business-accounts/{id}/businesses/preview", accountId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "rest-preview-" + suffix)
                        .header("X-Correlation-Id", "corr-rest-preview-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private void member(BusinessAccount account, User user, BusinessAccountRole role) {
        var member = new com.restaurante.model.entity.BusinessAccountMember();
        member.setBusinessAccount(account);
        member.setUser(user);
        member.setRole(role);
        member.setEstado(BusinessAccountMemberEstado.ATIVO);
        members.saveAndFlush(member);
    }

    private String previewPayloadWithAccess(long version, String suffix, long userId, String tenantRole) {
        return previewPayload(version, suffix, "PILOTO").replace("\"additionalAccesses\": []",
                "\"additionalAccesses\": [{\"userId\":" + userId + ",\"tenantRole\":\"" + tenantRole + "\"}]");
    }

    private int createAccountWithNif(String token, User owner, String suffix, String nif, String key) throws Exception {
        return mockMvc.perform(post("/platform/business-accounts")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .header("X-Correlation-Id", "corr-" + key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Conta " + suffix + "\",\"slug\":\"account-" + suffix
                                + "\",\"nif\":\"" + nif + "\",\"responsavelPrincipal\":{\"strategy\":\"ASSOCIATE_EXISTING\","
                                + "\"userId\":" + owner.getId() + ",\"confirmExistingUser\":true}}"))
                .andReturn().getResponse().getStatus();
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

    private String userToken(User user) {
        return jwtTokenProvider.generateToken(user.getUsername(), "ROLE_GERENTE", null, user.getId(), "GLOBAL");
    }

    private String suffix() { return Long.toString(Math.abs(System.nanoTime() % 100_000_000L)); }
    private static String digits(String value, int length) {
        String digits = Integer.toUnsignedString(value.hashCode()).replaceAll("\\D", "");
        return (digits + "0000000000000000").substring(0, length);
    }
}
