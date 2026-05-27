package com.restaurante.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import com.restaurante.dto.request.DeviceCriarPedidoItemRequest;
import com.restaurante.dto.request.DeviceCriarPedidoRequest;
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

import java.math.BigDecimal;
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
class DeviceOfflineManualPaymentIT extends PostgresTestcontainersConfig {

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
    void offline_createManualOrderCash_and_confirmManualCash_applies() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("offline-manual", "OMN");
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

        // Abre caixa OPEN para o device (Prompt 42 exige caixa para confirmação manual CASH/TPA)
        var openCash = new com.restaurante.dto.request.AbrirCaixaOperadorRequest();
        openCash.setOperadorUserId(prov.getOwnerUserId());
        mockMvc.perform(post("/device/caixa-operador/open")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(openCash)))
                .andExpect(status().isOk());

        // cria pedido online (para ter pedidoId)
        DeviceCriarPedidoRequest pedidoReq = new DeviceCriarPedidoRequest();
        pedidoReq.setClientRequestId("online-p-1");
        DeviceCriarPedidoItemRequest it = new DeviceCriarPedidoItemRequest();
        it.setProdutoId(prod.getId());
        it.setQuantidade(1);
        pedidoReq.setItens(List.of(it));
        String pedidoResp = mockMvc.perform(post("/device/pedidos")
                        .with(authentication(auth))
                        .header("Idempotency-Key", "idem-online-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode pedidoJson = objectMapper.readTree(pedidoResp);
        long pedidoId = pedidoJson.at("/data/pedidoId").asLong();
        BigDecimal total = new BigDecimal(pedidoJson.at("/data/total").asText("10.00"));

        // cria ordem manual CASH via offline sync
        DeviceOfflineSyncBatchRequest batch = new DeviceOfflineSyncBatchRequest();
        DeviceOfflineCommandRequest cmd = new DeviceOfflineCommandRequest();
        cmd.setClientRequestId("cmd-manual-1");
        cmd.setCommandType(DeviceOfflineCommandType.CREATE_ORDEM_PAGAMENTO_MANUAL);
        cmd.setCommandVersion("1");
        cmd.setLocalCreatedAt(Instant.now());
        cmd.setPayload(objectMapper.readTree("""
                {"destination":"PEDIDO","pedidoId":%d,"metodo":"CASH","valor":%s,"moeda":"AOA"}
                """.formatted(pedidoId, total.toPlainString())));
        batch.setCommands(List.of(cmd));

        String resp1 = mockMvc.perform(post("/device/offline-sync/batch")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode j1 = objectMapper.readTree(resp1);
        assertThat(j1.at("/data/applied").asInt()).isEqualTo(1);
        assertThat(j1.at("/data/results/0/createdEntityType").asText()).isEqualTo("ORDEM_PAGAMENTO");
        long ordemId = j1.at("/data/results/0/result/ordemPagamentoId").asLong();
        assertThat(ordemId).isPositive();

        // confirma manual CASH via offline sync
        DeviceOfflineSyncBatchRequest batch2 = new DeviceOfflineSyncBatchRequest();
        DeviceOfflineCommandRequest cmd2 = new DeviceOfflineCommandRequest();
        cmd2.setClientRequestId("cmd-confirm-1");
        cmd2.setCommandType(DeviceOfflineCommandType.CONFIRM_MANUAL_PAYMENT);
        cmd2.setCommandVersion("1");
        cmd2.setLocalCreatedAt(Instant.now());
        cmd2.setPayload(objectMapper.readTree("""
                {"ordemPagamentoId":%d,"metodo":"CASH","valorConfirmado":%s,"moeda":"AOA","comprovativoLocal":"OFFLINE-OK"}
                """.formatted(ordemId, total.toPlainString())));
        batch2.setCommands(List.of(cmd2));

        String resp2 = mockMvc.perform(post("/device/offline-sync/batch")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch2)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode j2 = objectMapper.readTree(resp2);
        assertThat(j2.at("/data/applied").asInt()).isEqualTo(1);
        assertThat(j2.at("/data/results/0/status").asText()).isEqualTo("APPLIED");
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
        req.setNome("Turno Offline Manual");
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
                .preco(new BigDecimal("10.00"))
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
        d.setNome("POS Offline Manual");
        d.setCodigo("POS-OFF-" + (System.nanoTime() % 100000));
        d.setTipo(DispositivoTipo.POS);
        d.setStatus(DispositivoStatus.ATIVO);
        return dispositivoOperacionalRepository.saveAndFlush(d);
    }
}
