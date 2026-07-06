package com.restaurante.qr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.ProdutoRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.entity.TenantCardapioConfig;
import com.restaurante.model.entity.Tenant;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantCardapioConfigRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.ProdutoService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
@org.springframework.transaction.annotation.Transactional
class ConsumaDemoFreezyHappyPathIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired ProdutoService produtoService;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CozinhaRepository cozinhaRepository;
    @Autowired TenantCardapioConfigRepository tenantCardapioConfigRepository;
    @Autowired TenantRepository tenantRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "tenant-owner")
    void consumaDemoFreezyHappyPath_fromPublicQrToDelivery() throws Exception {
        // 1. Provisiona tenant CONSUMA_PONTO (Demo Freezy)
        TenantContextHolder.set(new TenantContext(null, null, 1L, Set.of("ROLE_ADMIN"), TenantResolutionSource.JWT, true, false));
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
        ProvisionarTenantResponse tenantResp = provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Demo Freezy")
                                .slug("demo-freezy-" + suffix)
                                .tenantCode("DF" + suffix)
                                .tipo(TenantTipo.RESTAURANTE)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("RESTAURANTE_SIMPLES")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder().nome("Demo Freezy").sigla("DF" + suffix.substring(0, Math.min(suffix.length(), 3))).build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder().email("freezy-" + suffix + "@consuma.com").telefone("+24490" + String.format("%06d", Integer.parseInt(suffix))).criarUsuario(true).build())
                        .opcoes(ProvisionarTenantRequest.OpcoesProvisionamento.builder().criarMesas(false).criarQrPorMesa(false).criarQrPrincipal(true).build())
                        .build()
        );

        Tenant tenant = tenantRepository.findById(tenantResp.getTenantId()).orElseThrow();
        tenant.setTemplateCode("CONSUMA_PONTO_V1");
        tenantRepository.saveAndFlush(tenant);

        var ua = unidadeAtendimentoRepository.findById(tenantResp.getUnidadeAtendimentoId()).orElseThrow();
        com.restaurante.model.entity.Cozinha cozinha = new com.restaurante.model.entity.Cozinha();
        cozinha.setNome("Cozinha Freezy");
        cozinha.setTipo(com.restaurante.model.enums.TipoCozinha.CENTRAL);
        cozinha.setAtiva(true);
        cozinha = cozinhaRepository.saveAndFlush(cozinha);
        ua.adicionarCozinha(cozinha);
        unidadeAtendimentoRepository.saveAndFlush(ua);

        // 2. Configura e publica produto
        TenantContextHolder.set(new TenantContext(tenantResp.getTenantId(), tenantResp.getTenantCode(), tenantResp.getOwnerUserId(), Set.of("ROLE_GERENTE"), TenantResolutionSource.JWT, false, false));
        CategoriaProduto geral = categoriaProdutoRepository.findBySlugAndTenantId("geral", tenantResp.getTenantId()).orElseThrow();
        produtoService.criarTenantAware(ProdutoRequest.builder()
                .codigo("AGUA-500")
                .nome("Água 500ml")
                .preco(new BigDecimal("500.00"))
                .categoria(CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA)
                .categoriaProdutoId(geral.getId())
                .disponivel(true)
                .build());
        Produto prod = produtoRepository.findByCodigoAndTenantId("AGUA-500", tenantResp.getTenantId()).orElseThrow();
        
        Tenant tenantParaCardapio = tenantRepository.findById(tenantResp.getTenantId()).orElseThrow();
        TenantCardapioConfig config = tenantCardapioConfigRepository.findByTenantId(tenantResp.getTenantId()).orElseGet(() -> {
            TenantCardapioConfig novo = new TenantCardapioConfig();
            novo.setTenant(tenantParaCardapio);
            return novo;
        });
        config.setCardapioPublicado(true);
        config.setCardapioPublicadoEm(LocalDateTime.now());
        tenantCardapioConfigRepository.saveAndFlush(config);

        String qrToken = tenantResp.getQrToken();

        // 3. Cliente cria pedido via QR
        TenantContextHolder.clear();
        String payloadPedido = """
                {
                  "clienteNome": "Cliente Demo",
                  "itens": [ { "produtoId": %d, "quantidade": 1 } ]
                }
                """.formatted(prod.getId());

        String respCreate = mockMvc.perform(post("/public/q/" + qrToken + "/pedidos")
                        .header("Idempotency-Key", "idem-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadPedido))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode createJson = objectMapper.readTree(respCreate);
        long pedidoId = createJson.at("/data/pedidoId").asLong();
        assertThat(createJson.at("/data/statusOperacional").asText()).isEqualTo("CRIADO");
        assertThat(createJson.at("/data/statusFinanceiro").asText()).isEqualTo("NAO_PAGO");
        assertThat(createJson.at("/data/paymentOrder").isMissingNode() || createJson.at("/data/paymentOrder").isNull()).isTrue();

        // 4. Operador abre turno (obrigatório para aceitar/entregar)
        TenantContextHolder.set(new TenantContext(tenantResp.getTenantId(), tenantResp.getTenantCode(), tenantResp.getOwnerUserId(), Set.of("TENANT_OWNER", "TENANT_OPERATOR", "TENANT_CASHIER"), TenantResolutionSource.JWT, false, false));

        String templatesResponseStr = mockMvc.perform(get("/tenant/operacao/checklists/templates?tipo=ABERTURA"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        com.fasterxml.jackson.databind.JsonNode templatesJson = objectMapper.readTree(templatesResponseStr);
        com.fasterxml.jackson.databind.JsonNode templatesData = templatesJson.at("/data");
        com.fasterxml.jackson.databind.node.ArrayNode checklistArray = objectMapper.createArrayNode();
        if (templatesData.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode template : templatesData) {
                for (com.fasterxml.jackson.databind.JsonNode item : template.at("/itens")) {
                    com.fasterxml.jackson.databind.node.ObjectNode resposta = objectMapper.createObjectNode();
                    resposta.put("codigo", item.get("codigo").asText());
                    resposta.put("valorBoolean", true);
                    checklistArray.add(resposta);
                }
            }
        }

        com.fasterxml.jackson.databind.node.ObjectNode abrirTurnoNode = objectMapper.createObjectNode();
        abrirTurnoNode.put("instituicaoId", ua.getInstituicao().getId());
        abrirTurnoNode.put("unidadeAtendimentoId", ua.getId());
        abrirTurnoNode.put("tipo", "DIARIO");
        abrirTurnoNode.put("nome", "Turno Demo");
        abrirTurnoNode.set("checklist", checklistArray);

        mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(abrirTurnoNode.toString()))
                .andExpect(status().isCreated());

        String respBeforeAccept = mockMvc.perform(get("/tenant/pedidos/" + pedidoId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode beforeAcceptData = objectMapper.readTree(respBeforeAccept).at("/data");
        assertThat(beforeAcceptData.at("/statusOperacional").asText()).isEqualTo("CRIADO");
        assertThat(beforeAcceptData.at("/statusFinanceiro").asText()).isEqualTo("NAO_PAGO");
        assertPaymentOrderAbsent(beforeAcceptData);
        assertAllSubPedidosStatus(beforeAcceptData, "CRIADO");

        // 5. Operador aceita o pedido -> gera Ordem de Pagamento
        String respAceitar = mockMvc.perform(patch("/tenant/pedidos/" + pedidoId + "/aceitar"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode aceitarJson = objectMapper.readTree(respAceitar);
        assertThat(aceitarJson.at("/data/statusOperacional").asText()).isEqualTo("EM_ANDAMENTO");
        assertThat(aceitarJson.at("/data/statusFinanceiro").asText()).isEqualTo("NAO_PAGO");
        assertAllSubPedidosStatus(aceitarJson.at("/data"), "PENDENTE");
        assertThat(aceitarJson.at("/data/paymentOrder/status").asText()).isEqualTo("AGUARDANDO_CONFIRMACAO");
        assertThat(aceitarJson.at("/data/paymentOrder/valor").asDouble()).isEqualTo(500.0);
        assertThat(aceitarJson.at("/data/paymentOrder/expiresAt").asText()).isNotBlank();

        // 6. Cliente vê a Ordem de Pagamento no acompanhamento
        TenantContextHolder.clear();
        String respAcompanhar = mockMvc.perform(get("/public/q/" + qrToken + "/pedidos/" + pedidoId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode acompanharJson = objectMapper.readTree(respAcompanhar);
        assertThat(acompanharJson.at("/data/statusOperacional").asText()).isEqualTo("EM_ANDAMENTO");
        assertThat(acompanharJson.at("/data/paymentOrder/status").asText()).isEqualTo("AGUARDANDO_CONFIRMACAO");
        assertThat(acompanharJson.at("/data/paymentOrder/confirmedBy").isMissingNode()).isTrue();

        // 7. Operador confirma pagamento
        TenantContextHolder.set(new TenantContext(tenantResp.getTenantId(), tenantResp.getTenantCode(), tenantResp.getOwnerUserId(), Set.of("TENANT_OWNER", "TENANT_CASHIER"), TenantResolutionSource.JWT, false, false));
        String payloadConfirm = """
                {
                  "metodoConfirmado": "TPA"
                }
                """;
        String respConfirm = mockMvc.perform(patch("/tenant/pedidos/" + pedidoId + "/payment-order/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadConfirm))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode confirmJson = objectMapper.readTree(respConfirm);
        assertThat(confirmJson.at("/data/status").asText()).isEqualTo("CONFIRMADA");
        assertThat(confirmJson.at("/data/confirmedAt").asText()).isNotBlank();

        // 8. Verifica que allowedActions tem MARK_DELIVERED (pedido PAGO, EM_ANDAMENTO)
        String respGet = mockMvc.perform(get("/tenant/pedidos/" + pedidoId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode getJson = objectMapper.readTree(respGet);
        assertThat(getJson.at("/data/statusFinanceiro").asText()).isEqualTo("PAGO");
        assertThat(getJson.at("/data/statusOperacional").asText()).isEqualTo("EM_ANDAMENTO");
        assertThat(getJson.at("/data/paymentOrder/status").asText()).isEqualTo("CONFIRMADA");
        assertAllSubPedidosStatus(getJson.at("/data"), "PENDENTE");
        assertAllowedAction(getJson.at("/data"), "MARK_DELIVERED");

        // 9. Operador entrega o pedido
        String respEntregar = mockMvc.perform(patch("/tenant/pedidos/" + pedidoId + "/entregar"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode entregarJson = objectMapper.readTree(respEntregar);
        assertThat(entregarJson.at("/data/statusOperacional").asText()).isEqualTo("FINALIZADO");
        assertThat(entregarJson.at("/data/statusFinanceiro").asText()).isEqualTo("PAGO");
        assertAllSubPedidosStatus(entregarJson.at("/data"), "ENTREGUE");
        assertMissingAllowedAction(entregarJson.at("/data"), "MARK_DELIVERED");
        assertMissingAllowedAction(entregarJson.at("/data"), "CONFIRM_PAYMENT");

        // 10. Cliente vê o estado final FINALIZADO
        TenantContextHolder.clear();
        String respAcompanharFinal = mockMvc.perform(get("/public/q/" + qrToken + "/pedidos/" + pedidoId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode acompanharFinalJson = objectMapper.readTree(respAcompanharFinal);
        assertThat(acompanharFinalJson.at("/data/statusOperacional").asText()).isEqualTo("FINALIZADO");
        assertThat(acompanharFinalJson.at("/data/statusFinanceiro").asText()).isEqualTo("PAGO");
        assertThat(acompanharFinalJson.at("/data/paymentOrder/status").asText()).isEqualTo("CONFIRMADA");
    }

    private void assertPaymentOrderAbsent(JsonNode data) {
        JsonNode paymentOrder = data.at("/paymentOrder");
        assertThat(paymentOrder.isMissingNode() || paymentOrder.isNull()).isTrue();
    }

    private void assertAllSubPedidosStatus(JsonNode data, String expectedStatus) {
        JsonNode subPedidos = data.at("/subPedidos");
        assertThat(subPedidos.isArray()).isTrue();
        int count = 0;
        for (JsonNode subPedido : subPedidos) {
            count++;
            assertThat(subPedido.at("/status").asText()).isEqualTo(expectedStatus);
        }
        assertThat(count).isGreaterThan(0);
    }

    private void assertAllowedAction(JsonNode data, String expectedAction) {
        assertThat(allowedActionsContain(data, expectedAction)).isTrue();
    }

    private void assertMissingAllowedAction(JsonNode data, String expectedAction) {
        assertThat(allowedActionsContain(data, expectedAction)).isFalse();
    }

    private boolean allowedActionsContain(JsonNode data, String expectedAction) {
        for (JsonNode action : data.at("/allowedActions")) {
            if (expectedAction.equals(action.asText())) {
                return true;
            }
        }
        return false;
    }
}
