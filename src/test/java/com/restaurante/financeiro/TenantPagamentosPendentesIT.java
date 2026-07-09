package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import com.restaurante.dto.request.DeviceCriarPedidoItemRequest;
import com.restaurante.dto.request.DeviceCriarPedidoRequest;
import com.restaurante.dto.request.DeviceIniciarPagamentoRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import com.restaurante.financeiro.gateway.appypay.AppyPayClient;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeResponse;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodBootstrapService;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.OperationalEventLogRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UserRepository;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
@Transactional
class TenantPagamentosPendentesIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
	@Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
	@Autowired OperationalEventLogRepository operationalEventLogRepository;
	@Autowired UserRepository userRepository;
	@Autowired CozinhaRepository cozinhaRepository;
    @Autowired PagamentoGatewayRepository pagamentoGatewayRepository;
    @Autowired TenantPaymentMethodBootstrapService paymentMethodBootstrapService;

    @MockBean AppyPayClient appyPayClient;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void finance_canListPendentes_andCanForceManualPolling() throws Exception {
        when(appyPayClient.createCharge(any())).thenReturn(AppyPayChargeResponse.builder()
                .chargeId("ch_pend_1")
                .merchantTransactionId("IGNORED")
                .status("PENDING")
                .paymentMethod("REF")
                .entity("10100")
                .reference("999123456")
                .build());

        when(appyPayClient.getCharge("ch_pend_1")).thenReturn(AppyPayChargeResponse.builder()
                .chargeId("ch_pend_1")
                .status("CONFIRMED")
                .amount(1200L)
                .paymentMethod("REF")
                .entity("10100")
                .reference("999123456")
                .build());

        ProvisionarTenantResponse prov = provisionTenant("pend-ops-1", "PO1");
        paymentMethodBootstrapService.ensureDefaultsInCurrentTransaction(
                tenantRepository.findById(prov.getTenantId()).orElseThrow()
        );

        com.restaurante.model.entity.User owner = userRepository.findById(prov.getOwnerUserId()).orElseThrow();
        UsernamePasswordAuthenticationToken ownerAuth = new UsernamePasswordAuthenticationToken(
                owner,
                "N/A",
                List.of(new SimpleGrantedAuthority(Role.ROLE_GERENTE.name()))
        );

        var abrirTurnoResp = mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .with(authentication(ownerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(abrirTurnoReq(prov))))
                .andExpect(status().isCreated());
        Long turnoId = objectMapper.readTree(abrirTurnoResp.andReturn().getResponse().getContentAsString()).at("/data/id").asLong();

        ensureCozinhaCentralAtiva(prov);

        Produto prod = criarProdutoBasico(prov.getTenantId());
        DispositivoOperacional disp = criarDevicePos(prov);
        DevicePrincipal device = new DevicePrincipal(
                disp.getId(), disp.getCodigo(),
                prov.getTenantId(), prov.getTenantCode(),
                prov.getInstituicaoId(), prov.getUnidadeAtendimentoId(), null,
                DispositivoTipo.POS, DispositivoStatus.ATIVO,
                List.of(DeviceCapability.CREATE_ORDER, DeviceCapability.INITIATE_PAYMENT, DeviceCapability.VIEW_ORDERS, DeviceCapability.VIEW_PAYMENTS),
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );

        Long pedidoId = criarPedidoViaDevice(auth, prov, prod.getId());

        DeviceIniciarPagamentoRequest payReq = new DeviceIniciarPagamentoRequest();
        payReq.setClientRequestId("pend-pay-req-1");
        payReq.setMetodoPagamento(MetodoPagamentoAppyPay.REF);
        String payResp = mockMvc.perform(post("/device/pedidos/{id}/pagamentos", pedidoId)
                        .with(authentication(auth))
                        .header("Idempotency-Key", "idem-pend-pay-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

	        long pagamentoId = objectMapper.readTree(payResp).at("/data/pagamentoId").asLong();
	        // Polling manual tem delay inicial (default 2min). Ajustar createdAt para tornar elegível sem flakiness.
	        Pagamento pg = pagamentoGatewayRepository.findById(pagamentoId).orElseThrow();
	        pg.setCreatedAt(LocalDateTime.now().minusMinutes(5));
	        pagamentoGatewayRepository.saveAndFlush(pg);

	        String pend = mockMvc.perform(get("/tenant/financeiro/pagamentos/pendentes")
	                        .with(authentication(ownerAuth))
	                        .accept(MediaType.APPLICATION_JSON))
	                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode pendJson = objectMapper.readTree(pend);
        assertThat(pendJson.at("/data/content").isArray()).isTrue();
        assertThat(pendJson.at("/data/content").size()).isGreaterThanOrEqualTo(1);
        assertThat(pend).contains("\"pagamentoId\":" + pagamentoId);

        String resumo = mockMvc.perform(get("/tenant/financeiro/pagamentos/pendentes/resumo")
                        .with(authentication(ownerAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(resumo).at("/data/totalPendentes").asLong()).isGreaterThanOrEqualTo(1);

        String detalhe = mockMvc.perform(get("/tenant/financeiro/pagamentos/{id}/polling", pagamentoId)
                        .with(authentication(ownerAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(detalhe).at("/data/pollingAttempts").asInt()).isGreaterThanOrEqualTo(0);

        String manual = mockMvc.perform(post("/tenant/financeiro/pagamentos/{id}/poll", pagamentoId)
                        .with(authentication(ownerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Cliente apresentou comprovativo\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode manualJson = objectMapper.readTree(manual);
        assertThat(manualJson.at("/data/confirmado").asBoolean()).as(manual).isTrue();

        // eventos manuais registrados via OperationalEventLog (entityType PAGAMENTO)
        var recent = operationalEventLogRepository.findTop20ByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
                prov.getTenantId(), OperationalEntityType.PAGAMENTO, pagamentoId
        );
        assertThat(recent.stream().anyMatch(e -> e.getEventType() == OperationalEventType.PAGAMENTO_POLLING_MANUAL_SOLICITADO)).isTrue();
        assertThat(recent.stream().anyMatch(e -> e.getEventType() == OperationalEventType.PAGAMENTO_POLLING_MANUAL_EXECUTADO)).isTrue();

        // alertas por turno não deve ser 404
        mockMvc.perform(get("/tenant/financeiro/turnos/{turnoId}/alertas-pagamento", turnoId)
                        .with(authentication(ownerAuth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private Long criarPedidoViaDevice(UsernamePasswordAuthenticationToken auth, ProvisionarTenantResponse prov, Long produtoId) throws Exception {
        DeviceCriarPedidoRequest req = new DeviceCriarPedidoRequest();
        req.setClientRequestId("pend-order-" + System.nanoTime());
        req.setMesaId(prov.getMesas() != null && !prov.getMesas().isEmpty() ? prov.getMesas().get(0).getMesaId() : null);
        DeviceCriarPedidoItemRequest it = new DeviceCriarPedidoItemRequest();
        it.setProdutoId(produtoId);
        it.setQuantidade(1);
        req.setItens(List.of(it));

        var result = mockMvc.perform(post("/device/pedidos")
                        .with(authentication(auth))
                        .header("Idempotency-Key", "idem-order-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        int httpStatus = result.getResponse().getStatus();
        String resp = result.getResponse().getContentAsString();
        assertThat(httpStatus).as(resp).isEqualTo(201);
        return objectMapper.readTree(resp).at("/data/pedidoId").asLong();
    }

    private AbrirTurnoRequest abrirTurnoReq(ProvisionarTenantResponse prov) {
        AbrirTurnoRequest req = new AbrirTurnoRequest();
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());
        req.setTipo(TurnoOperacionalTipo.DIARIO);
        req.setNome("Turno Pendências");
        req.setChecklist(List.of(
                boolItem("DEVICE_ONLINE", true),
                boolItem("QR_VISIVEL", true),
                boolItem("CATALOGO_ATUALIZADO", true),
                boolItem("UNIDADE_PRODUCAO_ATIVA", true),
                boolItem("OPERADOR_CONFIRMOU", true)
        ));
        return req;
    }

    private static ChecklistItemRespostaRequest boolItem(String codigo, boolean v) {
        ChecklistItemRespostaRequest it = new ChecklistItemRespostaRequest();
        it.setCodigo(codigo);
        it.setValorBoolean(v);
        return it;
    }

    private DispositivoOperacional criarDevicePos(ProvisionarTenantResponse prov) {
        var tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        var inst = instituicaoRepository.findByIdAndTenantId(prov.getInstituicaoId(), prov.getTenantId()).orElseThrow();
        var ua = unidadeAtendimentoRepository.findByIdAndTenantId(prov.getUnidadeAtendimentoId(), prov.getTenantId()).orElseThrow();

        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setNome("POS PEND 1");
        d.setCodigo("POS-PEND-" + (System.nanoTime() % 100000));
        d.setTipo(DispositivoTipo.POS);
        d.setStatus(DispositivoStatus.ATIVO);
        return dispositivoOperacionalRepository.saveAndFlush(d);
    }

    private Produto criarProdutoBasico(Long tenantId) {
        var tenant = tenantRepository.findById(tenantId).orElseThrow();
        CategoriaProduto cat = new CategoriaProduto();
        cat.setTenant(tenant);
        cat.setNome("Geral");
        cat.setSlug("geral-" + (System.nanoTime() % 100_000));
        cat.setAtivo(true);
        cat = categoriaProdutoRepository.save(cat);

        Produto prod = Produto.builder()
                .codigo("P-" + (System.nanoTime() % 1_000_000))
                .nome("Produto Pend")
                .preco(new BigDecimal("12.00"))
                .categoria(CategoriaProdutoLegacy.OUTROS)
                .ativo(true)
                .disponivel(true)
                .build();
        prod.setTenant(tenant);
        prod.setCategoriaProduto(cat);
        return produtoRepository.save(prod);
    }

    private void ensureCozinhaCentralAtiva(ProvisionarTenantResponse prov) {
        var ua = unidadeAtendimentoRepository.findByIdAndTenantId(prov.getUnidadeAtendimentoId(), prov.getTenantId()).orElseThrow();
        if (!cozinhaRepository.findByUnidadeAtendimentoId(ua.getId()).isEmpty()) return;

        Cozinha cozinha = new Cozinha();
        cozinha.setNome("Cozinha Central " + (System.nanoTime() % 100_000));
        cozinha.setTipo(TipoCozinha.CENTRAL);
        cozinha.setAtiva(true);
        cozinha = cozinhaRepository.saveAndFlush(cozinha);

        ua.adicionarCozinha(cozinha);
        unidadeAtendimentoRepository.saveAndFlush(ua);
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
                        .opcoes(ProvisionarTenantRequest.OpcoesProvisionamento.builder()
                                .criarMesas(true)
                                .quantidadeMesas(1)
                                .criarQrPorMesa(true)
                                .build())
                        .build()
        );
    }
}
