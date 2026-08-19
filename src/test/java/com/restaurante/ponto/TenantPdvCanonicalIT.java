package com.restaurante.ponto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.financeiro.enums.TipoPagamentoFinanceiro;
import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.PedidoOrigem;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalStatus;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.TenantProvisioningService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import com.restaurante.testsupport.UniqueTestData;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"spring.main.web-application-type=servlet", "consuma.operacao.turno-obrigatorio=true"})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
@Transactional
class TenantPdvCanonicalIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired TurnoOperacionalRepository turnoRepository;
    @Autowired CategoriaProdutoRepository categoryRepository;
    @Autowired ProdutoRepository productRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired OrdemPagamentoRepository orderRepository;
    @Autowired PagamentoGatewayRepository paymentRepository;

    @AfterEach
    void clear() { TenantContextHolder.clear(); }

    @Test
    @WithMockUser(username = "ponto-owner")
    void directSaleIsAcceptedBeforePaymentOrderAndReplaysExactlyOnceWithAuthoritativePrice() throws Exception {
        ProvisionarTenantResponse provisioned = provision("pdv-canonical");
        setPontoContext(provisioned);
        TurnoOperacional turno = openTurno(provisioned);
        Produto product = product(provisioned, "Hambúrguer", new BigDecimal("1250.00"));

        String body = objectMapper.writeValueAsString(Map.of(
                "clientRequestId", "pdv-request-1",
                "instituicaoId", provisioned.getInstituicaoId(),
                "unidadeAtendimentoId", provisioned.getUnidadeAtendimentoId(),
                "metodoPagamento", "CASH",
                "itens", java.util.List.of(Map.of("produtoId", product.getId(), "quantidade", 2))));

        String created = mockMvc.perform(post("/tenant/pedidos")
                        .header("Idempotency-Key", "pdv-idem-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.statusOperacional").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.data.statusFinanceiro").value("NAO_PAGO"))
                .andExpect(jsonPath("$.data.pedidoOrigem").value("PDV_INTERNO"))
                .andExpect(jsonPath("$.data.total").value(2500.00))
                .andExpect(jsonPath("$.data.paymentOrder.status").value("AGUARDANDO_CONFIRMACAO"))
                .andReturn().getResponse().getContentAsString();
        long pedidoId = objectMapper.readTree(created).at("/data/id").asLong();

        mockMvc.perform(post("/tenant/pedidos")
                        .header("Idempotency-Key", "pdv-idem-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(pedidoId));

        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.EM_ANDAMENTO);
        assertThat(pedido.getStatusFinanceiro()).isEqualTo(StatusFinanceiroPedido.NAO_PAGO);
        assertThat(pedido.getPedidoOrigem()).isEqualTo(PedidoOrigem.PDV_INTERNO);
        assertThat(pedido.getTotal()).isEqualByComparingTo("2500.00");
        assertThat(pedidoRepository.findByTenantId(provisioned.getTenantId(),
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements()).isEqualTo(1);
        assertThat(orderRepository.findAllByTenantIdAndTurnoOperacionalId(
                provisioned.getTenantId(), turno.getId())).singleElement()
                .satisfies(order -> {
                    assertThat(order.getPedido().getId()).isEqualTo(pedidoId);
                    assertThat(order.getPedido().getStatus()).isEqualTo(StatusPedido.EM_ANDAMENTO);
                });

        String changed = objectMapper.writeValueAsString(Map.of(
                "clientRequestId", "pdv-request-1",
                "instituicaoId", provisioned.getInstituicaoId(),
                "unidadeAtendimentoId", provisioned.getUnidadeAtendimentoId(),
                "metodoPagamento", "CASH",
                "itens", java.util.List.of(Map.of("produtoId", product.getId(), "quantidade", 3))));
        mockMvc.perform(post("/tenant/pedidos").header("Idempotency-Key", "pdv-idem-1")
                        .contentType(MediaType.APPLICATION_JSON).content(changed))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "ponto-owner")
    void crossTenantProductIsInvisibleAndCashConfirmationRequiresOpenMatchingCashSession() throws Exception {
        ProvisionarTenantResponse tenantA = provision("pdv-tenant-a");
        ProvisionarTenantResponse tenantB = provision("pdv-tenant-b");
        setPontoContext(tenantB);
        Produto productB = product(tenantB, "Produto B", new BigDecimal("500.00"));

        setPontoContext(tenantA);
        TurnoOperacional turno = openTurno(tenantA);
        long before = pedidoRepository.count();
        String cross = requestJson(tenantA, productB.getId(), "cross", 1);
        mockMvc.perform(post("/tenant/pedidos").header("Idempotency-Key", "cross-idem")
                        .contentType(MediaType.APPLICATION_JSON).content(cross))
                .andExpect(status().isNotFound());
        assertThat(pedidoRepository.count()).isEqualTo(before);

        Produto productA = product(tenantA, "Produto A", new BigDecimal("500.00"));
        String created = mockMvc.perform(post("/tenant/pedidos").header("Idempotency-Key", "cash-idem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson(tenantA, productA.getId(), "cash", 1)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long pedidoId = objectMapper.readTree(created).at("/data/id").asLong();

        String confirm = objectMapper.writeValueAsString(Map.of(
                "clientRequestId", "confirm-cash-1", "metodoConfirmado", "CASH",
                "valorRecebido", 1000.00, "observacao", "Pagamento controlado"));
        mockMvc.perform(patch("/tenant/pedidos/{id}/payment-order/confirm", pedidoId)
                        .header("Idempotency-Key", "confirm-idem-1")
                        .contentType(MediaType.APPLICATION_JSON).content(confirm))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/tenant/caixa-operador/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instituicaoId", tenantA.getInstituicaoId(),
                                "unidadeAtendimentoId", tenantA.getUnidadeAtendimentoId(),
                                "turnoId", turno.getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.channel").value("WEB_PDV"));

        mockMvc.perform(patch("/tenant/pedidos/{id}/payment-order/confirm", pedidoId)
                .header("Idempotency-Key", "confirm-idem-2")
                .contentType(MediaType.APPLICATION_JSON).content(confirm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentOrder.metodoConfirmado").value("CASH"))
                .andExpect(jsonPath("$.data.paymentOrder.valorRecebido").value(1000.00))
                .andExpect(jsonPath("$.data.paymentOrder.troco").value(500.00));
        mockMvc.perform(patch("/tenant/pedidos/{id}/payment-order/confirm", pedidoId)
                .header("Idempotency-Key", "confirm-idem-2")
                .contentType(MediaType.APPLICATION_JSON).content(confirm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentOrder.status").value("CONFIRMADA"));
        String conflictingConfirmation = objectMapper.writeValueAsString(Map.of(
                "clientRequestId", "confirm-cash-1", "metodoConfirmado", "CASH",
                "valorRecebido", 1100.00, "observacao", "Pagamento controlado"));
        mockMvc.perform(patch("/tenant/pedidos/{id}/payment-order/confirm", pedidoId)
                        .header("Idempotency-Key", "confirm-idem-2")
                        .contentType(MediaType.APPLICATION_JSON).content(conflictingConfirmation))
                .andExpect(status().isConflict());
        assertThat(paymentRepository.findPagamentoConfirmadoPorPedido(
                pedidoId, TipoPagamentoFinanceiro.POS_PAGO)).isPresent();
        assertThat(pedidoRepository.findById(pedidoId).orElseThrow().getStatusFinanceiro())
                .isEqualTo(StatusFinanceiroPedido.PAGO);
    }

    private String requestJson(ProvisionarTenantResponse provisioned, Long productId,
                               String requestId, int quantity) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "clientRequestId", requestId,
                "instituicaoId", provisioned.getInstituicaoId(),
                "unidadeAtendimentoId", provisioned.getUnidadeAtendimentoId(),
                "metodoPagamento", "CASH",
                "itens", java.util.List.of(Map.of("produtoId", productId, "quantidade", quantity))));
    }

    private ProvisionarTenantResponse provision(String label) {
        TenantContextHolder.set(new TenantContext(null, null, 1L, Set.of(Role.ROLE_ADMIN.name()),
                TenantResolutionSource.JWT, true, false));
        return provisioningService.provisionar(ProvisionarTenantRequest.builder()
                .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                        .nome("Ponto " + label).slug(UniqueTestData.uniqueSlug(label))
                        .tenantCode(UniqueTestData.uniqueTenantCode("PDV")).tipo(TenantTipo.LOJA).build())
                .planoCodigo("PILOTO").templateCodigo("VENDEDOR_RUA")
                .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                        .nome("Instituição " + label)
                        .sigla(UniqueTestData.uniqueInstituicaoSigla("PDV")).build())
                .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                        .email(UniqueTestData.uniqueEmail(label)).telefone(UniqueTestData.uniqueTelefone())
                        .criarUsuario(true).build())
                .opcoes(ProvisionarTenantRequest.OpcoesProvisionamento.builder()
                        .criarMesas(false).criarQrPorMesa(false).criarQrPrincipal(false).build())
                .build());
    }

    private void setPontoContext(ProvisionarTenantResponse provisioned) {
        Tenant tenant = tenantRepository.findById(provisioned.getTenantId()).orElseThrow();
        tenant.setTemplateCode("CONSUMA_PONTO_V1");
        tenant.setTemplateVersion(1);
        tenantRepository.saveAndFlush(tenant);
        TenantContextHolder.set(new TenantContext(provisioned.getTenantId(), provisioned.getTenantCode(),
                provisioned.getOwnerUserId(), Set.of(TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false));
    }

    private TurnoOperacional openTurno(ProvisionarTenantResponse provisioned) {
        Tenant tenant = tenantRepository.findById(provisioned.getTenantId()).orElseThrow();
        User owner = userRepository.findById(provisioned.getOwnerUserId()).orElseThrow();
        TurnoOperacional turno = new TurnoOperacional();
        turno.setTenant(tenant);
        turno.setInstituicao(instituicaoRepository.findById(provisioned.getInstituicaoId()).orElseThrow());
        turno.setUnidadeAtendimento(unidadeAtendimentoRepository
                .findById(provisioned.getUnidadeAtendimentoId()).orElseThrow());
        turno.setAbertoPor(owner);
        turno.setStatus(TurnoOperacionalStatus.ABERTO);
        turno.setTipo(TurnoOperacionalTipo.DIARIO);
        turno.setNome("Turno PDV");
        turno.setAbertoEm(LocalDateTime.now());
        return turnoRepository.saveAndFlush(turno);
    }

    private Produto product(ProvisionarTenantResponse provisioned, String name, BigDecimal price) {
        Tenant tenant = tenantRepository.findById(provisioned.getTenantId()).orElseThrow();
        CategoriaProduto category = new CategoriaProduto();
        category.setTenant(tenant);
        category.setNome("Categoria " + name);
        category.setSlug("pdv-category-" + UUID.randomUUID());
        category.setOrdem(0);
        category.setAtivo(true);
        category = categoryRepository.saveAndFlush(category);
        Produto product = new Produto();
        product.setTenant(tenant);
        product.setCategoriaProduto(category);
        product.setCategoria(CategoriaProdutoLegacy.OUTROS);
        product.setCodigo("PDV-" + UUID.randomUUID());
        product.setNome(name);
        product.setPreco(price);
        product.setAtivo(true);
        product.setDisponivel(true);
        return productRepository.saveAndFlush(product);
    }
}
