package com.restaurante.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.BusinessAccount;
import com.restaurante.model.entity.BusinessProvisioningOperation;
import com.restaurante.model.entity.BusinessProvisioningPreview;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.BusinessAccountEstado;
import com.restaurante.model.enums.BusinessAccountMemberEstado;
import com.restaurante.model.enums.BusinessAccountRole;
import com.restaurante.model.enums.OnboardingNifReservationState;
import com.restaurante.model.enums.OnboardingRequestStatus;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.repository.BusinessAccountMemberRepository;
import com.restaurante.repository.BusinessAccountRepository;
import com.restaurante.repository.BusinessProvisioningOperationRepository;
import com.restaurante.repository.BusinessProvisioningPreviewRepository;
import com.restaurante.repository.OnboardingCommandRecordRepository;
import com.restaurante.repository.OnboardingNifReservationRepository;
import com.restaurante.repository.OnboardingRequestRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.JwtTokenProvider;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.main.web-application-type=servlet",
        "jwt.secret=0123456789ABCDEF0123456789ABCDEF",
        "jwt.expiration=3600000"
})
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("it-postgres")
class PlatformOnboardingCanonicalContractIT extends PostgresTestcontainersConfig {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider jwt;
    @Autowired UserRepository users;
    @Autowired BusinessAccountRepository accounts;
    @Autowired BusinessAccountMemberRepository members;
    @Autowired OnboardingRequestRepository onboardings;
    @Autowired OnboardingNifReservationRepository reservations;
    @Autowired OnboardingCommandRecordRepository commands;
    @Autowired BusinessProvisioningPreviewRepository previews;
    @Autowired BusinessProvisioningOperationRepository operations;
    @Autowired TenantRepository tenants;

