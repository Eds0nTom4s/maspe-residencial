package com.restaurante.qr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.Tenant;
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
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.QrCodeOperacionalService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import com.restaurante.testsupport.UniqueTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class PublicOrderStateMachineIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CozinhaRepository cozinhaRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired SubPedidoRepository subPedidoRepository;
    @Autowired QrCodeOperacionalService qrCodeOperacionalService;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "tenant-owner")
    void publicOrder_requiresAcceptanceBeforeKds_andTrackingSeparatesFinancialState() throws Exception {
        Tenant tenant = criarTenant("Public State Machine", "public-state-machine", "POSM");
        Instituicao inst = criarInstituicao(tenant, "Inst POSM", "PSM", "NIF-POSM-001", "+244900009001");
        UnidadeAtendimento ua = criarUnidade(inst, "UA POSM", TipoUnidadeAtendimento.RESTAURANTE);
        criarCozinhaVinculada(ua, "Cozinha POSM", TipoCozinha.CENTRAL);
        CategoriaProduto categoria = criarCategoria(tenant, "Pratos POSM", "pratos-posm");
        Produto produto = criarProduto(tenant, categoria, "PRATO-POSM", "Prato POSM", new BigDecimal("30.00"));
        publicarCardapioForTest(tenant.getId());
        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(
                tenant.getId(), inst.getId(), ua.getId(), null, QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR POSM"
        );
        User owner = criarTenantActor(tenant, TenantUserRole.TENANT_OWNER, "owner-posm");

        String createResp = mockMvc.perform(post("/public/q/{token}/pedidos", qr.getToken())
                        .header("Idempotency-Key", "posm-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clienteNome": "Cliente POSM",
                                  "clienteTelefone": "+244950009001",
                                  "itens": [
                                    { "produtoId": %d, "quantidade": 1, "observacao": "Sem pimenta" }
                                  ]
                                }
                                """.formatted(produto.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.statusOperacional").value("CRIADO"))
                .andExpect(jsonPath("$.data.operationalStatus").value("CRIADO"))
                .andExpect(jsonPath("$.data.statusFinanceiro").value("PENDENTE_PAGAMENTO"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDENTE_PAGAMENTO"))
                .andExpect(jsonPath("$.data.currentStep").value("RECEIVED"))
                .andReturn().getResponse().getContentAsString();

        JsonNode created = objectMapper.readTree(createResp);
        long pedidoId = created.at("/data/pedidoId").asLong();
        long subPedidoId = subPedidoRepository.findByPedidoIdOrderByCreatedAtAsc(pedidoId).getFirst().getId();
        long initialVersion = subPedidoRepository.findById(subPedidoId).orElseThrow().getVersion();

        TenantContextHolder.set(new TenantContext(
                tenant.getId(), tenant.getTenantCode(), owner.getId(),
                Set.of(TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(get("/tenant/kds/subpedidos")
                        .param("pedidoId", String.valueOf(pedidoId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.total").value(0));

        mockMvc.perform(patch("/tenant/kds/subpedidos/{id}/iniciar-preparo", subPedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":%d}".formatted(initialVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_ACCEPTED_FOR_PRODUCTION"));

        mockMvc.perform(post("/tenant/pedidos/{id}/aceitar", pedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacao\":\"Aceite operacional\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusOperacional").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.data.statusFinanceiro").value("PENDENTE_PAGAMENTO"));

        String acceptedList = mockMvc.perform(get("/tenant/kds/subpedidos")
                        .param("pedidoId", String.valueOf(pedidoId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.total").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDENTE"))
                .andReturn().getResponse().getContentAsString();
        long acceptedVersion = objectMapper.readTree(acceptedList).at("/data/items/0/version").asLong();

        String trackingAccepted = mockMvc.perform(get("/public/q/{token}/pedidos/{pedidoId}", qr.getToken(), pedidoId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusOperacional").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.data.operationalStatus").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.data.statusFinanceiro").value("PENDENTE_PAGAMENTO"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDENTE_PAGAMENTO"))
                .andExpect(jsonPath("$.data.currentStep").value("ACCEPTED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(trackingAccepted).at("/data/mensagem").asText()).contains("Pagamento");

        mockMvc.perform(patch("/tenant/kds/subpedidos/{id}/iniciar-preparo", subPedidoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":%d}".formatted(acceptedVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EM_PREPARACAO"));

        mockMvc.perform(get("/public/q/{token}/pedidos/{pedidoId}", qr.getToken(), pedidoId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStep").value("IN_PREPARATION"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDENTE_PAGAMENTO"));

        Pedido pedido = pedidoRepository.findByIdAndTenantIdComSessaoConsumo(pedidoId, tenant.getId()).orElseThrow();
        assertThat(pedido.getStatus().name()).isEqualTo("EM_ANDAMENTO");
        assertThat(pedido.getStatusFinanceiro().name()).isEqualTo("PENDENTE_PAGAMENTO");
    }

    private Tenant criarTenant(String nome, String slug, String tenantCode) {
        Tenant tenant = new Tenant();
        tenant.setNome(nome);
        tenant.setSlug(UniqueTestData.uniqueSlug(slug));
        tenant.setTenantCode(UniqueTestData.uniqueTenantCode(tenantCode));
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(tenant);
    }

    private Instituicao criarInstituicao(Tenant tenant, String nome, String sigla, String nif, String telefoneAutorizacao) {
        Instituicao instituicao = new Instituicao();
        instituicao.setTenant(tenant);
        instituicao.setNome(nome);
        instituicao.setSigla(UniqueTestData.uniqueInstituicaoSigla(sigla));
        instituicao.setNif(UniqueTestData.uniqueNif(nif));
        instituicao.setTelefoneAutorizacao(telefoneAutorizacao);
        instituicao.setAtiva(true);
        return instituicaoRepository.saveAndFlush(instituicao);
    }

    private UnidadeAtendimento criarUnidade(Instituicao instituicao, String nome, TipoUnidadeAtendimento tipo) {
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

    private CategoriaProduto criarCategoria(Tenant tenant, String nome, String slug) {
        CategoriaProduto categoria = new CategoriaProduto();
        categoria.setTenant(tenant);
        categoria.setNome(nome);
        categoria.setSlug(UniqueTestData.uniqueSlug(slug));
        categoria.setOrdem(0);
        categoria.setAtivo(true);
        return categoriaProdutoRepository.saveAndFlush(categoria);
    }

    private Produto criarProduto(Tenant tenant, CategoriaProduto categoria, String codigo, String nome, BigDecimal preco) {
        Produto produto = new Produto();
        produto.setTenant(tenant);
        produto.setCodigo(codigo);
        produto.setNome(nome);
        produto.setPreco(preco);
        produto.setAtivo(true);
        produto.setDisponivel(true);
        produto.setCategoria(CategoriaProdutoLegacy.PRATO_PRINCIPAL);
        produto.setCategoriaProduto(categoria);
        return produtoRepository.saveAndFlush(produto);
    }

    private User criarTenantActor(Tenant tenant, TenantUserRole role, String prefix) {
        User user = new User();
        user.setUsername(UniqueTestData.uniqueUsername(prefix));
        user.setPassword("x");
        user.setEmail(UniqueTestData.uniqueEmail(prefix));
        user.setTelefone(UniqueTestData.uniqueTelefone());
        user.setRoles(Set.of(Role.ROLE_GERENTE));
        user.setAtivo(true);
        user = userRepository.saveAndFlush(user);

        TenantUser membership = new TenantUser();
        membership.setTenant(tenant);
        membership.setUser(user);
        membership.setRole(role);
        membership.setEstado(TenantUserEstado.ATIVO);
        tenantUserRepository.saveAndFlush(membership);
        return user;
    }

    private void publicarCardapioForTest(long tenantId) {
        jdbcTemplate.update("""
                insert into tenant_cardapio_configs
                    (tenant_id, cardapio_publicado, cardapio_publicado_em, cardapio_publicado_por_user_id,
                     cardapio_atualizado_em, created_at, updated_at, version)
                values (?, true, now(), null, now(), now(), now(), 0)
                on conflict (tenant_id)
                do update set cardapio_publicado = true,
                              cardapio_publicado_em = now(),
                              cardapio_despublicado_em = null,
                              cardapio_motivo_despublicacao = null,
                              cardapio_atualizado_em = now(),
                              updated_at = now()
                """, tenantId);
    }
}
