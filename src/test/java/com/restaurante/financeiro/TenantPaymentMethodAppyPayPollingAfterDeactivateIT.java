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
import com.restaurante.financeiro.paymentmethod.repository.TenantPaymentMethodRepository;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.User;
import com.restaurante.model.entity.DispositivoOperacional;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodStatus;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.security.JwtTokenProvider;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodBootstrapService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class TenantPaymentMethodAppyPayPollingAfterDeactivateIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired TenantPaymentMethodRepository tenantPaymentMethodRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired TenantPaymentMethodBootstrapService bootstrapService;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired CozinhaRepository cozinhaRepository;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockBean AppyPayClient appyPayClient;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void manual_polling_confirms_pending_payment_even_if_appypay_method_deactivated_after_initiation() throws Exception {
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
                .build());

	        ProvisionarTenantResponse prov = provisionTenant("pm-poll-a", "PPA1");

	        // abrir turno com OWNER
	        User ownerForTurno = userRepository.findById(prov.getOwnerUserId()).orElseThrow();
	        Tenant tenantForTurno = tenantRepository.findById(prov.getTenantId()).orElseThrow();
	        String ownerToken = jwtTokenProvider.generateTenantScopedToken(
	                ownerForTurno,
	                tenantForTurno,
	                TenantUserRole.TENANT_OWNER,
	                TenantUserEstado.ATIVO,
	                1,
	                null
	        );
	        mockMvc.perform(post("/tenant/operacao/turnos/abrir")
	                        .header("Authorization", "Bearer " + ownerToken)
	                        .contentType(MediaType.APPLICATION_JSON)
	                        .content(objectMapper.writeValueAsString(abrirTurnoReq(prov))))
	                .andExpect(status().isCreated());

        Produto prod = criarProduto(prov);
        DispositivoOperacional disp = criarDevicePos(prov);

        DevicePrincipal device = new DevicePrincipal(
                disp.getId(),
                disp.getCodigo(),
                prov.getTenantId(),
                prov.getTenantCode(),
                prov.getInstituicaoId(),
                prov.getUnidadeAtendimentoId(),
                null,
                DispositivoTipo.POS,
                DispositivoStatus.ATIVO,
                List.of(DeviceCapability.CREATE_ORDER, DeviceCapability.VIEW_PAYMENTS, DeviceCapability.INITIATE_PAYMENT),
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );

        Long pedidoId = criarPedidoViaDevice(auth, prov, prod.getId());

        DeviceIniciarPagamentoRequest payReq = new DeviceIniciarPagamentoRequest();
        payReq.setClientRequestId("pos-pay-req-1");
        payReq.setMetodoPagamento(MetodoPagamentoAppyPay.REF);
        payReq.setTelefoneCliente("+244900000000");
        payReq.setDescricao("Pagamento POS");

        String payResp = mockMvc.perform(post("/device/pedidos/{id}/pagamentos", pedidoId)
                        .with(authentication(auth))
                        .header("Idempotency-Key", "idem-pay-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long pagamentoId = objectMapper.readTree(payResp).at("/data/pagamentoId").asLong();
        assertThat(pagamentoId).isPositive();

        // desativar APPYPAY após iniciação
        var appy = tenantPaymentMethodRepository.findByTenantIdAndCode(prov.getTenantId(), PaymentMethodCode.APPYPAY).orElseThrow();
        appy.setStatus(PaymentMethodStatus.INACTIVE);
        tenantPaymentMethodRepository.saveAndFlush(appy);

        // FINANCE força polling manual -> deve confirmar mesmo com método desativado
        User owner = userRepository.findById(prov.getOwnerUserId()).orElseThrow();
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        String token = jwtTokenProvider.generateTenantScopedToken(
                owner,
                tenant,
                TenantUserRole.TENANT_FINANCE,
                TenantUserEstado.ATIVO,
                1,
                null
        );
        String manual = mockMvc.perform(post("/tenant/financeiro/pagamentos/{id}/poll", pagamentoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"teste\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode manualJson = objectMapper.readTree(manual);
        assertThat(manualJson.at("/data/confirmado").asBoolean()).isTrue();
    }

    private Long criarPedidoViaDevice(UsernamePasswordAuthenticationToken auth, ProvisionarTenantResponse prov, Long produtoId) throws Exception {
        DeviceCriarPedidoRequest req = new DeviceCriarPedidoRequest();
        req.setClientRequestId("order-" + System.nanoTime());
        req.setMesaId(prov.getMesas() != null && !prov.getMesas().isEmpty() ? prov.getMesas().get(0).getMesaId() : null);
        DeviceCriarPedidoItemRequest it = new DeviceCriarPedidoItemRequest();
        it.setProdutoId(produtoId);
        it.setQuantidade(1);
        req.setItens(List.of(it));

        String resp = mockMvc.perform(post("/device/pedidos")
                        .with(authentication(auth))
                        .header("Idempotency-Key", "idem-order-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).at("/data/pedidoId").asLong();
    }

    private DispositivoOperacional criarDevicePos(ProvisionarTenantResponse prov) {
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        Instituicao inst = instituicaoRepository.findById(prov.getInstituicaoId()).orElseThrow();
        UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(prov.getUnidadeAtendimentoId()).orElseThrow();
        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setCodigo("POS-AP-" + System.nanoTime());
        d.setNome("POS AppyPay");
        d.setTipo(DispositivoTipo.POS);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setTokenVersion(1);
        return dispositivoOperacionalRepository.save(d);
    }

    private Produto criarProduto(ProvisionarTenantResponse prov) {
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        // garantir que existe uma Cozinha CENTRAL activa vinculada à UA via tabela de join
        // Usa repositório para reload da UA com coleção inicializada
        if (cozinhaRepository.findByUnidadeAtendimentoIdAndTipoAndAtiva(
                prov.getUnidadeAtendimentoId(), TipoCozinha.CENTRAL, true).isEmpty()) {
            vincularCozinhaCentral(prov.getUnidadeAtendimentoId());
        }
        CategoriaProduto cat = categoriaProdutoRepository.findBySlugAndTenantId("geral", prov.getTenantId())
                .orElseGet(() -> {
                    CategoriaProduto c = new CategoriaProduto();
                    c.setTenant(tenant);
                    c.setNome("Geral");
                    c.setSlug("geral");
                    c.setOrdem(0);
                    c.setAtivo(true);
                    return categoriaProdutoRepository.save(c);
                });
        Produto p = new Produto();
        p.setTenant(tenant);
        p.setCodigo("PROD-TEST-" + System.nanoTime());
        p.setNome("Produto Teste");
        p.setPreco(new BigDecimal("12.00"));
        p.setCategoria(com.restaurante.model.enums.CategoriaProdutoLegacy.PRATO_PRINCIPAL);
        p.setCategoriaProduto(cat);
        p.setDisponivel(true);
        p.setAtivo(true);
        return produtoRepository.save(p);
    }

    void vincularCozinhaCentral(Long unidadeAtendimentoId) {
        Cozinha coz = new Cozinha();
        coz.setNome("Cozinha Central");
        coz.setTipo(TipoCozinha.CENTRAL);
        coz.setAtiva(true);
        Cozinha salva = cozinhaRepository.saveAndFlush(coz);
        // INSERT directo na tabela de join para evitar LazyInitializationException
        jdbcTemplate.update(
                "INSERT INTO unidade_cozinha (unidade_id, cozinha_id) VALUES (?, ?)",
                unidadeAtendimentoId, salva.getId()
        );
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
        ProvisionarTenantResponse prov = provisioningService.provisionar(
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
        bootstrapService.ensureDefaults(prov.getTenantId());
        return prov;
    }
}
