package com.restaurante.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import com.restaurante.dto.request.DeviceCriarPedidoItemRequest;
import com.restaurante.dto.request.DeviceCriarPedidoRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.DeviceCapability;
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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class DevicePedidoCreateIT extends DeviceAuthIntegrationTestSupport {

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
    void device_pos_withCreateOrder_canCreatePedido_andIdempotencyReplayWorks() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("dev-pos-order", "DPO");
        openTurno(prov);
        Produto prod = criarProdutoBasico(prov.getTenantId());
        DispositivoOperacional disp = criarDevicePos(prov);
        String deviceToken = activateDeviceForTest(disp, List.of(
                DeviceCapability.HEARTBEAT,
                DeviceCapability.SYNC_CATALOG,
                DeviceCapability.VIEW_ORDERS,
                DeviceCapability.CREATE_ORDER
        ));

        DeviceCriarPedidoRequest req = new DeviceCriarPedidoRequest();
        req.setClientRequestId("pos-req-1");
        req.setMesaId(prov.getMesas() != null && !prov.getMesas().isEmpty() ? prov.getMesas().get(0).getMesaId() : null);
        req.setObservacao("Sem cebola");
        DeviceCriarPedidoItemRequest it = new DeviceCriarPedidoItemRequest();
        it.setProdutoId(prod.getId());
        it.setQuantidade(2);
        it.setObservacao("Bem passado");
        req.setItens(List.of(it));

        String resp1 = mockMvc.perform(post("/device/pedidos")
                        .header("Authorization", deviceAuthorization(deviceToken))
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode j1 = objectMapper.readTree(resp1);
        long pedidoId1 = j1.at("/data/pedidoId").asLong();
        assertThat(pedidoId1).isPositive();
        assertThat(j1.at("/data/idempotentReplay").asBoolean()).isFalse();
        assertThat(j1.at("/data/turnoOperacionalId").asLong()).isPositive();

        String resp2 = mockMvc.perform(post("/device/pedidos")
                        .header("Authorization", deviceAuthorization(deviceToken))
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode j2 = objectMapper.readTree(resp2);
        assertThat(j2.at("/data/pedidoId").asLong()).isEqualTo(pedidoId1);
        assertThat(j2.at("/data/idempotentReplay").asBoolean()).isTrue();
    }

    @Test
    void idempotencyKey_sameButDifferentBody_returns409_withDeviceErrorResponse() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("dev-pos-order2", "DP2");
        openTurno(prov);
        Produto prod = criarProdutoBasico(prov.getTenantId());
        DispositivoOperacional disp = criarDevicePos(prov);
        String deviceToken = activateDeviceForTest(disp, List.of(DeviceCapability.VIEW_ORDERS, DeviceCapability.CREATE_ORDER));

        DeviceCriarPedidoRequest req = new DeviceCriarPedidoRequest();
        req.setClientRequestId("pos-req-2");
        DeviceCriarPedidoItemRequest it = new DeviceCriarPedidoItemRequest();
        it.setProdutoId(prod.getId());
        it.setQuantidade(1);
        req.setItens(List.of(it));

        mockMvc.perform(post("/device/pedidos")
                        .header("Authorization", deviceAuthorization(deviceToken))
                        .header("Idempotency-Key", "idem-x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // muda quantidade com mesma chave
        it.setQuantidade(2);
        String resp = mockMvc.perform(post("/device/pedidos")
                        .header("Authorization", deviceAuthorization(deviceToken))
                        .header("Idempotency-Key", "idem-x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/code").asText()).isEqualTo("DEVICE_ORDER_IDEMPOTENCY_CONFLICT");
    }

    @Test
    void device_withoutCreateOrder_isForbidden() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("dev-pos-order3", "DP3");
        DispositivoOperacional disp = criarDevicePos(prov);
        String deviceToken = activateDeviceForTest(disp, List.of(DeviceCapability.VIEW_ORDERS));

        Produto prod = criarProdutoBasico(prov.getTenantId());
        DeviceCriarPedidoRequest req = new DeviceCriarPedidoRequest();
        req.setClientRequestId("pos-req-3");
        DeviceCriarPedidoItemRequest it = new DeviceCriarPedidoItemRequest();
        it.setProdutoId(prod.getId());
        it.setQuantidade(1);
        req.setItens(List.of(it));

        String resp = mockMvc.perform(post("/device/pedidos")
                        .header("Authorization", deviceAuthorization(deviceToken))
                        .header("Idempotency-Key", "idem-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(resp);
        assertThat(json.at("/code").asText()).isEqualTo("DEVICE_ORDER_CREATE_FORBIDDEN");
    }

    private AbrirTurnoRequest abrirTurnoReq(ProvisionarTenantResponse prov) {
        AbrirTurnoRequest req = new AbrirTurnoRequest();
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());
        req.setTipo(TurnoOperacionalTipo.DIARIO);
        req.setNome("Turno POS");
        req.setChecklist(List.of(boolItem("DEVICE_ONLINE", true),
                boolItem("QR_VISIVEL", true),
                boolItem("CATALOGO_ATUALIZADO", true),
                boolItem("UNIDADE_PRODUCAO_ATIVA", true),
                boolItem("OPERADOR_CONFIRMOU", true)));
        return req;
    }

    private ChecklistItemRespostaRequest boolItem(String codigo, boolean v) {
        ChecklistItemRespostaRequest r = new ChecklistItemRespostaRequest();
        r.setCodigo(codigo);
        r.setValorBoolean(v);
        return r;
    }

    private void openTurno(ProvisionarTenantResponse prov) throws Exception {
        TenantContextHolder.clear();
        String token = issueTenantOwnerToken(prov);
        mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(abrirTurnoReq(prov))))
                .andExpect(status().isCreated());
    }

    private DispositivoOperacional criarDevicePos(ProvisionarTenantResponse prov) {
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        var inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        var ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();
        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setNome("POS 1");
        d.setCodigo("POS-" + (System.nanoTime() % 100000));
        d.setTipo(DispositivoTipo.POS);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setTokenVersion(1);
        return dispositivoOperacionalRepository.saveAndFlush(d);
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
                .nome("Produto POS")
                .preco(new BigDecimal("12.00"))
                .categoria(CategoriaProdutoLegacy.OUTROS)
                .ativo(true)
                .disponivel(true)
                .build();
        prod.setTenant(tenant);
        prod.setCategoriaProduto(cat);
        return produtoRepository.save(prod);
    }

    private ProvisionarTenantResponse provisionTenant(String slug, String tenantCode) {
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
                                .tenantCode(tenantCode)
                                .tipo(TenantTipo.VENDEDOR_RUA)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("VENDEDOR_RUA")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + slug)
                                .sigla(tenantCode)
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
