package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import com.restaurante.dto.request.DeviceCriarPedidoItemRequest;
import com.restaurante.dto.request.DeviceCriarPedidoRequest;
import com.restaurante.dto.request.DeviceIniciarPagamentoRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.UpdateDevicePaymentMethodPolicyRequest;
import com.restaurante.dto.request.UpdateUnidadePaymentMethodPolicyRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import com.restaurante.financeiro.gateway.appypay.AppyPayClient;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeResponse;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.financeiro.paymentmethod.repository.TenantPaymentMethodRepository;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodBootstrapService;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodPolicyStatus;
import com.restaurante.model.enums.PaymentMethodStatus;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class PaymentMethodPolicyAppyPayIT extends DeviceAuthIntegrationTestSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantRepository tenantRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired TenantPaymentMethodBootstrapService bootstrapService;
    @Autowired TenantPaymentMethodRepository tenantPaymentMethodRepository;
    @Autowired FinanceiroItFixtureSupport fixtureSupport;

    @MockBean AppyPayClient appyPayClient;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void pos_cannot_start_appypay_when_device_policy_canStartGateway_false() throws Exception {
        when(appyPayClient.createCharge(any())).thenReturn(AppyPayChargeResponse.builder()
                .chargeId("ch_pol_1")
                .status("PENDING")
                .paymentMethod("REF")
                .entity("10100")
                .reference("999123456")
                .paymentUrl("https://pay.local/pol")
                .build());

        ProvisionarTenantResponse prov = provisionTenant("pm-pol-appy-a", "PPYA");
        bootstrapService.ensureDefaults(prov.getTenantId());

        // garantir APPYPAY ACTIVE para não falhar por tenant-level
        var appy = tenantPaymentMethodRepository.findByTenantIdAndCode(prov.getTenantId(), PaymentMethodCode.APPYPAY).orElseThrow();
        appy.setStatus(PaymentMethodStatus.ACTIVE);
        appy.setEnabledForPos(true);
        tenantPaymentMethodRepository.saveAndFlush(appy);

        fixtureSupport.ensureCentralCozinha(prov);
        Produto prod = criarProdutoBasico(prov.getTenantId());

        DispositivoOperacional disp = criarDevicePos(prov);
        String deviceToken = activateDeviceForTest(
                disp,
                List.of(DeviceCapability.CREATE_ORDER, DeviceCapability.VIEW_PAYMENTS, DeviceCapability.INITIATE_PAYMENT)
        );

        // policy do device: bloquear start gateway
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

        UpdateDevicePaymentMethodPolicyRequest devPol = new UpdateDevicePaymentMethodPolicyRequest();
        devPol.setInheritFromUnidade(false);
        devPol.setStatus(PaymentMethodPolicyStatus.ALLOW);
        devPol.setCanStartGateway(false);
        mockMvc.perform(put("/tenant/devices/{deviceId}/payment-method-policies/{code}", disp.getId(), PaymentMethodCode.APPYPAY)
                        .with(authentication(tenantAuth(prov.getOwnerUserId(), TenantUserRole.TENANT_OWNER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(devPol)))
                .andExpect(status().isOk());

        Long pedidoId = criarPedidoViaDevice(deviceToken, prov, prod.getId());

        DeviceIniciarPagamentoRequest payReq = new DeviceIniciarPagamentoRequest();
        payReq.setClientRequestId("pol-pay-1");
        payReq.setMetodoPagamento(MetodoPagamentoAppyPay.REF);
        payReq.setTelefoneCliente("+244900000000");
        payReq.setDescricao("Pagamento POS");

        mockMvc.perform(post("/device/pedidos/{id}/pagamentos", pedidoId)
                        .header("Authorization", deviceAuthorization(deviceToken))
                        .header("Idempotency-Key", "idem-pol-pay-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isConflict());
    }

    @Test
    void manual_polling_confirms_pending_payment_even_if_unidade_policy_blocks_after_initiation() throws Exception {
        when(appyPayClient.createCharge(any())).thenReturn(AppyPayChargeResponse.builder()
                .chargeId("ch_pol_pend_1")
                .status("PENDING")
                .paymentMethod("REF")
                .entity("10100")
                .reference("999123456")
                .build());

        when(appyPayClient.getCharge("ch_pol_pend_1")).thenReturn(AppyPayChargeResponse.builder()
                .chargeId("ch_pol_pend_1")
                .status("CONFIRMED")
                .amount(1200L)
                .build());

        ProvisionarTenantResponse prov = provisionTenant("pm-pol-appy-b", "PPYB");
        bootstrapService.ensureDefaults(prov.getTenantId());

        var appy = tenantPaymentMethodRepository.findByTenantIdAndCode(prov.getTenantId(), PaymentMethodCode.APPYPAY).orElseThrow();
        appy.setStatus(PaymentMethodStatus.ACTIVE);
        appy.setEnabledForPos(true);
        tenantPaymentMethodRepository.saveAndFlush(appy);

        // abrir turno com OWNER
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

        // device cria pedido e inicia pagamento AppyPay (enquanto permitido)
        fixtureSupport.ensureCentralCozinha(prov);
        Produto prod = criarProdutoBasico(prov.getTenantId());
        DispositivoOperacional disp = criarDevicePos(prov);
        String deviceToken = activateDeviceForTest(
                disp,
                List.of(DeviceCapability.CREATE_ORDER, DeviceCapability.VIEW_PAYMENTS, DeviceCapability.INITIATE_PAYMENT)
        );
        Long pedidoId = criarPedidoViaDevice(deviceToken, prov, prod.getId());

        DeviceIniciarPagamentoRequest payReq = new DeviceIniciarPagamentoRequest();
        payReq.setClientRequestId("pol-pend-1");
        payReq.setMetodoPagamento(MetodoPagamentoAppyPay.REF);
        payReq.setTelefoneCliente("+244900000000");
        payReq.setDescricao("Pagamento POS");

        String payResp = mockMvc.perform(post("/device/pedidos/{id}/pagamentos", pedidoId)
                        .header("Authorization", deviceAuthorization(deviceToken))
                        .header("Idempotency-Key", "idem-pol-pend-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long pagamentoId = objectMapper.readTree(payResp).at("/data/pagamentoId").asLong();

        // agora unidade bloqueia APPYPAY (não deve quebrar polling de pagamento já iniciado)
        UpdateUnidadePaymentMethodPolicyRequest block = new UpdateUnidadePaymentMethodPolicyRequest();
        block.setInheritFromTenant(false);
        block.setStatus(PaymentMethodPolicyStatus.BLOCK);
        mockMvc.perform(put("/tenant/unidades/{unidadeId}/payment-method-policies/{code}", prov.getUnidadeAtendimentoId(), PaymentMethodCode.APPYPAY)
                        .with(authentication(tenantAuth(prov.getOwnerUserId(), TenantUserRole.TENANT_OWNER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(block)))
                .andExpect(status().isOk());

        // FINANCE força polling manual -> deve confirmar mesmo com policy bloqueada
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_FINANCE.name()),
                TenantResolutionSource.JWT, false, false
        ));
        String manual = mockMvc.perform(post("/tenant/financeiro/pagamentos/{id}/poll", pagamentoId)
                        .with(authentication(tenantAuth(prov.getOwnerUserId(), TenantUserRole.TENANT_FINANCE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"teste\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode manualJson = objectMapper.readTree(manual);
        assertThat(manualJson.at("/data/confirmado").asBoolean()).isTrue();
    }

    private Long criarPedidoViaDevice(String deviceToken, ProvisionarTenantResponse prov, Long produtoId) throws Exception {
        DeviceCriarPedidoRequest req = new DeviceCriarPedidoRequest();
        req.setClientRequestId("order-" + System.nanoTime());
        req.setMesaId(prov.getMesas() != null && !prov.getMesas().isEmpty() ? prov.getMesas().get(0).getMesaId() : null);
        DeviceCriarPedidoItemRequest it = new DeviceCriarPedidoItemRequest();
        it.setProdutoId(produtoId);
        it.setQuantidade(1);
        req.setItens(List.of(it));

        String resp = mockMvc.perform(post("/device/pedidos")
                        .header("Authorization", deviceAuthorization(deviceToken))
                        .header("Idempotency-Key", "idem-order-pol-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).at("/data/pedidoId").asLong();
    }

    private DispositivoOperacional criarDevicePos(ProvisionarTenantResponse prov) {
        return dispositivoOperacionalRepository
                .findByTenantId(prov.getTenantId(), org.springframework.data.domain.PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseGet(() -> fixtureSupport.createPosDevice(prov, "POS APPYPAY"));
    }

    private Produto criarProdutoBasico(Long tenantId) {
        var tenant = tenantRepository.findById(tenantId).orElseThrow();
        CategoriaProduto cat = new CategoriaProduto();
        cat.setTenant(tenant);
        cat.setNome("Geral");
        cat.setSlug("geral-appy-" + (System.nanoTime() % 100_000));
        cat.setAtivo(true);
        cat = categoriaProdutoRepository.save(cat);

        Produto prod = Produto.builder()
                .codigo("P-APPY-" + (System.nanoTime() % 1_000_000))
                .nome("Produto AppyPay")
                .preco(new BigDecimal("12.00"))
                .categoria(CategoriaProdutoLegacy.OUTROS)
                .ativo(true)
                .disponivel(true)
                .build();
        prod.setTenant(tenant);
        prod.setCategoriaProduto(cat);
        return produtoRepository.save(prod);
    }

    private AbrirTurnoRequest abrirTurnoReq(ProvisionarTenantResponse prov) {
        AbrirTurnoRequest req = new AbrirTurnoRequest();
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());
        req.setTipo(TurnoOperacionalTipo.DIARIO);
        req.setNome("Turno");
        req.setObservacao("Abertura");
        req.setChecklist(List.of(
                boolItem("DEVICE_ONLINE", true),
                boolItem("QR_VISIVEL", true),
                boolItem("CATALOGO_ATUALIZADO", true),
                boolItem("UNIDADE_PRODUCAO_ATIVA", true),
                boolItem("OPERADOR_CONFIRMOU", true)
        ));
        return req;
    }

    private ChecklistItemRespostaRequest boolItem(String codigo, boolean v) {
        ChecklistItemRespostaRequest it = new ChecklistItemRespostaRequest();
        it.setCodigo(codigo);
        it.setValorBoolean(v);
        return it;
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
}
