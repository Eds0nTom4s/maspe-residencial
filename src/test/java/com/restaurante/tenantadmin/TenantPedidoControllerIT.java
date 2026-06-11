package com.restaurante.tenantadmin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProdutoRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantOperationalModulesConfig;
import com.restaurante.model.entity.TenantSessaoConsumoConfig;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantOperationalModulesConfigRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantSessaoConsumoConfigRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.ProdutoService;
import com.restaurante.service.QrCodeOperacionalService;
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
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
@org.springframework.transaction.annotation.Transactional
class TenantPedidoControllerIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired ProdutoService produtoService;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired com.restaurante.repository.CozinhaRepository cozinhaRepository;
    @Autowired QrCodeOperacionalService qrCodeOperacionalService;
    @Autowired TenantOperationalModulesConfigRepository modulesConfigRepository;
    @Autowired TenantSessaoConsumoConfigRepository sessaoConfigRepository;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "tenant-owner")
    void tenant_canListAndGetPedido_andCannotAccessOtherTenantPedido() throws Exception {
        // provisiona tenant A com QR principal
        TenantContextHolder.set(new TenantContext(null, null, 1L, Set.of("ROLE_ADMIN"), TenantResolutionSource.JWT, true, false));
        String suffixA = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
        ProvisionarTenantResponse a = provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("A")
                                .slug("tenant-a-" + suffixA)
                                .tenantCode("TA" + suffixA)
                                .tipo(TenantTipo.RESTAURANTE)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("RESTAURANTE_SIMPLES")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder().nome("A").sigla(uniqueSigla("TA")).build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder().email("a-" + suffixA + "@a.com").telefone("+24490" + String.format("%06d", Integer.parseInt(suffixA))).criarUsuario(true).build())
                        .opcoes(ProvisionarTenantRequest.OpcoesProvisionamento.builder().criarMesas(false).criarQrPorMesa(false).criarQrPrincipal(true).build())
                        .build()
        );
        var uaA = unidadeAtendimentoRepository.findById(a.getUnidadeAtendimentoId()).orElseThrow();
        com.restaurante.model.entity.Cozinha cozinhaA = new com.restaurante.model.entity.Cozinha();
        cozinhaA.setNome("Cozinha A");
        cozinhaA.setTipo(com.restaurante.model.enums.TipoCozinha.CENTRAL);
        cozinhaA.setAtiva(true);
        cozinhaA = cozinhaRepository.saveAndFlush(cozinhaA);
        uaA.adicionarCozinha(cozinhaA);
        unidadeAtendimentoRepository.saveAndFlush(uaA);

        // cria produto no tenant A
        TenantContextHolder.set(new TenantContext(a.getTenantId(), a.getTenantCode(), a.getOwnerUserId(), Set.of("ROLE_GERENTE"), TenantResolutionSource.JWT, false, false));
        CategoriaProduto geralA = categoriaProdutoRepository.findBySlugAndTenantId("geral", a.getTenantId()).orElseThrow();
        produtoService.criarTenantAware(ProdutoRequest.builder()
                .codigo("AGUA-500")
                .nome("Água 500ml")
                .preco(new BigDecimal("500.00"))
                .categoria(com.restaurante.model.enums.CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA)
                .categoriaProdutoId(geralA.getId())
                .disponivel(true)
                .build());
        Produto prodA = produtoRepository.findByCodigoAndTenantId("AGUA-500", a.getTenantId()).orElseThrow();
        publicarCardapioForTest(a.getTenantId());

        // cria pedido público por QR
        String payloadPedido = """
                {
                  "clienteNome": "Cliente",
                  "itens": [ { "produtoId": %d, "quantidade": 1 } ]
                }
                """.formatted(prodA.getId());

        String respPublic = mockMvc.perform(post("/public/q/" + a.getQrToken() + "/pedidos")
                        .header("Idempotency-Key", "key-idempotente-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadPedido))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode publicJson = objectMapper.readTree(respPublic);
        long pedidoIdA = publicJson.at("/data/pedidoId").asLong();

        // tenant lista pedidos
        String respList = mockMvc.perform(get("/tenant/pedidos").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode listJson = objectMapper.readTree(respList);
        assertThat(listJson.at("/data/content").isArray()).isTrue();
        assertThat(listJson.at("/data/content").toString()).contains("\"id\":" + pedidoIdA);

        // detalhe
        String respDet = mockMvc.perform(get("/tenant/pedidos/" + pedidoIdA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode detJson = objectMapper.readTree(respDet);
        assertThat(detJson.at("/data/id").asLong()).isEqualTo(pedidoIdA);

        // provisiona tenant B e cria pedido, tenant A não acessa
        TenantContextHolder.set(new TenantContext(null, null, 1L, Set.of("ROLE_ADMIN"), TenantResolutionSource.JWT, true, false));
        String suffixB = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
        ProvisionarTenantResponse b = provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("B")
                                .slug("tenant-b-" + suffixB)
                                .tenantCode("TB" + suffixB)
                                .tipo(TenantTipo.RESTAURANTE)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("RESTAURANTE_SIMPLES")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder().nome("B").sigla(uniqueSigla("TB")).build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder().email("b-" + suffixB + "@b.com").telefone("+24490" + String.format("%06d", Integer.parseInt(suffixB))).criarUsuario(true).build())
                        .opcoes(ProvisionarTenantRequest.OpcoesProvisionamento.builder().criarMesas(false).criarQrPorMesa(false).criarQrPrincipal(true).build())
                        .build()
        );
        var uaB = unidadeAtendimentoRepository.findById(b.getUnidadeAtendimentoId()).orElseThrow();
        com.restaurante.model.entity.Cozinha cozinhaB = new com.restaurante.model.entity.Cozinha();
        cozinhaB.setNome("Cozinha B");
        cozinhaB.setTipo(com.restaurante.model.enums.TipoCozinha.CENTRAL);
        cozinhaB.setAtiva(true);
        cozinhaB = cozinhaRepository.saveAndFlush(cozinhaB);
        uaB.adicionarCozinha(cozinhaB);
        unidadeAtendimentoRepository.saveAndFlush(uaB);

        TenantContextHolder.set(new TenantContext(b.getTenantId(), b.getTenantCode(), b.getOwnerUserId(), Set.of("ROLE_GERENTE"), TenantResolutionSource.JWT, false, false));
        CategoriaProduto geralB = categoriaProdutoRepository.findBySlugAndTenantId("geral", b.getTenantId()).orElseThrow();
        produtoService.criarTenantAware(ProdutoRequest.builder()
                .codigo("AGUA-500")
                .nome("Água 500ml")
                .preco(new BigDecimal("600.00"))
                .categoria(com.restaurante.model.enums.CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA)
                .categoriaProdutoId(geralB.getId())
                .disponivel(true)
                .build());
        Produto prodB = produtoRepository.findByCodigoAndTenantId("AGUA-500", b.getTenantId()).orElseThrow();
        publicarCardapioForTest(b.getTenantId());
        String payloadPedidoB = """
                {
                  "clienteNome": "Cliente",
                  "itens": [ { "produtoId": %d, "quantidade": 1 } ]
                }
                """.formatted(prodB.getId());
        String respPublicB = mockMvc.perform(post("/public/q/" + b.getQrToken() + "/pedidos")
                        .header("Idempotency-Key", "key-idempotente-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadPedidoB))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long pedidoIdB = objectMapper.readTree(respPublicB).at("/data/pedidoId").asLong();

        // volta para tenant A e tenta acessar pedido B
        TenantContextHolder.set(new TenantContext(a.getTenantId(), a.getTenantCode(), a.getOwnerUserId(), Set.of("ROLE_GERENTE"), TenantResolutionSource.JWT, false, false));
        mockMvc.perform(get("/tenant/pedidos/" + pedidoIdB))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "tenant-owner")
    void tenantPedidos_listsDirectPontoPedidoWithoutSessaoConsumo() throws Exception {
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
        Tenant tenant = criarTenantDireto("Ponto Tenant", "ponto-tenant-" + suffix, "PONTO" + suffix);
        Instituicao instituicao = criarInstituicaoDireta(
                tenant,
                "Ponto Tenant",
                uniqueSigla("PNT"),
                "NIF-PONTO-" + suffix,
                "+24491" + String.format("%06d", Integer.parseInt(suffix))
        );
        UnidadeAtendimento unidade = criarUnidadeDireta(instituicao, "Balcao Ponto", TipoUnidadeAtendimento.CAFETERIA);
        criarCozinhaVinculada(unidade, "Bar Ponto Tenant", TipoCozinha.BAR_PREP);
        User owner = criarUsuarioTenantDireto(unidade, suffix);
        vincularTenantUser(tenant, owner, unidade, TenantUserRole.TENANT_OWNER);

        CategoriaProduto categoria = criarCategoriaDireta(tenant, "Bebidas Ponto", "bebidas-ponto");
        Produto produto = criarProdutoDireto(tenant, categoria, "PONTO-SUMO-" + suffix, "Sumo Ponto", new BigDecimal("12.50"));
        publicarCardapioForTest(tenant.getId());
        configurarPedidoDiretoSemSessao(tenant);

        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(
                tenant.getId(),
                instituicao.getId(),
                unidade.getId(),
                null,
                QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO,
                "QR Ponto Tenant"
        );

        String payloadPedido = """
                {
                  "clienteNome": "Cliente Ponto",
                  "clienteTelefone": "+244910123456",
                  "metodoPagamento": "CASH",
                  "itens": [ { "produtoId": %d, "quantidade": 1 } ]
                }
                """.formatted(produto.getId());

        String respPublic = mockMvc.perform(post("/public/q/" + qr.getToken() + "/pedidos")
                        .header("Idempotency-Key", "tenant-ponto-direto-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadPedido))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long pedidoId = objectMapper.readTree(respPublic).at("/data/pedidoId").asLong();

        TenantContextHolder.set(new TenantContext(
                tenant.getId(),
                tenant.getTenantCode(),
                owner.getId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT,
                false,
                false
        ));

        String respList = mockMvc.perform(get("/tenant/pedidos").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode listJson = objectMapper.readTree(respList);
        assertThat(listJson.at("/data/content").toString()).contains("\"id\":" + pedidoId);
        assertThat(listJson.at("/data/content").toString()).contains("NAO_PAGO");

        String respDet = mockMvc.perform(get("/tenant/pedidos/" + pedidoId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode detJson = objectMapper.readTree(respDet);
        assertThat(detJson.at("/data/id").asLong()).isEqualTo(pedidoId);
        assertThat(detJson.at("/data/contexto/mesaId").isMissingNode()
                || detJson.at("/data/contexto/mesaId").isNull()).isTrue();
        assertThat(detJson.at("/data/statusFinanceiro").asText()).isEqualTo("NAO_PAGO");
        assertThat(detJson.at("/data/metodoPagamento").asText()).isEqualTo("CASH");
    }

    @Test
    @WithMockUser(username = "tenant-owner")
    void tenantPedidos_acceptsAndConfirmsManualPaymentForDirectPontoPedido() throws Exception {
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
        Tenant tenant = criarTenantDireto("Ponto Workflow", "ponto-workflow-" + suffix, "PWF" + suffix);
        Instituicao instituicao = criarInstituicaoDireta(
                tenant,
                "Ponto Workflow",
                uniqueSigla("PWF"),
                "NIF-PWF-" + suffix,
                "+24493" + String.format("%06d", Integer.parseInt(suffix))
        );
        UnidadeAtendimento unidade = criarUnidadeDireta(instituicao, "Balcao Workflow", TipoUnidadeAtendimento.CAFETERIA);
        criarCozinhaVinculada(unidade, "Bar Workflow", TipoCozinha.BAR_PREP);
        User owner = criarUsuarioTenantDireto(unidade, suffix);
        vincularTenantUser(tenant, owner, unidade, TenantUserRole.TENANT_OWNER);

        CategoriaProduto categoria = criarCategoriaDireta(tenant, "Bebidas Workflow", "bebidas-workflow");
        Produto produto = criarProdutoDireto(tenant, categoria, "PWF-SUMO-" + suffix, "Sumo Workflow", new BigDecimal("15.00"));
        publicarCardapioForTest(tenant.getId());
        configurarPedidoDiretoSemSessao(tenant);

        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(
                tenant.getId(),
                instituicao.getId(),
                unidade.getId(),
                null,
                QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO,
                "QR Workflow"
        );

        String payloadPedido = """
                {
                  "clienteNome": "Cliente Workflow",
                  "clienteTelefone": "+244944455566",
                  "metodoPagamento": "CASH",
                  "itens": [ { "produtoId": %d, "quantidade": 1 } ]
                }
                """.formatted(produto.getId());

        String respPublic = mockMvc.perform(post("/public/q/" + qr.getToken() + "/pedidos")
                        .header("Idempotency-Key", "tenant-ponto-workflow-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadPedido))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long pedidoId = objectMapper.readTree(respPublic).at("/data/pedidoId").asLong();

        TenantContextHolder.set(new TenantContext(
                tenant.getId(),
                tenant.getTenantCode(),
                owner.getId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT,
                false,
                false
        ));

        String aceite = mockMvc.perform(post("/tenant/pedidos/" + pedidoId + "/aceitar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacao\":\"validado no balcão\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode aceiteJson = objectMapper.readTree(aceite);
        assertThat(aceiteJson.at("/data/statusOperacional").asText()).isEqualTo("EM_ANDAMENTO");
        assertThat(aceiteJson.at("/data/aceiteEm").isMissingNode()).isFalse();

        String pagamento = mockMvc.perform(post("/tenant/pedidos/" + pedidoId + "/pagamento/manual-confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"metodoPagamento\":\"CASH\",\"valor\":15.00,\"observacao\":\"recebido no balcão\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode pagamentoJson = objectMapper.readTree(pagamento);
        assertThat(pagamentoJson.at("/data/statusFinanceiro").asText()).isEqualTo("PAGO");
        assertThat(pagamentoJson.at("/data/metodoPagamento").asText()).isEqualTo("CASH");

        String tracking = mockMvc.perform(get("/public/q/" + qr.getToken() + "/pedidos/" + pedidoId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode trackingJson = objectMapper.readTree(tracking);
        assertThat(trackingJson.at("/data/statusOperacional").asText()).isEqualTo("EM_ANDAMENTO");
        assertThat(trackingJson.at("/data/statusFinanceiro").asText()).isEqualTo("PAGO");
        assertThat(trackingJson.at("/data/metodoPagamento").asText()).isEqualTo("CASH");
        assertThat(trackingJson.at("/data/clienteNome").asText()).isEqualTo("Cliente Workflow");
        assertThat(trackingJson.at("/data/clienteTelefoneMascarado").asText()).isNotBlank();
        assertThat(trackingJson.at("/data/mensagem").asText()).contains("Pagamento confirmado");
    }

    @Test
    @WithMockUser(username = "tenant-owner")
    void tenantPedidos_rejectsDirectPontoPedidoWithReasonAndReflectsTracking() throws Exception {
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
        Tenant tenant = criarTenantDireto("Ponto Reject", "ponto-reject-" + suffix, "PRJ" + suffix);
        Instituicao instituicao = criarInstituicaoDireta(
                tenant,
                "Ponto Reject",
                uniqueSigla("PRJ"),
                "NIF-PRJ-" + suffix,
                "+24494" + String.format("%06d", Integer.parseInt(suffix))
        );
        UnidadeAtendimento unidade = criarUnidadeDireta(instituicao, "Balcao Reject", TipoUnidadeAtendimento.CAFETERIA);
        criarCozinhaVinculada(unidade, "Bar Reject", TipoCozinha.BAR_PREP);
        User owner = criarUsuarioTenantDireto(unidade, suffix);
        vincularTenantUser(tenant, owner, unidade, TenantUserRole.TENANT_OWNER);

        CategoriaProduto categoria = criarCategoriaDireta(tenant, "Bebidas Reject", "bebidas-reject");
        Produto produto = criarProdutoDireto(tenant, categoria, "PRJ-SUMO-" + suffix, "Sumo Reject", new BigDecimal("11.00"));
        publicarCardapioForTest(tenant.getId());
        configurarPedidoDiretoSemSessao(tenant);

        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(
                tenant.getId(),
                instituicao.getId(),
                unidade.getId(),
                null,
                QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO,
                "QR Reject"
        );

        String respPublic = mockMvc.perform(post("/public/q/" + qr.getToken() + "/pedidos")
                        .header("Idempotency-Key", "tenant-ponto-reject-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clienteNome": "Cliente Reject",
                                  "clienteTelefone": "+244977788899",
                                  "metodoPagamento": "TPA",
                                  "itens": [ { "produtoId": %d, "quantidade": 1 } ]
                                }
                                """.formatted(produto.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long pedidoId = objectMapper.readTree(respPublic).at("/data/pedidoId").asLong();

        TenantContextHolder.set(new TenantContext(
                tenant.getId(),
                tenant.getTenantCode(),
                owner.getId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT,
                false,
                false
        ));

        String rejeicao = mockMvc.perform(post("/tenant/pedidos/" + pedidoId + "/rejeitar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Produto indisponível\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode rejeicaoJson = objectMapper.readTree(rejeicao);
        assertThat(rejeicaoJson.at("/data/statusOperacional").asText()).isEqualTo("CANCELADO");
        assertThat(rejeicaoJson.at("/data/motivoRejeicao").asText()).contains("Produto indisponível");

        String tracking = mockMvc.perform(get("/public/q/" + qr.getToken() + "/pedidos/" + pedidoId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode trackingJson = objectMapper.readTree(tracking);
        assertThat(trackingJson.at("/data/statusOperacional").asText()).isEqualTo("CANCELADO");
        assertThat(trackingJson.at("/data/motivoRejeicao").asText()).contains("Produto indisponível");
        assertThat(trackingJson.at("/data/mensagem").asText()).contains("Pedido rejeitado");
    }

    private static String uniqueSigla(String prefix) {
        String normalizedPrefix = prefix == null ? "I" : prefix.replaceAll("[^A-Z0-9]", "");
        if (normalizedPrefix.isBlank()) {
            normalizedPrefix = "I";
        }
        if (normalizedPrefix.length() > 3) {
            normalizedPrefix = normalizedPrefix.substring(0, 3);
        }

        long suffix = Math.abs(System.nanoTime() % 10_000_000L);
        return normalizedPrefix + String.format("%07d", suffix);
    }

    private Tenant criarTenantDireto(String nome, String slug, String tenantCode) {
        Tenant tenant = new Tenant();
        tenant.setNome(nome);
        tenant.setSlug(slug);
        tenant.setTenantCode(tenantCode);
        tenant.setTipo(TenantTipo.VENDEDOR_RUA);
        tenant.setTemplateCode("CONSUMA_PONTO");
        tenant.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(tenant);
    }

    private Instituicao criarInstituicaoDireta(Tenant tenant, String nome, String sigla, String nif, String telefoneAutorizacao) {
        Instituicao instituicao = new Instituicao();
        instituicao.setTenant(tenant);
        instituicao.setNome(nome);
        instituicao.setSigla(sigla);
        instituicao.setNif(nif);
        instituicao.setTelefoneAutorizacao(telefoneAutorizacao);
        instituicao.setAtiva(true);
        return instituicaoRepository.saveAndFlush(instituicao);
    }

    private UnidadeAtendimento criarUnidadeDireta(Instituicao instituicao, String nome, TipoUnidadeAtendimento tipo) {
        UnidadeAtendimento unidade = new UnidadeAtendimento();
        unidade.setNome(nome);
        unidade.setTipo(tipo);
        unidade.setAtiva(true);
        unidade.setInstituicao(instituicao);
        return unidadeAtendimentoRepository.saveAndFlush(unidade);
    }

    private void criarCozinhaVinculada(UnidadeAtendimento unidade, String nome, TipoCozinha tipo) {
        Cozinha cozinha = new Cozinha();
        cozinha.setNome(nome);
        cozinha.setTipo(tipo);
        cozinha.setAtiva(true);
        Cozinha salva = cozinhaRepository.saveAndFlush(cozinha);
        unidade.adicionarCozinha(salva);
        unidadeAtendimentoRepository.saveAndFlush(unidade);
    }

    private CategoriaProduto criarCategoriaDireta(Tenant tenant, String nome, String slug) {
        CategoriaProduto categoria = new CategoriaProduto();
        categoria.setTenant(tenant);
        categoria.setNome(nome);
        categoria.setSlug(slug);
        categoria.setOrdem(0);
        categoria.setAtivo(true);
        return categoriaProdutoRepository.saveAndFlush(categoria);
    }

    private Produto criarProdutoDireto(Tenant tenant, CategoriaProduto categoria, String codigo, String nome, BigDecimal preco) {
        Produto produto = new Produto();
        produto.setTenant(tenant);
        produto.setCodigo(codigo);
        produto.setNome(nome);
        produto.setPreco(preco);
        produto.setCategoria(CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA);
        produto.setCategoriaProduto(categoria);
        produto.setDisponivel(true);
        produto.setAtivo(true);
        return produtoRepository.saveAndFlush(produto);
    }

    private User criarUsuarioTenantDireto(UnidadeAtendimento unidade, String suffix) {
        User user = User.builder()
                .username("owner-ponto-" + suffix)
                .password("{noop}test")
                .email("owner-ponto-" + suffix + "@test.local")
                .nomeCompleto("Owner Ponto " + suffix)
                .telefone("+24492" + String.format("%06d", Integer.parseInt(suffix)))
                .unidadeAtendimento(unidade)
                .roles(Set.of(Role.ROLE_GERENTE))
                .ativo(true)
                .build();
        return userRepository.saveAndFlush(user);
    }

    private void vincularTenantUser(Tenant tenant, User user, UnidadeAtendimento unidade, TenantUserRole role) {
        TenantUser membership = new TenantUser();
        membership.setTenant(tenant);
        membership.setUser(user);
        membership.setRole(role);
        membership.setEstado(TenantUserEstado.ATIVO);
        membership.setUnidadeAtendimentoDefault(unidade);
        tenantUserRepository.saveAndFlush(membership);
    }

    private void configurarPedidoDiretoSemSessao(Tenant tenant) {
        TenantOperationalModulesConfig modules = new TenantOperationalModulesConfig();
        modules.setTenant(tenant);
        modules.setSessaoConsumoEnabled(false);
        modules.setPedidoDiretoEnabled(true);
        modules.setMesasEnabled(false);
        modules.setQrMesaEnabled(false);
        modules.setCaixaEnabled(true);
        modules.setKdsEnabled(false);
        modulesConfigRepository.saveAndFlush(modules);

        TenantSessaoConsumoConfig sessaoConfig = new TenantSessaoConsumoConfig();
        sessaoConfig.setTenant(tenant);
        sessaoConfig.setEnabled(false);
        sessaoConfig.setPermitirPrePago(false);
        sessaoConfig.setPermitirPosPago(false);
        sessaoConfig.setPermitirModoAnonimo(true);
        sessaoConfig.setPermitirSessaoSemMesa(true);
        sessaoConfig.setPermitirSessaoComMesa(false);
        sessaoConfigRepository.saveAndFlush(sessaoConfig);
    }
}
