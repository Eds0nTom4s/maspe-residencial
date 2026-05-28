package com.restaurante.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.AbrirCaixaOperadorRequest;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import com.restaurante.dto.request.ConfirmarOrdemManualRequest;
import com.restaurante.dto.request.DeviceCriarPedidoItemRequest;
import com.restaurante.dto.request.DeviceCriarPedidoRequest;
import com.restaurante.dto.request.DeviceOfflineCommandRequest;
import com.restaurante.dto.request.DeviceOfflineSyncBatchRequest;
import com.restaurante.dto.request.FecharCaixaOperadorRequest;
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
import com.restaurante.model.enums.MetodoPagamentoManual;
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
class CaixaOperadorDeviceIT extends PostgresTestcontainersConfig {

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
    void confirmManualCash_requires_open_cash_session() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("caixa-req", "CXR");
        openTurno(prov);
        Produto prod = criarProdutoBasico(prov.getTenantId());
        DispositivoOperacional disp = criarDevicePos(prov);

        UsernamePasswordAuthenticationToken auth = deviceAuth(prov, disp, List.of(
                DeviceCapability.CREATE_ORDER,
                DeviceCapability.CONFIRM_CASH_PAYMENT,
                DeviceCapability.VIEW_PAYMENT_ORDER,
                DeviceCapability.OPEN_OPERATOR_CASH_SESSION,
                DeviceCapability.CLOSE_OPERATOR_CASH_SESSION,
                DeviceCapability.VIEW_OPERATOR_CASH_SESSION,
                DeviceCapability.VIEW_OPERATOR_CASH_SESSION_ITEMS
        ));

        long pedidoId = criarPedidoOnline(auth, prod.getId());
        long ordemId = criarOrdemManualOffline(auth, pedidoId, "cmd-ordem-1", MetodoPagamentoManual.CASH);

        // sem abrir caixa -> conflito
        ConfirmarOrdemManualRequest confirm = new ConfirmarOrdemManualRequest();
        confirm.setClientRequestId("confirm-1");
        confirm.setMetodoConfirmado(MetodoPagamentoManual.CASH);
        confirm.setValorRecebido(new BigDecimal("10.00"));

