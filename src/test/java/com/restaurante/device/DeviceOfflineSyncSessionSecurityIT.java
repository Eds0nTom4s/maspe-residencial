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
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.TenantProvisioningService;
import com.restaurante.testsupport.DeviceAuthIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class DeviceOfflineSyncSessionSecurityIT extends DeviceAuthIntegrationTestSupport {

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
    void device_cannot_view_other_device_sync_session() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("offline-sess-sec", "OSS");
        Produto prod = criarProdutoBasico(prov.getTenantId());
        DispositivoOperacional deviceA = criarDevicePos(prov, "POS A");
        DispositivoOperacional deviceB = criarDevicePos(prov, "POS B");
        String deviceAToken = activateDeviceForTest(deviceA, List.of(DeviceCapability.OFFLINE_SYNC, DeviceCapability.OFFLINE_CREATE_ORDER));
        String deviceBToken = activateDeviceForTest(deviceB, List.of(DeviceCapability.OFFLINE_SYNC));

        DeviceOfflineSyncBatchRequest batch = new DeviceOfflineSyncBatchRequest();
        batch.setSyncSessionId("sess-A");
        DeviceOfflineCommandRequest cmd = new DeviceOfflineCommandRequest();
        cmd.setClientRequestId("cmd-1");
        cmd.setCommandType(DeviceOfflineCommandType.CREATE_PEDIDO_POS);
        cmd.setCommandVersion("1");
        cmd.setLocalCreatedAt(Instant.now());
        cmd.setPayload(objectMapper.readTree("""
                {"itens":[{"produtoId": %d, "quantidade": 1}], "localTotalEstimado": 10.00}
                """.formatted(prod.getId())));
        batch.setCommands(List.of(cmd));

        String resp = mockMvc.perform(post("/device/offline-sync/batch")
                        .header("Authorization", deviceAuthorization(deviceAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(resp).at("/data");
        String serverSyncId = json.get("serverSyncId").asText();

        mockMvc.perform(get("/device/offline-sync/sessions/{serverSyncId}", serverSyncId)
                        .header("Authorization", deviceAuthorization(deviceBToken)))
                .andExpect(status().isNotFound());
    }

    private Produto criarProdutoBasico(Long tenantId) {
        Tenant t = tenantRepository.findById(tenantId).orElseThrow();
        CategoriaProduto cat = new CategoriaProduto();
        cat.setTenant(t);
        cat.setNome("Geral");
        cat.setSlug("geral-" + (System.nanoTime() % 100_000));
        cat.setAtivo(true);
        cat = categoriaProdutoRepository.save(cat);

        Produto prod = Produto.builder()
                .codigo("P-" + (System.nanoTime() % 1_000_000))
                .nome("Produto Offline")
                .preco(new BigDecimal("10.00"))
                .categoria(CategoriaProdutoLegacy.OUTROS)
                .ativo(true)
                .disponivel(true)
                .build();
        prod.setTenant(t);
        prod.setCategoriaProduto(cat);
        return produtoRepository.save(prod);
    }

    private DispositivoOperacional criarDevicePos(ProvisionarTenantResponse prov, String name) {
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        var inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        var ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();
        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setCodigo("DEV-OFF-" + System.nanoTime());
        d.setNome(name);
        d.setTipo(DispositivoTipo.POS);
        d.setOperationalDeviceType(OperationalDeviceType.POS_CAIXA);
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
