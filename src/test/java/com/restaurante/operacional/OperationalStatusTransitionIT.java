package com.restaurante.operacional;

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
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.OperationalEventLogRepository;
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
import com.restaurante.service.PedidoService;
import com.restaurante.service.QrCodeOperacionalService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import com.restaurante.testsupport.UniqueTestData;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class OperationalStatusTransitionIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CozinhaRepository cozinhaRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired PedidoService pedidoService;
    @Autowired SubPedidoRepository subPedidoRepository;
    @Autowired OperationalEventLogRepository operationalEventLogRepository;
    @Autowired QrCodeOperacionalService qrCodeOperacionalService;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "kitchen-user")
    void kitchen_canMoveSubPedidoToEmPreparacao_andPronto_andEventLogIsCreated() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-1", "OS1");
        pedidoService.confirmar(setup.pedidoId);

        // Actor kitchen no tenant
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), 1001L,
                Set.of(TenantUserRole.TENANT_KITCHEN.name()),
                TenantResolutionSource.JWT, false, false
        ));

        Long subPedidoId = setup.subPedidoId;

        mockMvc.perform(patch("/tenant/producao/subpedidos/" + subPedidoId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_PREPARACAO\",\"motivo\":\"Inicio\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/tenant/producao/subpedidos/" + subPedidoId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PRONTO\",\"motivo\":\"Finalizado\"}"))
                .andExpect(status().isOk());

        var events = operationalEventLogRepository.searchByTenantAndFilters(
                setup.tenant.getId(), setup.pedidoId, subPedidoId, OperationalEventType.SUBPEDIDO_STATUS_CHANGED,
                null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 50)
        );
        assertThat(events.getTotalElements()).isGreaterThanOrEqualTo(2);
        assertThat(events.getContent()).allMatch(e -> e.getStatusNovo() != null);
    }

    @Test
    @WithMockUser(username = "kitchen-user")
    void kitchen_cannotMarkEntregue() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-2", "OS2");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), 1002L,
                Set.of(TenantUserRole.TENANT_KITCHEN.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(patch("/tenant/producao/subpedidos/" + setup.subPedidoId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENTREGUE\",\"motivo\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "finance-user")
    void finance_cannotChangePedidoStatus_orViewOperationalEvents() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-3", "OS3");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), 1003L,
                Set.of(TenantUserRole.TENANT_FINANCE.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELADO\",\"motivo\":\"x\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/tenant/operacional/eventos?pedidoId=" + setup.pedidoId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "cashier-user")
    void cashier_canCancelPedido_onlyIfNotPaid_andEventLogCreated() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-4", "OS4");
        User cashier = criarTenantActor(setup.tenant, TenantUserRole.TENANT_CASHIER, "cashier-os4");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), cashier.getId(),
                Set.of(TenantUserRole.TENANT_CASHIER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELADO\",\"motivo\":\"Cliente desistiu\"}"))
                .andExpect(status().isOk());

        Pedido pedido = pedidoRepository.findByIdAndTenantId(setup.pedidoId, setup.tenant.getId()).orElseThrow();
        assertThat(pedido.getStatus().name()).isEqualTo("CANCELADO");
        assertThat(pedido.getStatusFinanceiro()).isEqualTo(StatusFinanceiroPedido.PENDENTE_PAGAMENTO);

        var events = operationalEventLogRepository.searchByTenantAndFilters(
                setup.tenant.getId(), setup.pedidoId, null, OperationalEventType.PEDIDO_STATUS_CHANGED,
                null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 20)
        );
        assertThat(events.getContent().stream().anyMatch(e -> "CANCELADO".equals(e.getStatusNovo()))).isTrue();
    }

    @Test
    @WithMockUser(username = "operator-user")
    void invalidTransitionReturns409_andFinancialIsNotChanged() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-5", "OS5");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), 1005L,
                Set.of(TenantUserRole.TENANT_OPERATOR.name()),
                TenantResolutionSource.JWT, false, false
        ));

        // PRONTO -> EM_PREPARACAO é inválido; primeiro faça PRONTO de forma direta deve falhar (PENDENTE -> PRONTO inválido)
        mockMvc.perform(patch("/tenant/producao/subpedidos/" + setup.subPedidoId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PRONTO\",\"motivo\":\"pular\"}"))
                .andExpect(status().isConflict());

        Pedido pedido = pedidoRepository.findByIdAndTenantId(setup.pedidoId, setup.tenant.getId()).orElseThrow();
        assertThat(pedido.getStatusFinanceiro()).isEqualTo(StatusFinanceiroPedido.PENDENTE_PAGAMENTO);
    }

    @Test
    @WithMockUser(username = "cashier-user")
    void cashier_canFinalizePedido_whenAllSubPedidosArePronto_andEventLogIsCreated() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-6", "OS6");
        pedidoService.confirmar(setup.pedidoId);

        // Primeiro: cozinha marca subpedido como EM_PREPARACAO -> PRONTO
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), 1006L,
                Set.of(TenantUserRole.TENANT_KITCHEN.name()),
                TenantResolutionSource.JWT, false, false
        ));
        mockMvc.perform(patch("/tenant/producao/subpedidos/" + setup.subPedidoId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_PREPARACAO\",\"motivo\":\"Inicio\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/tenant/producao/subpedidos/" + setup.subPedidoId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PRONTO\",\"motivo\":\"Finalizado\"}"))
                .andExpect(status().isOk());

        // Agora: caixa finaliza pedido (FINALIZADO), entregando subpedidos PRONTO -> ENTREGUE
        User cashier = criarTenantActor(setup.tenant, TenantUserRole.TENANT_CASHIER, "cashier-os6");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), cashier.getId(),
                Set.of(TenantUserRole.TENANT_CASHIER.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FINALIZADO\",\"motivo\":\"Entregue ao cliente\"}"))
                .andExpect(status().isOk());

        Pedido pedido = pedidoRepository.findByIdAndTenantId(setup.pedidoId, setup.tenant.getId()).orElseThrow();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.FINALIZADO);
        assertThat(pedido.getStatusFinanceiro()).isEqualTo(StatusFinanceiroPedido.PENDENTE_PAGAMENTO);

        var sub = subPedidoRepository.findByIdAndTenantId(setup.subPedidoId, setup.tenant.getId()).orElseThrow();
        assertThat(sub.getStatus()).isEqualTo(StatusSubPedido.ENTREGUE);

        var events = operationalEventLogRepository.searchByTenantAndFilters(
                setup.tenant.getId(), setup.pedidoId, null, null,
                null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 50)
        );
        assertThat(events.getContent().stream().anyMatch(e -> e.getEventType() == OperationalEventType.SUBPEDIDO_STATUS_CHANGED && "ENTREGUE".equals(e.getStatusNovo()))).isTrue();
        assertThat(events.getContent().stream().anyMatch(e -> e.getEventType() == OperationalEventType.PEDIDO_STATUS_CHANGED && "FINALIZADO".equals(e.getStatusNovo()))).isTrue();
    }

    @Test
    @WithMockUser(username = "operator-user")
    void operator_canAcceptPedido_withoutChangingPaymentOrStartingProduction() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-7", "OS7");
        User operator = criarTenantActor(setup.tenant, TenantUserRole.TENANT_OPERATOR, "operator-os7");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), operator.getId(),
                Set.of(TenantUserRole.TENANT_OPERATOR.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/aceitar"))
                .andExpect(status().isOk());

        Pedido pedido = pedidoRepository.findByIdAndTenantId(setup.pedidoId, setup.tenant.getId()).orElseThrow();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.EM_ANDAMENTO);
        assertThat(pedido.getStatusFinanceiro()).isEqualTo(StatusFinanceiroPedido.PENDENTE_PAGAMENTO);

        var sub = subPedidoRepository.findByIdAndTenantId(setup.subPedidoId, setup.tenant.getId()).orElseThrow();
        assertThat(sub.getStatus()).isEqualTo(StatusSubPedido.PENDENTE);

        var events = operationalEventLogRepository.searchByTenantAndFilters(
                setup.tenant.getId(), setup.pedidoId, null, null,
                null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 50)
        );
        assertThat(events.getContent().stream().anyMatch(e ->
                e.getEventType() == OperationalEventType.SUBPEDIDO_STATUS_CHANGED
                        && "PENDENTE".equals(e.getStatusNovo()))).isTrue();
        assertThat(events.getContent().stream().anyMatch(e ->
                e.getEventType() == OperationalEventType.PEDIDO_STATUS_CHANGED
                        && "EM_ANDAMENTO".equals(e.getStatusNovo()))).isTrue();
    }

    @Test
    @WithMockUser(username = "operator-user")
    void acceptPedido_isBlockedWhenAlreadyAccepted_andDoesNotChangePayment() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-8", "OS8");
        User operator = criarTenantActor(setup.tenant, TenantUserRole.TENANT_OPERATOR, "operator-os8");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), operator.getId(),
                Set.of(TenantUserRole.TENANT_OPERATOR.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/aceitar"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/aceitar"))
                .andExpect(status().isConflict());

        Pedido pedido = pedidoRepository.findByIdAndTenantId(setup.pedidoId, setup.tenant.getId()).orElseThrow();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.EM_ANDAMENTO);
        assertThat(pedido.getStatusFinanceiro()).isEqualTo(StatusFinanceiroPedido.PENDENTE_PAGAMENTO);
    }

    @Test
    @WithMockUser(username = "operator-user")
    void operator_canRejectCreatedNaoPagoPedido_andEventLogIsCreated() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-9", "OS9");
        Pedido created = pedidoRepository.findByIdAndTenantId(setup.pedidoId, setup.tenant.getId()).orElseThrow();
        created.setStatusFinanceiro(StatusFinanceiroPedido.NAO_PAGO);
        pedidoRepository.saveAndFlush(created);

        User operator = criarTenantActor(setup.tenant, TenantUserRole.TENANT_OPERATOR, "operator-os9");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), operator.getId(),
                Set.of(TenantUserRole.TENANT_OPERATOR.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/rejeitar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Fora do horário de produção\"}"))
                .andExpect(status().isOk());

        Pedido pedido = pedidoRepository.findByIdAndTenantId(setup.pedidoId, setup.tenant.getId()).orElseThrow();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(pedido.getStatusFinanceiro()).isEqualTo(StatusFinanceiroPedido.NAO_PAGO);

        var sub = subPedidoRepository.findByIdAndTenantId(setup.subPedidoId, setup.tenant.getId()).orElseThrow();
        assertThat(sub.getStatus()).isEqualTo(StatusSubPedido.CANCELADO);

        var events = operationalEventLogRepository.searchByTenantAndFilters(
                setup.tenant.getId(), setup.pedidoId, null, OperationalEventType.PEDIDO_STATUS_CHANGED,
                null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 20)
        );
        assertThat(events.getContent().stream().anyMatch(e -> "CANCELADO".equals(e.getStatusNovo()))).isTrue();
    }

    @Test
    @WithMockUser(username = "operator-user")
    void rejectPedido_isBlockedAfterAccept() throws Exception {
        Setup setup = setupTenantAndPedido("op-status-10", "O10");
        Pedido created = pedidoRepository.findByIdAndTenantId(setup.pedidoId, setup.tenant.getId()).orElseThrow();
        created.setStatusFinanceiro(StatusFinanceiroPedido.NAO_PAGO);
        pedidoRepository.saveAndFlush(created);

        User operator = criarTenantActor(setup.tenant, TenantUserRole.TENANT_OPERATOR, "operator-os10");
        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), operator.getId(),
                Set.of(TenantUserRole.TENANT_OPERATOR.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/aceitar"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/tenant/pedidos/" + setup.pedidoId + "/rejeitar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Tarde demais\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "operator-user")
    void acceptPedidoFromOtherTenantIsNotFound() throws Exception {
        Setup tenantA = setupTenantAndPedido("op-status-11a", "11A");
        Setup tenantB = setupTenantAndPedido("op-status-11b", "11B");
        User operatorA = criarTenantActor(tenantA.tenant, TenantUserRole.TENANT_OPERATOR, "operator-os11");
        TenantContextHolder.set(new TenantContext(
                tenantA.tenant.getId(), tenantA.tenant.getTenantCode(), operatorA.getId(),
                Set.of(TenantUserRole.TENANT_OPERATOR.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(patch("/tenant/pedidos/" + tenantB.pedidoId + "/aceitar"))
                .andExpect(status().isNotFound());
    }

    private Setup setupTenantAndPedido(String slug, String tenantCode) throws Exception {
        Tenant tenant = criarTenant("Tenant " + slug, slug, tenantCode);
        Instituicao inst = criarInstituicao(tenant, "Inst " + slug, tenantCode.substring(0, Math.min(3, tenantCode.length())), "NIF-" + tenantCode, "+244900" + Math.abs(slug.hashCode() % 1_000_000));
        UnidadeAtendimento unidade = criarUnidade(inst, "Unidade " + slug, TipoUnidadeAtendimento.RESTAURANTE);
        criarCozinhaVinculada(unidade, "Cozinha " + slug, TipoCozinha.CENTRAL);

        CategoriaProduto cat = criarCategoria(tenant, "Geral", "geral");
        Produto prod = criarProduto(tenant, cat, "P1-" + tenantCode, "Produto " + tenantCode, new BigDecimal("10.00"));
        publicarCardapioForTest(tenant.getId());

        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(
                tenant.getId(), inst.getId(), unidade.getId(), null, QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR " + slug
        );

        String payload = """
                { "itens": [ { "produtoId": %d, "quantidade": 1 } ] }
                """.formatted(prod.getId());

        String resp = mockMvc.perform(post("/public/q/" + qr.getToken() + "/pedidos")
                        .header("Idempotency-Key", "idem-" + slug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        Long pedidoId = json.at("/data/pedidoId").asLong();
        var subs = subPedidoRepository.findByPedidoIdOrderByCreatedAtAsc(pedidoId);
        Long subPedidoId = subs.getFirst().getId();

        return new Setup(tenant, pedidoId, subPedidoId);
    }

    private Tenant criarTenant(String nome, String slug, String tenantCode) {
        Tenant t = new Tenant();
        t.setNome(nome);
        t.setSlug(slug);
        t.setTenantCode(tenantCode);
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }

    private Instituicao criarInstituicao(Tenant tenant, String nome, String sigla, String nif, String telefoneAutorizacao) {
        Instituicao i = new Instituicao();
        i.setTenant(tenant);
        i.setNome(nome);
        i.setSigla(sigla);
        i.setNif(nif);
        i.setTelefoneAutorizacao(telefoneAutorizacao);
        i.setAtiva(true);
        return instituicaoRepository.saveAndFlush(i);
    }

    private UnidadeAtendimento criarUnidade(Instituicao instituicao, String nome, TipoUnidadeAtendimento tipo) {
        UnidadeAtendimento u = new UnidadeAtendimento();
        u.setNome(nome);
        u.setTipo(tipo);
        u.setAtiva(true);
        u.setInstituicao(instituicao);
        return unidadeAtendimentoRepository.saveAndFlush(u);
    }

    private void criarCozinhaVinculada(UnidadeAtendimento unidade, String nome, TipoCozinha tipo) {
        Cozinha c = new Cozinha();
        c.setNome(nome);
        c.setTipo(tipo);
        c.setAtiva(true);
        Cozinha salva = cozinhaRepository.saveAndFlush(c);
        unidade.adicionarCozinha(salva);
        unidadeAtendimentoRepository.saveAndFlush(unidade);
    }

    private CategoriaProduto criarCategoria(Tenant tenant, String nome, String slug) {
        CategoriaProduto c = new CategoriaProduto();
        c.setTenant(tenant);
        c.setNome(nome);
        c.setSlug(slug);
        c.setOrdem(0);
        c.setAtivo(true);
        return categoriaProdutoRepository.saveAndFlush(c);
    }

    private Produto criarProduto(Tenant tenant, CategoriaProduto categoriaProduto, String codigo, String nome, BigDecimal preco) {
        Produto p = new Produto();
        p.setTenant(tenant);
        p.setCodigo(codigo);
        p.setNome(nome);
        p.setPreco(preco);
        p.setAtivo(true);
        p.setCategoriaProduto(categoriaProduto);
        p.setCategoria(com.restaurante.model.enums.CategoriaProdutoLegacy.OUTROS);
        return produtoRepository.saveAndFlush(p);
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

    private record Setup(Tenant tenant, Long pedidoId, Long subPedidoId) {}
}