        mockMvc.perform(post("/device/ordens-pagamento/" + ordemId + "/confirmar-manual")
                        .with(authentication(auth))
                        .header("Idempotency-Key", "idem-confirm-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirm)))
                .andExpect(status().isConflict());

        // abre caixa e confirma
        AbrirCaixaOperadorRequest open = new AbrirCaixaOperadorRequest();
        open.setOperadorUserId(prov.getOwnerUserId());
        String openResp = mockMvc.perform(post("/device/caixa-operador/open")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(open)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode openJson = objectMapper.readTree(openResp);
        long caixaId = openJson.at("/data/id").asLong();
        assertThat(caixaId).isPositive();

        mockMvc.perform(post("/device/ordens-pagamento/" + ordemId + "/confirmar-manual")
                        .with(authentication(auth))
                        .header("Idempotency-Key", "idem-confirm-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirm)))
                .andExpect(status().isOk());
    }

    @Test
    void close_cash_session_calculates_expected_and_differences_and_creates_items() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("caixa-close", "CXC");
        openTurno(prov);
        Produto prod = criarProdutoBasico(prov.getTenantId());
        DispositivoOperacional disp = criarDevicePos(prov);

        UsernamePasswordAuthenticationToken auth = deviceAuth(prov, disp, List.of(
                DeviceCapability.CREATE_ORDER,
                DeviceCapability.CONFIRM_CASH_PAYMENT,
                DeviceCapability.CONFIRM_TPA_PAYMENT,
                DeviceCapability.VIEW_PAYMENT_ORDER,
                DeviceCapability.OPEN_OPERATOR_CASH_SESSION,
                DeviceCapability.CLOSE_OPERATOR_CASH_SESSION,
                DeviceCapability.VIEW_OPERATOR_CASH_SESSION,
                DeviceCapability.VIEW_OPERATOR_CASH_SESSION_ITEMS
        ));

        AbrirCaixaOperadorRequest open = new AbrirCaixaOperadorRequest();
        open.setOperadorUserId(prov.getOwnerUserId());
        String openResp = mockMvc.perform(post("/device/caixa-operador/open")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(open)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long caixaId = objectMapper.readTree(openResp).at("/data/id").asLong();

        long pedidoCash = criarPedidoOnline(auth, prod.getId());
        long ordemCash = criarOrdemManualOffline(auth, pedidoCash, "cmd-ordem-cash", MetodoPagamentoManual.CASH);
        confirmarOrdem(auth, ordemCash, "c-1", MetodoPagamentoManual.CASH, new BigDecimal("10.00"), "idem-conf-cash");

        long pedidoTpa = criarPedidoOnline(auth, prod.getId());
        long ordemTpa = criarOrdemManualOffline(auth, pedidoTpa, "cmd-ordem-tpa", MetodoPagamentoManual.TPA);
        confirmarOrdem(auth, ordemTpa, "t-1", MetodoPagamentoManual.TPA, new BigDecimal("10.00"), "idem-conf-tpa");

        FecharCaixaOperadorRequest close = new FecharCaixaOperadorRequest();
        close.setDeclaredCashAmount(new BigDecimal("10.00"));
        close.setDeclaredTpaAmount(new BigDecimal("10.00"));

        String closeResp = mockMvc.perform(post("/device/caixa-operador/" + caixaId + "/close")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(close)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode closeJson = objectMapper.readTree(closeResp);
        assertThat(closeJson.at("/data/status").asText()).isEqualTo("CLOSED");
        assertThat(closeJson.at("/data/expectedCashAmount").asText()).isEqualTo("10.00");
        assertThat(closeJson.at("/data/expectedTpaAmount").asText()).isEqualTo("10.00");
        assertThat(closeJson.at("/data/manualDifferenceAmount").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private UsernamePasswordAuthenticationToken deviceAuth(ProvisionarTenantResponse prov, DispositivoOperacional disp, List<DeviceCapability> caps) {
        DevicePrincipal device = new DevicePrincipal(
                disp.getId(), disp.getCodigo(),
                prov.getTenantId(), prov.getTenantCode(),
                prov.getInstituicaoId(), prov.getUnidadeAtendimentoId(), null,
                DispositivoTipo.POS, DispositivoStatus.ATIVO,
                caps,
                1
        );
        return new UsernamePasswordAuthenticationToken(device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE")));
    }

    private long criarPedidoOnline(UsernamePasswordAuthenticationToken auth, Long produtoId) throws Exception {
        DeviceCriarPedidoRequest pedidoReq = new DeviceCriarPedidoRequest();
        pedidoReq.setClientRequestId("online-" + System.nanoTime());
        DeviceCriarPedidoItemRequest it = new DeviceCriarPedidoItemRequest();
        it.setProdutoId(produtoId);
        it.setQuantidade(1);
        pedidoReq.setItens(List.of(it));
        String pedidoResp = mockMvc.perform(post("/device/pedidos")
                        .with(authentication(auth))
                        .header("Idempotency-Key", "idem-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(pedidoResp).at("/data/pedidoId").asLong();
    }

    private long criarOrdemManualOffline(UsernamePasswordAuthenticationToken auth, long pedidoId, String clientRequestId, MetodoPagamentoManual metodo) throws Exception {
        DeviceOfflineSyncBatchRequest batch = new DeviceOfflineSyncBatchRequest();
        DeviceOfflineCommandRequest cmd = new DeviceOfflineCommandRequest();
        cmd.setClientRequestId(clientRequestId);
        cmd.setCommandType(DeviceOfflineCommandType.CREATE_ORDEM_PAGAMENTO_MANUAL);
        cmd.setCommandVersion("1");
        cmd.setLocalCreatedAt(Instant.now());
        cmd.setPayload(objectMapper.readTree("""
                {"destination":"PEDIDO","pedidoId":%d,"metodo":"%s","valor":10.00,"moeda":"AOA"}
                """.formatted(pedidoId, metodo.name())));
        batch.setCommands(List.of(cmd));

        String resp = mockMvc.perform(post("/device/offline-sync/batch")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode j = objectMapper.readTree(resp);
        return j.at("/data/results/0/createdEntityId").asLong();
    }

    private void confirmarOrdem(UsernamePasswordAuthenticationToken auth,
                               long ordemId,
                               String clientRequestId,
                               MetodoPagamentoManual metodo,
                               BigDecimal valorRecebido,
                               String idemKey) throws Exception {
        ConfirmarOrdemManualRequest confirm = new ConfirmarOrdemManualRequest();
        confirm.setClientRequestId(clientRequestId);
        confirm.setMetodoConfirmado(metodo);
        confirm.setValorRecebido(valorRecebido);
        confirm.setReferenciaOperador("OK");
        mockMvc.perform(post("/device/ordens-pagamento/" + ordemId + "/confirmar-manual")
                        .with(authentication(auth))
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirm)))
                .andExpect(status().isOk());
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
        req.setNome("Turno Caixa");
        req.setChecklist(List.of(
                boolItem("DEVICE_ONLINE", true),
                boolItem("QR_VISIVEL", true),
                boolItem("CATALOGO_ATUALIZADO", true),
                boolItem("UNIDADE_PRODUCAO_ATIVA", true),
                boolItem("OPERADOR_CONFIRMOU", true)
        ));
        var userAuth = new UsernamePasswordAuthenticationToken(
                prov.getOwnerUserId().toString(), "N/A", List.of(new SimpleGrantedAuthority("ROLE_GERENTE"))
        );
        mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .with(authentication(userAuth))
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
                .nome("Produto Caixa")
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
        d.setTipo(DispositivoTipo.POS);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setCodigo("POS-" + (System.nanoTime() % 1_000_000));
        d.setNome("POS Caixa");
        return dispositivoOperacionalRepository.save(d);
    }
}