    @Test
    void createIsPersistentIdempotentValidatedSecuredAndCorsCompatible() throws Exception {
        String suffix = suffix();
        User admin = user("admin-create-" + suffix, Role.ROLE_ADMIN);
        User manager = user("manager-create-" + suffix, Role.ROLE_GERENTE);
        String token = token(admin);
        String payload = createPayload("Create " + suffix, "12.345/" + suffix, "10.00", "aoa");
        long before = onboardings.count();

        String firstBody = create(token, "create-idem-" + suffix, payload, 201);
        JsonNode first = json.readTree(firstBody).path("data");
        assertThat(first.path("status").asText()).isEqualTo("PENDENTE");
        assertThat(first.path("version").isNumber()).isTrue();
        assertThat(first.path("contractVersion").asText()).isEqualTo("ONBOARDING_CANONICAL_V2");
        assertThat(first.path("normalizedNif").asText()).isEqualTo(("12345" + suffix).toUpperCase());
        assertThat(first.path("statusPagamento").asText()).isEqualTo("PENDENTE");
        assertThat(first.path("moeda").asText()).isEqualTo("AOA");
        assertThat(first.path("businessAccountId").isNull()).isTrue();
        assertThat(first.path("tenantId").isNull()).isTrue();
        assertThat(first.path("provisioningOperationId").isNull()).isTrue();

        JsonNode replay = json.readTree(create(token, "create-idem-" + suffix, payload, 201)).path("data");
        assertThat(replay.path("id").asLong()).isEqualTo(first.path("id").asLong());
        assertThat(replay.path("version").asLong()).isEqualTo(first.path("version").asLong());
        assertThat(onboardings.count()).isEqualTo(before + 1);
        assertThat(commandCount(first.path("id").asLong(), "ONBOARDING_CREATED")).isEqualTo(1);

        create(token, "create-idem-" + suffix,
                createPayload("Different " + suffix, "12.345/" + suffix, "10.00", "AOA"), 409);
        create(token, "negative-" + suffix,
                createPayload("Negative " + suffix, null, "-0.01", "AOA"), 400);
        create(token, "currency-" + suffix,
                createPayload("Currency " + suffix, null, "0", "A0A"), 400);

        mockMvc.perform(post("/platform/onboarding-requests").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/platform/onboarding-requests")
                        .header("Authorization", "Bearer " + token(manager))
                        .header("Idempotency-Key", "forbidden-" + suffix)
                        .header("X-Correlation-Id", "corr-forbidden-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/platform/onboarding-requests/{id}", Long.MAX_VALUE)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(options("/platform/onboarding-requests/1/complete")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers",
                                "authorization,content-type,idempotency-key,x-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsStringIgnoringCase("idempotency-key")));
    }

    @Test
    void concurrentCreateReservesNewAccountNifButExistingAccountBecomesExplicitCandidate() throws Exception {
        String suffix = suffix();
        User admin = user("admin-nif-" + suffix, Role.ROLE_ADMIN);
        User owner = user("owner-nif-" + suffix, Role.ROLE_GERENTE);
        String token = token(admin);
        String nif = "NIF-RACE-" + suffix;
        var executor = Executors.newFixedThreadPool(2);
        List<Integer> statuses = new ArrayList<>();
        try {
            Future<Integer> first = executor.submit(() -> createStatus(token, "nif-a-" + suffix,
                    createPayload("Nif A " + suffix, nif, null, null)));
            Future<Integer> second = executor.submit(() -> createStatus(token, "nif-b-" + suffix,
                    createPayload("Nif B " + suffix, nif, null, null)));
            statuses.add(first.get());
            statuses.add(second.get());
        } finally {
            executor.shutdownNow();
        }
        Collections.sort(statuses);
        assertThat(statuses).containsExactly(201, 409);
        assertThat(reservations.findByNormalizedNifAndState(normalizeNif(nif),
                OnboardingNifReservationState.ACTIVE)).isPresent();
        commandRaw(token, "/platform/business-accounts", "reserved-direct-account-" + suffix,
                "{\"nome\":\"Reserved direct\",\"slug\":\"reserved-direct-" + suffix
                        + "\",\"nif\":\"" + nif + "\",\"responsavelPrincipal\":{"
                        + "\"strategy\":\"ASSOCIATE_EXISTING\",\"userId\":" + owner.getId()
                        + ",\"confirmExistingUser\":true}}", 409);

        String existingNif = "NIF-EXIST-" + suffix;
        JsonNode account = createAccount(token, owner, "existing-" + suffix, existingNif);
        JsonNode onboarding = json.readTree(create(token, "candidate-" + suffix,
                createPayload("Candidate " + suffix, existingNif.toLowerCase(), null, null), 201)).path("data");
        assertThat(onboarding.path("nifResolution").asText()).isEqualTo("EXISTING_ACCOUNT_CANDIDATE");
        assertThat(onboarding.at("/businessAccountCandidate/id").asLong()).isEqualTo(account.path("id").asLong());
        assertThat(onboarding.path("businessAccountId").isNull()).isTrue();

        approveCreate(token, onboarding, owner, "candidate-new-" + suffix, 409);
        JsonNode wrong = createAccount(token, owner, "wrong-" + suffix, null);
        approveExisting(token, onboarding, wrong, "candidate-wrong-" + suffix, 409);
        JsonNode approved = approveExisting(token, onboarding, account, "candidate-right-" + suffix, 200);
        assertThat(approved.path("status").asText()).isEqualTo("APROVADO");
        assertThat(approved.path("businessAccountId").asLong()).isEqualTo(account.path("id").asLong());
        assertThat(accounts.findById(account.path("id").asLong()).orElseThrow().getEstado())
                .isEqualTo(BusinessAccountEstado.RASCUNHO);
    }

    @Test
    void approvalSupportsBothOwnerStrategiesAndReplaysBeforeStaleVersion() throws Exception {
        String suffix = suffix();
        User admin = user("admin-owner-" + suffix, Role.ROLE_ADMIN);
        User owner = user("owner-associate-" + suffix, Role.ROLE_GERENTE);
        String token = token(admin);
        JsonNode onboarding = createData(token, "associate-create-" + suffix,
                createPayload("Associate " + suffix, null, "25.00", "AOA"));
        String approval = approveCreatePayload(onboarding.path("version").asLong(), owner.getId(), suffix);
        JsonNode approved = command(token, "/platform/onboarding-requests/" + onboarding.path("id").asLong()
                + "/approve", "associate-approve-" + suffix, approval, 200);
        assertApprovedWithoutOperationalEffects(approved);
        assertThat(approved.path("statusPagamento").asText()).isEqualTo("PENDENTE");
        long accountId = approved.path("businessAccountId").asLong();
        assertThat(members.countByBusinessAccountIdAndRoleAndEstado(accountId,
                BusinessAccountRole.OWNER, BusinessAccountMemberEstado.ATIVO)).isEqualTo(1);

        JsonNode replay = command(token, "/platform/onboarding-requests/" + onboarding.path("id").asLong()
                + "/approve", "associate-approve-" + suffix, approval, 200);
        assertThat(replay.path("version").asLong()).isEqualTo(approved.path("version").asLong());
        assertThat(commandCount(onboarding.path("id").asLong(), "ONBOARDING_APPROVED")).isEqualTo(1);
        command(token, "/platform/onboarding-requests/" + onboarding.path("id").asLong() + "/approve",
                "associate-approve-" + suffix, approval.replace("CONSUMA_PONTO", "CONSUMA_REST"), 409);

        JsonNode createOwnerRequest = createData(token, "new-owner-create-" + suffix,
                createPayload("New Owner " + suffix, null, "0", null));
        String newOwnerPayload = """
                {"onboardingVersion":%d,"accountChoice":"CREATE_NEW","businessAccountSlug":"new-owner-%s",
                 "ownerChoice":{"strategy":"CREATE_NEW","username":"new-owner-%s@example.test",
                 "temporaryPassword":"Temporary#12345","nome":"New Owner","email":"new-owner-%s@example.test",
                 "telefone":"+24493%s"},"confirmedPlanCode":"PILOTO","vertical":"CONSUMA_REST",
                 "reason":"owner criado explicitamente"}
                """.formatted(createOwnerRequest.path("version").asLong(), suffix, suffix, suffix, digits(suffix, 7));
        String newOwnerRaw = commandRaw(token, "/platform/onboarding-requests/"
                + createOwnerRequest.path("id").asLong() + "/approve", "new-owner-approve-" + suffix,
                newOwnerPayload, 200);
        JsonNode newOwnerApproved = json.readTree(newOwnerRaw).path("data");
        assertThat(newOwnerApproved.path("ownerStrategy").asText()).isEqualTo("CREATE_NEW");
        assertThat(newOwnerApproved.path("ownerResultUserId").asLong()).isPositive();
        assertThat(newOwnerRaw).doesNotContain("Temporary#12345");
        assertApprovedWithoutOperationalEffects(newOwnerApproved);

        JsonNode invalidPassword = createData(token, "invalid-password-create-" + suffix,
                createPayload("Invalid Password " + suffix, null, null, null));
        command(token, "/platform/onboarding-requests/" + invalidPassword.path("id").asLong() + "/approve",
                "invalid-password-" + suffix,
                newOwnerPayload.replace(String.valueOf(createOwnerRequest.path("version").asLong()),
                                String.valueOf(invalidPassword.path("version").asLong()))
                        .replace("new-owner-" + suffix, "short-owner-" + suffix)
                        .replace("Temporary#12345", "short"), 400);
    }

    @Test
    void approveRejectAndApproveCancelRacesProduceOneDecision() throws Exception {
        String suffix = suffix();
        User admin = user("admin-races-" + suffix, Role.ROLE_ADMIN);
        User owner = user("owner-races-" + suffix, Role.ROLE_GERENTE);
        String token = token(admin);
        JsonNode rejectRace = createData(token, "reject-race-create-" + suffix,
                createPayload("Reject Race " + suffix, null, null, null));
        List<Integer> approveReject = race(
                () -> commandStatus(token, path(rejectRace, "approve"), "race-approve-" + suffix,
                        approveCreatePayload(rejectRace.path("version").asLong(), owner.getId(), "ar-" + suffix)),
                () -> commandStatus(token, path(rejectRace, "reject"), "race-reject-" + suffix,
                        decisionPayload(rejectRace.path("version").asLong(), "rejeição concorrente")));
        assertThat(approveReject).containsExactly(200, 409);
        var rejectedResult = onboardings.findById(rejectRace.path("id").asLong()).orElseThrow();
        assertThat(rejectedResult.getStatus()).isIn(OnboardingRequestStatus.APROVADO, OnboardingRequestStatus.REJEITADO);
        if (rejectedResult.getStatus() == OnboardingRequestStatus.REJEITADO) {
            assertThat(rejectedResult.getBusinessAccount()).isNull();
        }

        JsonNode cancelRace = createData(token, "cancel-race-create-" + suffix,
                createPayload("Cancel Race " + suffix, null, null, null));
        List<Integer> approveCancel = race(
                () -> commandStatus(token, path(cancelRace, "approve"), "race-approve-c-" + suffix,
                        approveCreatePayload(cancelRace.path("version").asLong(), owner.getId(), "ac-" + suffix)),
                () -> commandStatus(token, path(cancelRace, "cancel"), "race-cancel-" + suffix,
                        decisionPayload(cancelRace.path("version").asLong(), "cancelamento concorrente")));
        assertThat(approveCancel).containsExactly(200, 409);
        assertThat(onboardings.findById(cancelRace.path("id").asLong()).orElseThrow().getStatus())
                .isIn(OnboardingRequestStatus.APROVADO, OnboardingRequestStatus.CANCELADO);
    }

    @Test
    void rejectAndCancelAreVersionedStateGuardedAndPersistentlyIdempotent() throws Exception {
        String suffix = suffix();
        User admin = user("admin-decisions-" + suffix, Role.ROLE_ADMIN);
        String token = token(admin);
        JsonNode rejected = createData(token, "reject-create-" + suffix,
                createPayload("Reject " + suffix, "NIF-REJECT-" + suffix, null, null));
        String rejectPayload = decisionPayload(rejected.path("version").asLong(), "documentação incompleta");
        JsonNode rejectResult = command(token, path(rejected, "reject"), "reject-" + suffix, rejectPayload, 200);
        assertThat(rejectResult.path("status").asText()).isEqualTo("REJEITADO");
        assertThat(command(token, path(rejected, "reject"), "reject-" + suffix, rejectPayload, 200)
                .path("version").asLong()).isEqualTo(rejectResult.path("version").asLong());
        assertThat(reservations.findByNormalizedNifAndState(normalizeNif("NIF-REJECT-" + suffix),
                OnboardingNifReservationState.RELEASED)).isPresent();
        command(token, path(rejected, "cancel"), "cancel-rejected-" + suffix,
                decisionPayload(rejectResult.path("version").asLong(), "não permitido"), 409);

        JsonNode cancelled = createData(token, "cancel-create-" + suffix,
                createPayload("Cancel " + suffix, null, null, null));
        String cancelPayload = decisionPayload(cancelled.path("version").asLong(), "cliente desistiu");
        JsonNode cancelResult = command(token, path(cancelled, "cancel"), "cancel-" + suffix, cancelPayload, 200);
        assertThat(cancelResult.path("status").asText()).isEqualTo("CANCELADO");
        assertThat(command(token, path(cancelled, "cancel"), "cancel-" + suffix, cancelPayload, 200)
                .path("version").asLong()).isEqualTo(cancelResult.path("version").asLong());
        command(token, path(cancelled, "reject"), "reject-cancelled-" + suffix,
                decisionPayload(cancelResult.path("version").asLong(), "não permitido"), 409);
    }

    @Test
    void handoffUsesCanonicalProvisioningAndCompleteNeverActivatesAccountOrTenant() throws Exception {
        String suffix = suffix();
        User admin = user("admin-complete-" + suffix, Role.ROLE_ADMIN);
        User owner = user("owner-complete-" + suffix, Role.ROLE_GERENTE);
        String token = token(admin);
        JsonNode created = createData(token, "complete-create-" + suffix,
                createPayload("Complete " + suffix, null, "0", null));
        JsonNode approved = command(token, path(created, "approve"), "complete-approve-" + suffix,
                approveCreatePayload(created.path("version").asLong(), owner.getId(), "complete-" + suffix), 200);
        long onboardingId = created.path("id").asLong();
        long accountId = approved.path("businessAccountId").asLong();
        long accountVersion = approved.path("businessAccountVersion").asLong();

        long previewCount = previews.count();
        long operationCount = operations.count();
        long onboardingVersionBeforeHandoff = approved.path("version").asLong();
        String handoffRaw = mockMvc.perform(get("/platform/onboarding-requests/{id}/handoff", onboardingId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode handoff = json.readTree(handoffRaw).path("data");
        assertThat(handoff.path("businessAccountId").asLong()).isEqualTo(accountId);
        assertThat(handoff.path("confirmedPlanCode").asText()).isEqualTo("PILOTO");
        assertThat(handoff.path("vertical").asText()).isEqualTo("CONSUMA_PONTO");
        assertThat(handoff.at("/fieldMatrix/negocio.slug").asText()).isEqualTo("MISSING");
        assertThat(previews.count()).isEqualTo(previewCount);
        assertThat(operations.count()).isEqualTo(operationCount);
        assertThat(onboardings.findById(onboardingId).orElseThrow().getVersion())
                .isEqualTo(onboardingVersionBeforeHandoff);

        String previewPayload = previewPayload(accountVersion, suffix, "PILOTO", "CONSUMA_PONTO");
        String previewRaw = commandRaw(token, "/platform/business-accounts/" + accountId + "/businesses/preview",
                "preview-" + suffix, previewPayload, 200);
        JsonNode preview = json.readTree(previewRaw).path("data");
        String provisionPayload = "{\"previewId\":\"" + preview.path("previewId").asText()
                + "\",\"requestFingerprint\":\"" + preview.path("requestFingerprint").asText()
                + "\",\"accountVersion\":" + accountVersion + ",\"confirmed\":true}";
        JsonNode operationResult = json.readTree(commandRaw(token,
                "/platform/business-accounts/" + accountId + "/businesses/provision",
                "provision-" + suffix, provisionPayload, 201)).path("data");
        String operationId = operationResult.path("operationId").asText();
        long tenantId = operationResult.path("tenantId").asLong();

        JsonNode other = createData(token, "other-create-" + suffix,
                createPayload("Other " + suffix, null, null, null));
        JsonNode otherApproved = command(token, path(other, "approve"), "other-approve-" + suffix,
                approveCreatePayload(other.path("version").asLong(), owner.getId(), "other-" + suffix), 200);
        command(token, path(other, "provisioning-operation"), "wrong-account-link-" + suffix,
                linkPayload(otherApproved.path("version").asLong(), operationId), 409);

        String linkPayload = linkPayload(approved.path("version").asLong(), operationId);
        JsonNode linked = command(token, path(created, "provisioning-operation"), "link-" + suffix,
                linkPayload, 200);
        JsonNode linkReplay = command(token, path(created, "provisioning-operation"), "link-" + suffix,
                linkPayload, 200);
        assertThat(linkReplay.path("version").asLong()).isEqualTo(linked.path("version").asLong());
        assertThat(linked.path("provisioningOperationId").asText()).isEqualTo(operationId);
        assertThat(commandCount(onboardingId, "ONBOARDING_OPERATION_LINKED")).isEqualTo(1);
        mockMvc.perform(get("/platform/provisioning-operations/{operationId}", operationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        command(token, path(created, "cancel"), "cancel-linked-" + suffix,
                decisionPayload(linked.path("version").asLong(), "não compensar operação"), 409);

        BusinessProvisioningOperation operation = operations.findByOperationId(operationId).orElseThrow();
        String completePayload = completePayload(linked.path("version").asLong(), operationId);
        operation.setStatus("RUNNING");
        operation = operations.saveAndFlush(operation);
        command(token, path(created, "complete"), "complete-running-" + suffix, completePayload, 409);
        operation = operations.findByOperationId(operationId).orElseThrow();
        operation.setStatus("SUCCEEDED");
        operation.setEffectsCommitted(false);
        operation = operations.saveAndFlush(operation);
        command(token, path(created, "complete"), "complete-effects-" + suffix, completePayload, 409);
        operation = operations.findByOperationId(operationId).orElseThrow();
        operation.setEffectsCommitted(true);
        operations.saveAndFlush(operation);

        BusinessProvisioningPreview previewEntity = previews.findByPreviewId(preview.path("previewId").asText()).orElseThrow();
        String originalPayload = previewEntity.getPayloadJson();
        previewEntity.setPayloadJson(originalPayload.replace("\"PILOTO\"", "\"OTHER_PLAN\""));
        previewEntity = previews.saveAndFlush(previewEntity);
        command(token, path(created, "complete"), "complete-plan-" + suffix, completePayload, 409);
        previewEntity = previews.findByPreviewId(preview.path("previewId").asText()).orElseThrow();
        previewEntity.setPayloadJson(originalPayload.replace("CONSUMA_PONTO", "CONSUMA_REST"));
        previewEntity = previews.saveAndFlush(previewEntity);
        command(token, path(created, "complete"), "complete-vertical-" + suffix, completePayload, 409);
        previewEntity = previews.findByPreviewId(preview.path("previewId").asText()).orElseThrow();
        previewEntity.setPayloadJson(originalPayload);
        previews.saveAndFlush(previewEntity);

        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        tenant.setEstado(TenantEstado.ATIVO);
        tenant = tenants.saveAndFlush(tenant);
        command(token, path(created, "complete"), "complete-active-tenant-" + suffix, completePayload, 409);
        tenant = tenants.findById(tenantId).orElseThrow();
        tenant.setEstado(TenantEstado.RASCUNHO);
        tenants.saveAndFlush(tenant);

        long accountVersionBeforeComplete = accounts.findById(accountId).orElseThrow().getVersion();
        long tenantVersionBeforeComplete = tenants.findById(tenantId).orElseThrow().getVersion();
        JsonNode completed = command(token, path(created, "complete"), "complete-" + suffix,
                completePayload, 200);
        assertThat(completed.path("status").asText()).isEqualTo("CONCLUIDO");
        assertThat(completed.path("tenantId").asLong()).isEqualTo(tenantId);
        assertThat(completed.path("tenantEstado").asText()).isEqualTo("RASCUNHO");
        assertThat(completed.path("businessAccountEstado").asText()).isEqualTo("RASCUNHO");
        assertThat(accounts.findById(accountId).orElseThrow().getVersion()).isEqualTo(accountVersionBeforeComplete);
        assertThat(tenants.findById(tenantId).orElseThrow().getVersion()).isEqualTo(tenantVersionBeforeComplete);
        JsonNode completeReplay = command(token, path(created, "complete"), "complete-" + suffix,
                completePayload, 200);
        assertThat(completeReplay.path("version").asLong()).isEqualTo(completed.path("version").asLong());
        assertThat(commandCount(onboardingId, "ONBOARDING_COMPLETED")).isEqualTo(1);
        assertThat(commands.findAll().stream().noneMatch(c -> c.getAfterState() != null
                && c.getAfterState().contains("ATIVADO"))).isTrue();
    }

    private JsonNode createData(String token, String key, String payload) throws Exception {
        return json.readTree(create(token, key, payload, 201)).path("data");
    }

    private String create(String token, String key, String payload, int expected) throws Exception {
        return mockMvc.perform(post("/platform/onboarding-requests")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .header("X-Correlation-Id", "corr-" + key)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private int createStatus(String token, String key, String payload) throws Exception {
        return mockMvc.perform(post("/platform/onboarding-requests")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .header("X-Correlation-Id", "corr-" + key)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andReturn().getResponse().getStatus();
    }

    private JsonNode command(String token, String path, String key, String payload, int expected) throws Exception {
        return json.readTree(commandRaw(token, path, key, payload, expected)).path("data");
    }

    private String commandRaw(String token, String path, String key, String payload, int expected) throws Exception {
        return mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .header("X-Correlation-Id", "corr-" + key)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private int commandStatus(String token, String path, String key, String payload) throws Exception {
        return mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .header("X-Correlation-Id", "corr-" + key + "-" + Thread.currentThread().getId())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andReturn().getResponse().getStatus();
    }

    private JsonNode createAccount(String token, User owner, String suffix, String nif) throws Exception {
        String nifField = nif == null ? "" : ",\"nif\":\"" + nif + "\"";
        String body = commandRaw(token, "/platform/business-accounts", "account-" + suffix,
                "{\"nome\":\"Account " + suffix + "\",\"slug\":\"account-" + suffix + "\"" + nifField
                        + ",\"maxTenants\":2,\"responsavelPrincipal\":{\"strategy\":\"ASSOCIATE_EXISTING\","
                        + "\"userId\":" + owner.getId() + ",\"confirmExistingUser\":true}}", 201);
        return json.readTree(body).path("data");
    }

    private void approveCreate(String token, JsonNode onboarding, User owner, String key, int expected) throws Exception {
        command(token, path(onboarding, "approve"), key,
                approveCreatePayload(onboarding.path("version").asLong(), owner.getId(), key), expected);
    }

    private JsonNode approveExisting(String token, JsonNode onboarding, JsonNode account,
                                     String key, int expected) throws Exception {
        String payload = "{\"onboardingVersion\":" + onboarding.path("version").asLong()
                + ",\"accountChoice\":\"EXISTING\",\"businessAccountId\":" + account.path("id").asLong()
                + ",\"accountVersion\":" + account.path("version").asLong()
                + ",\"confirmExistingAccount\":true,\"confirmedPlanCode\":\"PILOTO\","
                + "\"vertical\":\"CONSUMA_REST\",\"reason\":\"conta existente confirmada\"}";
        return command(token, path(onboarding, "approve"), key, payload, expected);
    }

    private String approveCreatePayload(long version, long ownerId, String suffix) {
        return "{\"onboardingVersion\":" + version + ",\"accountChoice\":\"CREATE_NEW\","
                + "\"businessAccountSlug\":\"onboarding-" + suffix + "\",\"maxTenants\":2,"
                + "\"ownerChoice\":{\"strategy\":\"ASSOCIATE_EXISTING\",\"userId\":" + ownerId
                + ",\"confirmExistingUser\":true},\"confirmedPlanCode\":\"PILOTO\","
                + "\"vertical\":\"CONSUMA_PONTO\",\"reason\":\"aprovação administrativa\"}";
    }

    private String createPayload(String name, String nif, String value, String currency) {
        String nifField = nif == null ? "" : ",\"nif\":\"" + nif + "\"";
        String valueField = value == null ? "" : ",\"valor\":" + value;
        String currencyField = currency == null ? "" : ",\"moeda\":\"" + currency + "\"";
        return "{\"nomeSolicitante\":\"Applicant " + name + "\",\"telefone\":\"+24492"
                + digits(name, 7) + "\",\"email\":\"" + normalize(name) + "@example.test\","
                + "\"nomeNegocio\":\"" + name + "\",\"tipoNegocio\":\"RESTAURANTE\","
                + "\"planoCodigo\":\"PILOTO\"" + nifField + valueField + currencyField + "}";
    }

    private String previewPayload(long accountVersion, String suffix, String plan, String vertical) {
        return """
                {"accountVersion":%d,"planoCodigo":"%s","vertical":"%s",
                 "negocio":{"nomeNegocio":"Business %s","slug":"business-%s","tenantCode":"B%s",
                 "tipo":"VENDEDOR_RUA","nif":"BUS-%s","telefone":"+24492%s"},
                 "ponto":{"entregaManual":false,"allowPickup":true},
                 "acessos":{"strategy":"ACCOUNT_OWNER_AS_TENANT_OWNER","additionalAccesses":[]}}
                """.formatted(accountVersion, plan, vertical, suffix, suffix, digits(suffix, 8), suffix,
                digits(suffix, 7));
    }

    private String linkPayload(long version, String operationId) {
        return "{\"onboardingVersion\":" + version + ",\"operationId\":\"" + operationId
                + "\",\"reason\":\"handoff persistido\"}";
    }

    private String completePayload(long version, String operationId) {
        return "{\"onboardingVersion\":" + version + ",\"operationId\":\"" + operationId
                + "\",\"reason\":\"efeitos canónicos validados\"}";
    }

    private String decisionPayload(long version, String reason) {
        return "{\"onboardingVersion\":" + version + ",\"reason\":\"" + reason + "\"}";
    }

    private List<Integer> race(ThrowingIntSupplier first, ThrowingIntSupplier second) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = executor.submit(first::get);
            Future<Integer> b = executor.submit(second::get);
            List<Integer> values = new ArrayList<>(List.of(a.get(), b.get()));
            Collections.sort(values);
            return values;
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertApprovedWithoutOperationalEffects(JsonNode value) {
        assertThat(value.path("status").asText()).isEqualTo("APROVADO");
        assertThat(value.path("businessAccountEstado").asText()).isEqualTo("RASCUNHO");
        assertThat(value.path("tenantId").isNull()).isTrue();
        assertThat(value.path("provisioningOperationId").isNull()).isTrue();
    }

    private long commandCount(long onboardingId, String action) {
        return commands.findAll().stream()
                .filter(value -> value.getOnboardingRequest().getId().equals(onboardingId))
                .filter(value -> action.equals(value.getAction())).count();
    }

    private User user(String suffix, Role role) {
        User value = new User();
        value.setUsername(suffix + "@example.test");
        value.setPassword("x");
        value.setNomeCompleto("User " + suffix);
        value.setEmail(suffix + "@example.test");
        value.setTelefone("+24494" + digits(suffix, 7));
        value.setRoles(Set.of(role));
        value.setAtivo(true);
        return users.saveAndFlush(value);
    }

    private String token(User user) {
        String roles = user.getRoles().stream().map(Enum::name).sorted().reduce((a, b) -> a + "," + b).orElse("");
        return jwt.generateToken(user.getUsername(), roles, null, user.getId(), "GLOBAL");
    }

    private String path(JsonNode onboarding, String command) {
        return "/platform/onboarding-requests/" + onboarding.path("id").asLong() + "/" + command;
    }

    private String suffix() { return String.valueOf(Math.abs(System.nanoTime() % 100_000_000L)); }
    private String normalize(String value) { return value.toLowerCase().replaceAll("[^a-z0-9]", "-"); }
    private String normalizeNif(String value) { return value.toUpperCase().replaceAll("[\\s./-]", ""); }
    private String digits(String value, int length) {
        String digits = String.valueOf(Math.abs(value.hashCode()));
        return (digits + "00000000000000000000").substring(0, length);
    }

    @FunctionalInterface
    interface ThrowingIntSupplier { int get() throws Exception; }
}
