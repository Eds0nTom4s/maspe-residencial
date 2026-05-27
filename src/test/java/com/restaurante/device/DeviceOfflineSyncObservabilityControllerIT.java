package com.restaurante.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.DeviceOfflineCommandRequest;
import com.restaurante.dto.request.DeviceOfflineSyncBatchRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.DeviceCapability;
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
class DeviceOfflineSyncObservabilityControllerIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void tenant_can_list_detail_and_list_commands_for_offline_sync_sessions() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("offline-obs-ctrl", "OCT");
        Produto prod = criarProdutoBasico(prov.getTenantId(), true);
        DispositivoOperacional disp = criarDevicePos(prov, OperationalDeviceType.POS_CAIXA);

        UsernamePasswordAuthenticationToken auth = authFor(prov, disp);

        DeviceOfflineSyncBatchRequest batch = new DeviceOfflineSyncBatchRequest();
        batch.setSyncSessionId("s-ctrl-1");
        batch.setAppVersion("2.0.0");
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
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String serverSyncId = objectMapper.readTree(syncResp).at("/data/serverSyncId").asText();
        assertThat(serverSyncId).isNotBlank();

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        String list = mockMvc.perform(get("/tenant/offline-sync/sessions"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(list).contains(serverSyncId);

        String detail = mockMvc.perform(get("/tenant/offline-sync/sessions/{id}", serverSyncId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode d = objectMapper.readTree(detail).at("/data");
        assertThat(d.get("appVersion").asText()).isEqualTo("2.0.0");

        String cmds = mockMvc.perform(get("/tenant/offline-sync/sessions/{id}/commands", serverSyncId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(cmds).doesNotContain("payloadJson");
        assertThat(cmds).doesNotContain("payload_json");

        // cross-tenant: outro tenant não vê sessão
        ProvisionarTenantResponse prov2 = provisionTenant("offline-obs-ctrl-b", "OCB");
        TenantContextHolder.set(new TenantContext(
                prov2.getTenantId(), prov2.getTenantCode(), prov2.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));
        mockMvc.perform(get("/tenant/offline-sync/sessions/{id}", serverSyncId))
                .andExpect(status().isNotFound());
    }

    @Test
    void metrics_returns_top_conflict_codes() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("offline-obs-metrics", "MET");
        Produto prodOk = criarProdutoBasico(prov.getTenantId(), true);
        Produto prodInactive = criarProdutoBasico(prov.getTenantId(), false);
        DispositivoOperacional disp = criarDevicePos(prov, OperationalDeviceType.POS_CAIXA);
        UsernamePasswordAuthenticationToken auth = authFor(prov, disp);

        // session OK
        DeviceOfflineSyncBatchRequest ok = new DeviceOfflineSyncBatchRequest();
        ok.setSyncSessionId("m-ok");
        DeviceOfflineCommandRequest cmd1 = new DeviceOfflineCommandRequest();
        cmd1.setClientRequestId("cmd-ok");
        cmd1.setCommandType(DeviceOfflineCommandType.CREATE_PEDIDO_POS);
        cmd1.setCommandVersion("1");
        cmd1.setLocalCreatedAt(Instant.now());
        cmd1.setPayload(objectMapper.readTree("""
                {"itens":[{"produtoId": %d, "quantidade": 1}], "localTotalEstimado": 10.00}
                """.formatted(prodOk.getId())));
        ok.setCommands(List.of(cmd1));
        mockMvc.perform(post("/device/offline-sync/batch")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ok)))
                .andExpect(status().isOk());

        // session conflict (product inactive)
        DeviceOfflineSyncBatchRequest bad = new DeviceOfflineSyncBatchRequest();
        bad.setSyncSessionId("m-bad");
        DeviceOfflineCommandRequest cmd2 = new DeviceOfflineCommandRequest();
        cmd2.setClientRequestId("cmd-bad");
        cmd2.setCommandType(DeviceOfflineCommandType.CREATE_PEDIDO_POS);
        cmd2.setCommandVersion("1");
        cmd2.setLocalCreatedAt(Instant.now());
        cmd2.setPayload(objectMapper.readTree("""
                {"itens":[{"produtoId": %d, "quantidade": 1}], "localTotalEstimado": 10.00}
                """.formatted(prodInactive.getId())));
        bad.setCommands(List.of(cmd2));
        mockMvc.perform(post("/device/offline-sync/batch")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isOk());

        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));
        String resp = mockMvc.perform(get("/tenant/offline-sync/metrics"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode j = objectMapper.readTree(resp).at("/data/topConflictCodes");
        assertThat(j.toString()).contains("PRODUCT_INACTIVE");
    }

    private UsernamePasswordAuthenticationToken authFor(ProvisionarTenantResponse prov, DispositivoOperacional disp) {
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
                .nome("Produto " + System.nanoTime())
                .preco(new BigDecimal("10.00"))
                .categoria(CategoriaProdutoLegacy.OUTROS)
                .ativo(ativo)
                .disponivel(true)
                .build();
        prod.setTenant(t);
        prod.setCategoriaProduto(cat);
        return produtoRepository.save(prod);
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

    private ProvisionarTenantResponse provisionTenant(String nome, String code) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of(Role.ROLE_ADMIN.name()),
                TenantResolutionSource.JWT, true, false
        ));
        String phone = "+244900" + Math.abs(nome.hashCode() % 1_000_000);
        return provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + nome)
                                .slug(nome)
                                .tenantCode(code)
                                .tipo(TenantTipo.VENDEDOR_RUA)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("VENDEDOR_RUA")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + nome)
                                .sigla(code)
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email(nome + "@owner.com")
                                .telefone(phone)
                                .criarUsuario(true)
                                .build())
                        .build()
        );
    }
}
