package com.restaurante.financeiro;

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
import com.restaurante.financeiro.polling.PagamentoGatewayPollingService;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.OperationalEventLogRepository;
import com.restaurante.repository.PedidoRepository;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.web-application-type=servlet",
                "consuma.payment.polling.enabled=true",
                "consuma.payment.polling.initial-delay-minutes=0",
                "consuma.payment.polling.fixed-delay-ms=9999999"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class PagamentoGatewayPollingIT extends DeviceAuthIntegrationTestSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired PagamentoGatewayRepository pagamentoGatewayRepository;
    @Autowired OperationalEventLogRepository operationalEventLogRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired PagamentoGatewayPollingService pollingService;
    @Autowired FinanceiroItFixtureSupport fixtureSupport;
    @Autowired PlatformTransactionManager transactionManager;

    @MockBean AppyPayClient appyPayClient;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void polling_confirmsPagamento_whenGatewayConfirmed_andDoesNotDuplicate() throws Exception {
        when(appyPayClient.createCharge(any())).thenReturn(AppyPayChargeResponse.builder()
                .chargeId("ch_poll_1")
                .merchantTransactionId("IGNORED")
                .status("PENDING")
                .paymentMethod("REF")
                .entity("10100")
                .reference("999123456")
                .build());

        when(appyPayClient.getCharge("ch_poll_1")).thenReturn(AppyPayChargeResponse.builder()
                .chargeId("ch_poll_1")
                .status("CONFIRMED")
                .amount(1200L)
                .build());

        ProvisionarTenantResponse prov = provisionTenant("poll-pay-1", "PP1");

        // abrir turno (tenant context)
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));
        mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .with(authentication(tenantAuth(prov.getOwnerUserId(), TenantUserRole.TENANT_OWNER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(abrirTurnoReq(prov))))
                .andExpect(status().isCreated());

        fixtureSupport.ensureCentralCozinha(prov);
        Produto prod = criarProdutoBasico(prov.getTenantId());
        DispositivoOperacional disp = fixtureSupport.createPosDevice(prov, "POS POLL 1");
        String deviceToken = activateDeviceForTest(
                disp,
                List.of(DeviceCapability.CREATE_ORDER, DeviceCapability.INITIATE_PAYMENT, DeviceCapability.VIEW_PAYMENTS, DeviceCapability.VIEW_ORDERS)
        );

        Long pedidoId = criarPedidoViaDevice(deviceToken, prov, prod.getId());

        DeviceIniciarPagamentoRequest payReq = new DeviceIniciarPagamentoRequest();
        payReq.setClientRequestId("poll-pay-req-1");
        payReq.setMetodoPagamento(MetodoPagamentoAppyPay.REF);

        String resp = mockMvc.perform(post("/device/pedidos/{id}/pagamentos", pedidoId)
                        .header("Authorization", deviceAuthorization(deviceToken))
                        .header("Idempotency-Key", "idem-pay-poll-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long pagamentoId = objectMapper.readTree(resp).at("/data/pagamentoId").asLong();
        Pagamento pgBefore = pagamentoGatewayRepository.findById(pagamentoId).orElseThrow();
        assertThat(pgBefore.getStatus().name()).isEqualTo("PENDENTE");
        assertThat(pgBefore.getGatewayChargeId()).isEqualTo("ch_poll_1");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> pollingService.pollPagamento(pagamentoId));

        Pagamento pgAfter = pagamentoGatewayRepository.findById(pagamentoId).orElseThrow();
        assertThat(pgAfter.getStatus().name()).isEqualTo("CONFIRMADO");
        assertThat(pedidoRepository.findById(pedidoId).orElseThrow().getStatusFinanceiro().name()).isEqualTo("PAGO");

        // polling novamente não duplica
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> pollingService.pollPagamento(pagamentoId));
        Pagamento pgAfter2 = pagamentoGatewayRepository.findById(pagamentoId).orElseThrow();
        assertThat(pgAfter2.getStatus().name()).isEqualTo("CONFIRMADO");

        // getCharge deve ser chamado pelo menos 1x; a segunda chamada pode ser ignorada por status terminal
        verify(appyPayClient, times(1)).getCharge("ch_poll_1");

        var events = operationalEventLogRepository.findByTenantIdAndEventType(prov.getTenantId(), OperationalEventType.PAGAMENTO_CONFIRMADO_POR_POLLING);
        assertThat(events).isNotEmpty();
    }

    private Long criarPedidoViaDevice(String deviceToken, ProvisionarTenantResponse prov, Long produtoId) throws Exception {
        DeviceCriarPedidoRequest req = new DeviceCriarPedidoRequest();
        req.setClientRequestId("poll-order-" + System.nanoTime());
        req.setMesaId(prov.getMesas() != null && !prov.getMesas().isEmpty() ? prov.getMesas().get(0).getMesaId() : null);
        DeviceCriarPedidoItemRequest it = new DeviceCriarPedidoItemRequest();
        it.setProdutoId(produtoId);
        it.setQuantidade(1);
        req.setItens(List.of(it));

        String resp = mockMvc.perform(post("/device/pedidos")
                        .header("Authorization", deviceAuthorization(deviceToken))
                        .header("Idempotency-Key", "idem-order-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).at("/data/pedidoId").asLong();
    }

    private AbrirTurnoRequest abrirTurnoReq(ProvisionarTenantResponse prov) {
        AbrirTurnoRequest req = new AbrirTurnoRequest();
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());
        req.setTipo(TurnoOperacionalTipo.DIARIO);
        req.setNome("Turno Polling");
        req.setChecklist(List.of(
                item("DEVICE_ONLINE", true),
                item("QR_VISIVEL", true),
                item("CATALOGO_ATUALIZADO", true),
                item("UNIDADE_PRODUCAO_ATIVA", true),
                item("OPERADOR_CONFIRMOU", true)
        ));
        return req;
    }

    private ChecklistItemRespostaRequest item(String codigo, boolean valor) {
        ChecklistItemRespostaRequest item = new ChecklistItemRespostaRequest();
        item.setCodigo(codigo);
        item.setValorBoolean(valor);
        return item;
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
                .nome("Produto Poll")
                .preco(new BigDecimal("12.00"))
                .categoria(CategoriaProdutoLegacy.OUTROS)
                .ativo(true)
                .disponivel(true)
                .build();
        prod.setTenant(tenant);
        prod.setCategoriaProduto(cat);
        return produtoRepository.save(prod);
    }

    private UsernamePasswordAuthenticationToken tenantAuth(Long userId, TenantUserRole role) {
        return new UsernamePasswordAuthenticationToken(
                String.valueOf(userId),
                "N/A",
                List.of(
                        new SimpleGrantedAuthority(Role.ROLE_GERENTE.name()),
                        new SimpleGrantedAuthority(role.name())
                )
        );
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
