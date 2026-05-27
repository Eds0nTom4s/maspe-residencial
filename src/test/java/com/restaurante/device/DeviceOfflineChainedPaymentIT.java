package com.restaurante.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
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
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalTipo;
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

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class DeviceOfflineChainedPaymentIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantRepository tenantRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void chainedBatch_createPedido_thenCreateManualOrderByLocalRef_thenConfirmByLocalRef_works_andReplayIsDuplicate() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("offline-chain", "OCH");
        openTurno(prov);
        Produto prod = criarProdutoBasico(prov.getTenantId());
        DispositivoOperacional disp = criarDevicePos(prov);

        DevicePrincipal device = new DevicePrincipal(
                disp.getId(), disp.getCodigo(),
                prov.getTenantId(), prov.getTenantCode(),
                prov.getInstituicaoId(), prov.getUnidadeAtendimentoId(), null,
                DispositivoTipo.POS, DispositivoStatus.ATIVO,
                List.of(
                        DeviceCapability.CREATE_ORDER,
                        DeviceCapability.CONFIRM_CASH_PAYMENT,
                        DeviceCapability.OPEN_OPERATOR_CASH_SESSION,
                        DeviceCapability.CLOSE_OPERATOR_CASH_SESSION,
                        DeviceCapability.VIEW_OPERATOR_CASH_SESSION,
                        DeviceCapability.OFFLINE_SYNC,
                        DeviceCapability.OFFLINE_CREATE_ORDER,
                        DeviceCapability.OFFLINE_CREATE_MANUAL_PAYMENT_ORDER,
                        DeviceCapability.OFFLINE_CONFIRM_MANUAL_PAYMENT
                ),
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE")));

        var openCash = new com.restaurante.dto.request.AbrirCaixaOperadorRequest();
        openCash.setOperadorUserId(prov.getOwnerUserId());
        mockMvc.perform(post("/device/caixa-operador/open")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(openCash)))
                .andExpect(status().isOk());

        DeviceOfflineCommandRequest cmd1 = new DeviceOfflineCommandRequest();
        cmd1.setClientRequestId("cmd-pedido-001");
        cmd1.setCommandType(DeviceOfflineCommandType.CREATE_PEDIDO_POS);
        cmd1.setCommandVersion("1");
        cmd1.setLocalCreatedAt(Instant.now());
        cmd1.setPayload(objectMapper.readTree("""
                {"itens":[{"produtoId":%d,"quantidade":1}],"localTotalEstimado":10.00}
                """.formatted(prod.getId())));

        DeviceOfflineCommandRequest cmd2 = new DeviceOfflineCommandRequest();
        cmd2.setClientRequestId("cmd-ordem-001");
        cmd2.setCommandType(DeviceOfflineCommandType.CREATE_ORDEM_PAGAMENTO_MANUAL);
        cmd2.setCommandVersion("1");
        cmd2.setLocalCreatedAt(Instant.now());
        cmd2.setPayload(objectMapper.readTree("""
                {"destination":"PEDIDO","pedidoClientRequestId":"cmd-pedido-001","metodo":"CASH","valor":10.00,"moeda":"AOA"}
                """));

        DeviceOfflineCommandRequest cmd3 = new DeviceOfflineCommandRequest();
        cmd3.setClientRequestId("cmd-confirm-001");
        cmd3.setCommandType(DeviceOfflineCommandType.CONFIRM_MANUAL_PAYMENT);
        cmd3.setCommandVersion("1");
        cmd3.setLocalCreatedAt(Instant.now());
        cmd3.setPayload(objectMapper.readTree("""
                {"ordemPagamentoClientRequestId":"cmd-ordem-001","metodo":"CASH","valorConfirmado":10.00,"moeda":"AOA","comprovativoLocal":"OFFLINE"}
                """));

        DeviceOfflineSyncBatchRequest batch = new DeviceOfflineSyncBatchRequest();
        batch.setCommands(List.of(cmd1, cmd2, cmd3));

        String resp1 = mockMvc.perform(post("/device/offline-sync/batch")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode j1 = objectMapper.readTree(resp1);
        assertThat(j1.at("/data/applied").asInt()).isEqualTo(3);
        assertThat(j1.at("/data/results/0/status").asText()).isEqualTo("APPLIED");
        assertThat(j1.at("/data/results/1/status").asText()).isEqualTo("APPLIED");
        assertThat(j1.at("/data/results/2/status").asText()).isEqualTo("APPLIED");

        String resp2 = mockMvc.perform(post("/device/offline-sync/batch")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode j2 = objectMapper.readTree(resp2);
        assertThat(j2.at("/data/duplicates").asInt()).isEqualTo(3);
        assertThat(j2.at("/data/applied").asInt()).isEqualTo(0);
        assertThat(j2.at("/data/results/0/status").asText()).isEqualTo("DUPLICATE");
        assertThat(j2.at("/data/results/1/status").asText()).isEqualTo("DUPLICATE");
        assertThat(j2.at("/data/results/2/status").asText()).isEqualTo("DUPLICATE");
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

    private void openTurno(ProvisionarTenantResponse prov) throws Exception {
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));
        AbrirTurnoRequest req = new AbrirTurnoRequest();
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());
        req.setTipo(TurnoOperacionalTipo.POS);
        req.setNome("Turno Offline Chain");
        req.setChecklist(List.of(
                boolItem("DEVICE_ONLINE", true),
                boolItem("QR_VISIVEL", true),
                boolItem("CATALOGO_ATUALIZADO", true),
                boolItem("UNIDADE_PRODUCAO_ATIVA", true),
                boolItem("OPERADOR_CONFIRMOU", true)
        ));
        mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    private ChecklistItemRespostaRequest boolItem(String codigo, boolean v) {
        ChecklistItemRespostaRequest r = new ChecklistItemRespostaRequest();
        r.setCodigo(codigo);
        r.setValorBoolean(v);
        return r;
    }

    private Produto criarProdutoBasico(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        CategoriaProduto cat = new CategoriaProduto();
        cat.setTenant(tenant);
        cat.setNome("Geral");
        cat.setSlug("geral-" + (System.nanoTime() % 100_000));
        cat.setAtivo(true);
        cat = categoriaProdutoRepository.save(cat);

        Produto prod = Produto.builder()
                .codigo("P-" + (System.nanoTime() % 1_000_000))
                .nome("Produto Offline")
                .preco(new java.math.BigDecimal("10.00"))
                .categoria(CategoriaProdutoLegacy.OUTROS)
                .ativo(true)
                .disponivel(true)
                .build();
        prod.setTenant(tenant);
        prod.setCategoriaProduto(cat);
        return produtoRepository.save(prod);
    }

    private DispositivoOperacional criarDevicePos(ProvisionarTenantResponse prov) {
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        var inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        var ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();
        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setNome("POS Offline Chain");
        d.setCodigo("POS-OFF-" + (System.nanoTime() % 100000));
        d.setTipo(DispositivoTipo.POS);
        d.setStatus(DispositivoStatus.ATIVO);
        return dispositivoOperacionalRepository.saveAndFlush(d);
    }
}
