package com.restaurante.operacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import com.restaurante.dto.request.FecharTurnoRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.PublicQrPedidoItemRequest;
import com.restaurante.dto.request.PublicQrPedidoRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.repository.OperationalEventLogRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.SubPedidoRepository;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.restaurante.repository.UserRepository;
import com.restaurante.model.entity.User;
import static com.restaurante.testsupport.MockMvcTenantSupport.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.web-application-type=servlet",
                "consuma.financeiro.pending-payments.block-turno-close-on-critical=true",
                "consuma.financeiro.pending-payments.warning-after-minutes=0",
                "consuma.financeiro.pending-payments.critical-after-minutes=0"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("it-postgres")
class TurnoPreFechoFinanceiroAlertasIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired SubPedidoRepository subPedidoRepository;
    @Autowired SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired PagamentoGatewayRepository pagamentoGatewayRepository;
    @Autowired OperationalEventLogRepository operationalEventLogRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired UserRepository userRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired CozinhaRepository cozinhaRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @Transactional
    @WithMockUser(username = "owner")
    void pre_fecho_includes_financial_alerts_and_blocks_close_when_property_enabled() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("op-turno-fin-alert", "OFA");
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        User ownerUser = userRepository.findById(prov.getOwnerUserId()).orElseThrow();

        // abre turno
        AbrirTurnoRequest openReq = abrirReq(prov.getInstituicaoId(), prov.getUnidadeAtendimentoId());
        String openResp = mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .with(tenantHeaders(prov.getTenantId(), prov.getTenantCode()))
                        .with(authUser(ownerUser, TenantUserRole.TENANT_OWNER.name()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(openReq)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long turnoId = objectMapper.readTree(openResp).at("/data/id").asLong();

        // cria pedido associado ao turno (via QR público)
        ensureCozinhaAtivaForCategoriaCentral(prov.getUnidadeAtendimentoId());
        createProdutoMinimo(prov.getTenantId(), "pre-fecho");
        var prod = produtoRepository.findByTenantIdAndDisponivelTrueAndAtivoTrue(prov.getTenantId(), org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().get(0);
        PublicQrPedidoRequest qrReq = new PublicQrPedidoRequest();
        PublicQrPedidoItemRequest it = new PublicQrPedidoItemRequest();
        it.setProdutoId(prod.getId());
        it.setQuantidade(1);
        qrReq.setItens(List.of(it));
        qrReq.setIdempotencyKey("idem-fin-" + turnoId);
        String publicResp = mockMvc.perform(post("/public/q/" + prov.getQrToken() + "/pedidos")
                        .header("Idempotency-Key", "idem-fin-" + turnoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(qrReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long pedidoId = objectMapper.readTree(publicResp).at("/data/pedidoId").asLong();

        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow();
        assertThat(pedido.getTurnoOperacional()).isNotNull();
        assertThat(pedido.getTurnoOperacional().getId()).isEqualTo(turnoId);

        // remover bloqueios operacionais: marcar subpedidos e pedido como terminais e sessão como encerrada
        List<SubPedido> sps = subPedidoRepository.findByPedidoIdOrderByCreatedAtAsc(pedidoId);
        for (SubPedido sp : sps) {
            sp.setStatus(StatusSubPedido.ENTREGUE);
        }
        subPedidoRepository.saveAllAndFlush(sps);
        pedido.setStatus(StatusPedido.FINALIZADO);
        if (pedido.getSessaoConsumo() != null) {
            pedido.getSessaoConsumo().setStatus(StatusSessaoConsumo.ENCERRADA);
            sessaoConsumoRepository.saveAndFlush(pedido.getSessaoConsumo());
        }
        pedidoRepository.saveAndFlush(pedido);

        // cria pagamento PENDENTE vinculado ao pedido/turno
        Tenant tenant = pedido.getTenant();
        Pagamento pg = Pagamento.builder()
                .tenant(tenant)
                .pedido(pedido)
                .tipoPagamento(com.restaurante.financeiro.enums.TipoPagamentoFinanceiro.POS_PAGO)
                .metodo(MetodoPagamentoAppyPay.REF)
                .amount(pedido.getTotal() != null ? pedido.getTotal() : new BigDecimal("12.00"))
                .status(StatusPagamentoGateway.PENDENTE)
                .externalReference("TESTD" + (System.nanoTime() % 1_000_000))
                .gatewayChargeId("ch_local_" + (System.nanoTime() % 1_000_000))
                .observacoes("Pendente")
                .build();
        pg.setCreatedAt(LocalDateTime.now().minusMinutes(60));
        pagamentoGatewayRepository.saveAndFlush(pg);

        String pre = mockMvc.perform(get("/tenant/operacao/turnos/" + turnoId + "/pre-fecho")
                        .with(tenantHeaders(prov.getTenantId(), prov.getTenantCode()))
                        .with(authUser(ownerUser, TenantUserRole.TENANT_OWNER.name()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var j = objectMapper.readTree(pre);
        assertThat(j.at("/data/alertasFinanceiros/totalPagamentosPendentes").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(j.at("/data/alertasFinanceiros/totalCriticos").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(j.at("/data/podeFechar").asBoolean()).isFalse();

        // fecho normal deve ser bloqueado (409)
        FecharTurnoRequest closeReq = new FecharTurnoRequest();
        closeReq.setForcarFecho(false);
        closeReq.setObservacao("try");
        closeReq.setChecklist(List.of());
        mockMvc.perform(post("/tenant/operacao/turnos/" + turnoId + "/fechar")
                        .with(tenantHeaders(prov.getTenantId(), prov.getTenantCode()))
                        .with(authUser(ownerUser, TenantUserRole.TENANT_OWNER.name()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(closeReq)))
                .andExpect(status().isConflict());

        assertThat(operationalEventLogRepository.findByTenantIdAndEventType(prov.getTenantId(), OperationalEventType.TURNO_FECHO_BLOQUEADO_ALERTA_FINANCEIRO)).isNotEmpty();
    }

    private AbrirTurnoRequest abrirReq(Long instId, Long uaId) {
        AbrirTurnoRequest req = new AbrirTurnoRequest();
        req.setInstituicaoId(instId);
        req.setUnidadeAtendimentoId(uaId);
        req.setTipo(TurnoOperacionalTipo.DIARIO);
        req.setNome("Turno");
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
        ChecklistItemRespostaRequest r = new ChecklistItemRespostaRequest();
        r.setCodigo(codigo);
        r.setValorBoolean(v);
        return r;
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

    private Produto createProdutoMinimo(Long tenantId, String suffix) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        CategoriaProduto cat = categoriaProdutoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(tenantId)
                .stream()
                .findFirst()
                .orElseGet(() -> {
                    CategoriaProduto created = new CategoriaProduto();
                    created.setTenant(tenant);
                    created.setNome("Auto");
                    created.setSlug("auto-" + tenantId);
                    created.setDescricao("Categoria criada automaticamente para testes IT");
                    created.setOrdem(0);
                    created.setAtivo(true);
                    return categoriaProdutoRepository.saveAndFlush(created);
                });

        Produto p = new Produto();
        p.setTenant(tenant);
        p.setCodigo("P-" + suffix);
        p.setNome("Produto IT " + suffix);
        p.setDescricao("Fixture IT");
        p.setPreco(new BigDecimal("10.00"));
        p.setCategoria(CategoriaProdutoLegacy.OUTROS);
        p.setCategoriaProduto(cat);
        p.setDisponivel(true);
        p.setAtivo(true);
        return produtoRepository.saveAndFlush(p);
    }

    private void ensureCozinhaAtivaForCategoriaCentral(Long unidadeAtendimentoId) {
        boolean existsInUnit = !cozinhaRepository.findByUnidadeAtendimentoIdAndTipoAndAtiva(unidadeAtendimentoId, TipoCozinha.CENTRAL, true).isEmpty();
        if (existsInUnit) return;
        boolean existsAny = !cozinhaRepository.findByAtivaAndTipo(true, TipoCozinha.CENTRAL).isEmpty();
        if (existsAny) return;

        Cozinha cozinha = new Cozinha();
        cozinha.setNome("Cozinha Central (IT)");
        cozinha.setTipo(TipoCozinha.CENTRAL);
        cozinha.setAtiva(true);
        cozinha.setDescricao("Fixture IT");
        cozinhaRepository.saveAndFlush(cozinha);
    }
}

