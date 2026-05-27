package com.restaurante.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.device.offline.repository.DeviceOfflineCommandReplayAttemptRepository;
import com.restaurante.device.offline.repository.DeviceOfflineCommandRepository;
import com.restaurante.dto.request.DeviceOfflineCommandRequest;
import com.restaurante.dto.request.DeviceOfflineSyncBatchRequest;
import com.restaurante.dto.request.OfflineCommandReplayPreviewRequest;
import com.restaurante.dto.request.OfflineCommandReplayRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DeviceOfflineCommandStatus;
import com.restaurante.model.enums.DeviceOfflineCommandType;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.OperationalDeviceType;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.device.DevicePrincipal;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class TenantOfflineSyncReplayControllerIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantRepository tenantRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired DeviceOfflineCommandRepository commandRepository;
    @Autowired DeviceOfflineCommandReplayAttemptRepository attemptRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void owner_can_preview_and_replay_retryable_conflict_and_export_is_sanitized() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("offline-replay", "RPL");
        Produto prod = criarProdutoBasico(prov.getTenantId(), false); // inativo => conflito
        DispositivoOperacional disp = criarDevicePos(prov, OperationalDeviceType.POS_CAIXA);

        UsernamePasswordAuthenticationToken deviceAuth = deviceAuth(prov, disp);

        DeviceOfflineSyncBatchRequest batch = new DeviceOfflineSyncBatchRequest();
        batch.setSyncSessionId("sess-replay-1");
        DeviceOfflineCommandRequest cmd = new DeviceOfflineCommandRequest();
        cmd.setClientRequestId("cmd-1");
        cmd.setCommandType(DeviceOfflineCommandType.CREATE_PEDIDO_POS);
        cmd.setCommandVersion("1");
        cmd.setLocalCreatedAt(Instant.now());
        cmd.setPayload(objectMapper.readTree("""
                {"itens":[{"produtoId": %d, "quantidade": 1}], "localTotalEstimado": 10.00}
                """.formatted(prod.getId())));
        batch.setCommands(List.of(cmd));

        String syncResp = mockMvc.perform(post("/device/offline-sync/batch")
                        .with(authentication(deviceAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode sync = objectMapper.readTree(syncResp).at("/data");
        String serverSyncId = sync.get("serverSyncId").asText();
        assertThat(sync.get("conflicts").asInt()).isEqualTo(1);

        // ativa produto e reprocessa
        prod.setAtivo(true);
        produtoRepository.saveAndFlush(prod);

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        OfflineCommandReplayPreviewRequest prevReq = new OfflineCommandReplayPreviewRequest();
        prevReq.setStatuses(List.of(DeviceOfflineCommandStatus.CONFLICT));
        prevReq.setOnlyEligible(true);
        String preview = mockMvc.perform(post("/tenant/offline-sync/sessions/{id}/replay/preview", serverSyncId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prevReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(preview).contains("eligibleCount");

        OfflineCommandReplayRequest replayReq = new OfflineCommandReplayRequest();
        replayReq.setStatuses(List.of(DeviceOfflineCommandStatus.CONFLICT));
        replayReq.setReason("Support replay");
        replayReq.setDryRun(false);
        replayReq.setForce(false);
        String replay = mockMvc.perform(post("/tenant/offline-sync/sessions/{id}/replay", serverSyncId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(replayReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode rep = objectMapper.readTree(replay).at("/data");
        assertThat(rep.get("succeeded").asInt()).isGreaterThanOrEqualTo(1);

        // comando original vira APPLIED e attempt é criado
        var saved = commandRepository.findByTenantIdAndDispositivoOperacionalIdAndClientRequestId(prov.getTenantId(), disp.getId(), "cmd-1").orElseThrow();
        assertThat(saved.getStatus().name()).isEqualTo("APPLIED");
        assertThat(saved.getReplayCount()).isGreaterThanOrEqualTo(1);
        assertThat(saved.getLastReplayAttempt()).isNotNull();
        assertThat(attemptRepository.findByTenantAndCommand(prov.getTenantId(), saved.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements())
                .isGreaterThanOrEqualTo(1);

        String export = mockMvc.perform(get("/tenant/offline-sync/sessions/{id}/diagnostic-export", serverSyncId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(export).doesNotContain("payload_json");
        assertThat(export).doesNotContain("payloadJson");
        assertThat(export).contains("payloadHash");
    }

    @Test
    void finance_cannot_execute_replay() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("offline-replay-fin", "RPF");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_FINANCE.name()),
                TenantResolutionSource.JWT, false, false
        ));
        OfflineCommandReplayRequest req = new OfflineCommandReplayRequest();
        req.setStatuses(List.of(DeviceOfflineCommandStatus.CONFLICT));
        req.setReason("x");
        mockMvc.perform(post("/tenant/offline-sync/sessions/{id}/replay", "does-not-exist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    private UsernamePasswordAuthenticationToken deviceAuth(ProvisionarTenantResponse prov, DispositivoOperacional disp) {
        DevicePrincipal device = new DevicePrincipal(
                disp.getId(), disp.getCodigo(),
                prov.getTenantId(), prov.getTenantCode(),
                prov.getInstituicaoId(), prov.getUnidadeAtendimentoId(), null,
                DispositivoTipo.POS, DispositivoStatus.ATIVO,
                List.of(DeviceCapability.OFFLINE_SYNC, DeviceCapability.OFFLINE_CREATE_ORDER),
                1
        );
        return new UsernamePasswordAuthenticationToken(device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE")));
    }

    private Produto criarProdutoBasico(Long tenantId, boolean ativo) {
        Tenant t = tenantRepository.findById(tenantId).orElseThrow();
        CategoriaProduto cat = new CategoriaProduto();
        cat.setTenant(t);
        cat.setNome("Geral");
        cat.setSlug("geral-" + (System.nanoTime() % 100_000));
        cat.setAtivo(true);
        cat = categoriaProdutoRepository.save(cat);

        Produto prod = Produto.builder()
                .codigo("P-" + (System.nanoTime() % 1_000_000))
                .nome("Produto Replay")
                .preco(new BigDecimal("10.00"))
                .categoria(CategoriaProdutoLegacy.OUTROS)
                .ativo(ativo)
                .disponivel(true)
                .build();
        prod.setTenant(t);
        prod.setCategoriaProduto(cat);
        return produtoRepository.saveAndFlush(prod);
    }

    private DispositivoOperacional criarDevicePos(ProvisionarTenantResponse prov, OperationalDeviceType opType) {
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        var inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        var ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();
        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setCodigo("DEV-OFF-" + System.nanoTime());
        d.setNome("POS Offline");
        d.setTipo(DispositivoTipo.POS);
        d.setOperationalDeviceType(opType);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setTokenVersion(1);
        return dispositivoOperacionalRepository.saveAndFlush(d);
    }

    private ProvisionarTenantResponse provisionTenant(String slug, String code) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of(Role.ROLE_ADMIN.name()),
                TenantResolutionSource.JWT, true, false
        ));
        String phone = "+244900" + Math.abs(slug.hashCode() % 1_000_000);
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + slug)
                                .slug(slug)
                                .tenantCode(code)
                                .tipo(TenantTipo.VENDEDOR_RUA)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("VENDEDOR_RUA")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + slug)
                                .sigla(code)
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email(slug + "@owner.com")
                                .telefone(phone)
                                .criarUsuario(true)
                                .build())
                        .build()
        );
    }
}
