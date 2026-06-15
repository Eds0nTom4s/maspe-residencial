package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.OrdemPagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.OrdemPagamentoTipo;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TipoPagamentoPedido;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class CashierHybridPaymentsControllerIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired UserRepository userRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired OrdemPagamentoRepository ordemPagamentoRepository;
    @Autowired PagamentoGatewayRepository pagamentoGatewayRepository;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "cashier")
    void caixaHybridContracts_listFilterConfirmAndKeepOperationalStateSeparated() throws Exception {
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));
        Tenant tenantA = criarTenant("Caixa A", "caixa-a-" + suffix, "CXA" + suffix);
        Instituicao instA = criarInstituicao(tenantA, "Inst Caixa A", "CXA", suffix);
        UnidadeAtendimento unidadeA = criarUnidade(instA, "Balcao A");
        User cashierA = criarUser("cashier-a-" + suffix, "+24493" + String.format("%06d", Integer.parseInt(suffix)));
        vincular(tenantA, cashierA, unidadeA, TenantUserRole.TENANT_CASHIER);
        User kitchenA = criarUser("kitchen-a-" + suffix, "+24495" + String.format("%06d", Integer.parseInt(suffix)));
        vincular(tenantA, kitchenA, unidadeA, TenantUserRole.TENANT_KITCHEN);

        Tenant tenantB = criarTenant("Caixa B", "caixa-b-" + suffix, "CXB" + suffix);
        Instituicao instB = criarInstituicao(tenantB, "Inst Caixa B", "CXB", suffix);
        UnidadeAtendimento unidadeB = criarUnidade(instB, "Balcao B");
        User cashierB = criarUser("cashier-b-" + suffix, "+24494" + String.format("%06d", Integer.parseInt(suffix)));
        vincular(tenantB, cashierB, unidadeB, TenantUserRole.TENANT_CASHIER);

        Pedido cash = criarPedido(tenantA, "PED-CASH-" + suffix, StatusPedido.EM_ANDAMENTO, StatusFinanceiroPedido.NAO_PAGO, "1000.00");
        criarOrdem(tenantA, instA, unidadeA, cash, MetodoPagamentoManual.CASH);
        Pedido tpa = criarPedido(tenantA, "PED-TPA-" + suffix, StatusPedido.CRIADO, StatusFinanceiroPedido.NAO_PAGO, "2000.00");
        criarOrdem(tenantA, instA, unidadeA, tpa, MetodoPagamentoManual.TPA);
        Pedido cancelado = criarPedido(tenantA, "PED-CANCEL-" + suffix, StatusPedido.CANCELADO, StatusFinanceiroPedido.NAO_PAGO, "3000.00");
        criarOrdem(tenantA, instA, unidadeA, cancelado, MetodoPagamentoManual.CASH);
        Pedido entreguePendente = criarPedido(tenantA, "PED-DELIVERED-PENDING-" + suffix, StatusPedido.FINALIZADO, StatusFinanceiroPedido.NAO_PAGO, "5000.00");
        criarOrdem(tenantA, instA, unidadeA, entreguePendente, MetodoPagamentoManual.CASH);
        Pedido crossTenant = criarPedido(tenantB, "PED-CROSS-" + suffix, StatusPedido.CRIADO, StatusFinanceiroPedido.NAO_PAGO, "4000.00");
        criarOrdem(tenantB, instB, unidadeB, crossTenant, MetodoPagamentoManual.CASH);

        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(1);
        LocalDateTime windowEnd = LocalDateTime.now().plusMinutes(1);

        setTenantContext(tenantA, kitchenA, TenantUserRole.TENANT_KITCHEN);
        mockMvc.perform(post("/tenant/caixa/pedidos/{pedidoId}/pagamento/manual-confirmar", cash.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"metodoPagamento":"CASH","valor":1000.00,"referenciaComprovativo":"KITCHEN-BLOCKED"}
                                """))
                .andExpect(status().isForbidden());

        setTenantContext(tenantA, cashierA, TenantUserRole.TENANT_CASHIER);

        String listResp = mockMvc.perform(get("/tenant/caixa/pedidos")
                        .param("page", "0")
                        .param("size", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(listResp).at("/data/content").isArray()).isTrue();

        mockMvc.perform(get("/tenant/caixa/pedidos")
                        .param("paymentStatus", "PENDENTES")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4));

        mockMvc.perform(get("/tenant/caixa/pedidos")
                        .param("paymentStatus", "PENDENTES")
                        .param("operationalStatus", "FINALIZADO")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].pedidoId").value(entreguePendente.getId()))
                .andExpect(jsonPath("$.data.content[0].canConfirmPayment").value(true));

        mockMvc.perform(get("/tenant/caixa/pedidos")
                        .param("operationalStatus", "EM_ANDAMENTO")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].operationalStatus").value("EM_ANDAMENTO"));

        mockMvc.perform(get("/tenant/caixa/pedidos")
                        .param("paymentMethod", "CASH")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));

        mockMvc.perform(get("/tenant/caixa/pedidos")
                        .param("dateFrom", windowStart.toString())
                        .param("dateTo", windowEnd.toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4));

        mockMvc.perform(post("/tenant/caixa/pedidos/{pedidoId}/pagamento/manual-confirmar", cash.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"metodoPagamento":"CASH","valor":1000.00,"referenciaComprovativo":"CASH-IT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusFinanceiro").value("PAGO"))
                .andExpect(jsonPath("$.data.statusOperacional").value("EM_ANDAMENTO"));

        // Idempotente controlado: segunda confirmação não duplica pagamento nem muda operação.
        mockMvc.perform(post("/tenant/caixa/pedidos/{pedidoId}/pagamento/manual-confirmar", cash.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"metodoPagamento":"CASH","valor":1000.00,"referenciaComprovativo":"CASH-IT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusFinanceiro").value("PAGO"))
                .andExpect(jsonPath("$.data.statusOperacional").value("EM_ANDAMENTO"));
        assertThat(pagamentoGatewayRepository.findByPedidoIdOrderByCreatedAtDesc(cash.getId())).hasSize(1);

        mockMvc.perform(post("/tenant/caixa/pedidos/{pedidoId}/pagamento/manual-confirmar", tpa.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"metodoPagamento":"TPA","valor":2000.00,"referenciaComprovativo":"TPA-IT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusFinanceiro").value("PAGO"))
                .andExpect(jsonPath("$.data.statusOperacional").value("CRIADO"));

        mockMvc.perform(post("/tenant/caixa/pedidos/{pedidoId}/pagamento/manual-confirmar", crossTenant.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"metodoPagamento":"CASH","valor":4000.00}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/tenant/caixa/pedidos/{pedidoId}/pagamento/manual-confirmar", cancelado.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"metodoPagamento":"CASH","valor":3000.00}
                                """))
                .andExpect(status().isConflict());

        Pedido valorDiferente = criarPedido(tenantA, "PED-DIFF-" + suffix, StatusPedido.CRIADO, StatusFinanceiroPedido.NAO_PAGO, "1500.00");
        criarOrdem(tenantA, instA, unidadeA, valorDiferente, MetodoPagamentoManual.CASH);
        mockMvc.perform(post("/tenant/caixa/pedidos/{pedidoId}/pagamento/manual-confirmar", valorDiferente.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"metodoPagamento":"CASH","valor":1400.00}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/tenant/caixa/pedidos/{pedidoId}/pagamento/manual-confirmar", valorDiferente.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"metodoPagamento":"CASH","valor":-1}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/tenant/caixa/pedidos")
                        .param("paymentStatus", "PAGOS")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/tenant/caixa/resumo").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantidadePago").value(2))
                .andExpect(jsonPath("$.data.totalPago").value(3000.00));

        mockMvc.perform(get("/tenant/caixa/historico")
                        .param("page", "0")
                        .param("size", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/tenant/caixa/historico")
                        .param("paymentMethod", "CASH")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/tenant/caixa/historico")
                        .param("metodoPagamento", "TPA")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        Pedido cashReloaded = pedidoRepository.findById(cash.getId()).orElseThrow();
        assertThat(cashReloaded.getStatus()).isEqualTo(StatusPedido.EM_ANDAMENTO);
        assertThat(cashReloaded.getStatusFinanceiro()).isEqualTo(StatusFinanceiroPedido.PAGO);
    }

    private void setTenantContext(Tenant tenant, User user, TenantUserRole role) {
        TenantContextHolder.set(new TenantContext(
                tenant.getId(),
                tenant.getTenantCode(),
                user.getId(),
                Set.of(role.name()),
                TenantResolutionSource.JWT,
                false,
                false
        ));
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

    private Instituicao criarInstituicao(Tenant tenant, String nome, String siglaBase, String suffix) {
        Instituicao inst = new Instituicao();
        inst.setTenant(tenant);
        inst.setNome(nome);
        inst.setSigla(UniqueTestData.uniqueInstituicaoSigla(siglaBase));
        inst.setNif(UniqueTestData.uniqueNif("NIF-" + siglaBase + "-" + suffix));
        inst.setTelefoneAutorizacao("+24492" + String.format("%06d", Integer.parseInt(suffix)));
        inst.setAtiva(true);
        return instituicaoRepository.saveAndFlush(inst);
    }

    private UnidadeAtendimento criarUnidade(Instituicao inst, String nome) {
        UnidadeAtendimento unidade = new UnidadeAtendimento();
        unidade.setInstituicao(inst);
        unidade.setNome(nome);
        unidade.setTipo(TipoUnidadeAtendimento.RESTAURANTE);
        unidade.setAtiva(true);
        return unidadeAtendimentoRepository.saveAndFlush(unidade);
    }

    private User criarUser(String username, String telefone) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("{noop}password");
        user.setEmail(username + "@example.com");
        user.setNomeCompleto(username);
        user.setTelefone(telefone);
        user.setRoles(Set.of(Role.ROLE_GERENTE));
        user.setAtivo(true);
        return userRepository.saveAndFlush(user);
    }

    private void vincular(Tenant tenant, User user, UnidadeAtendimento unidade, TenantUserRole role) {
        TenantUser tenantUser = new TenantUser();
        tenantUser.setTenant(tenant);
        tenantUser.setUser(user);
        tenantUser.setUnidadeAtendimentoDefault(unidade);
        tenantUser.setRole(role);
        tenantUser.setEstado(TenantUserEstado.ATIVO);
        tenantUserRepository.saveAndFlush(tenantUser);
    }

    private Pedido criarPedido(Tenant tenant,
                               String numero,
                               StatusPedido status,
                               StatusFinanceiroPedido statusFinanceiro,
                               String total) {
        Pedido pedido = new Pedido();
        pedido.setTenant(tenant);
        pedido.setNumero(numero);
        pedido.setStatus(status);
        pedido.setStatusFinanceiro(statusFinanceiro);
        pedido.setTipoPagamento(TipoPagamentoPedido.POS_PAGO);
        pedido.setTotal(new BigDecimal(total));
        return pedidoRepository.saveAndFlush(pedido);
    }

    private OrdemPagamento criarOrdem(Tenant tenant,
                                      Instituicao inst,
                                      UnidadeAtendimento unidade,
                                      Pedido pedido,
                                      MetodoPagamentoManual metodo) {
        OrdemPagamento ordem = new OrdemPagamento();
        ordem.setTenant(tenant);
        ordem.setInstituicao(inst);
        ordem.setUnidadeAtendimento(unidade);
        ordem.setTipo(OrdemPagamentoTipo.PEDIDO);
        ordem.setStatus(OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO);
        ordem.setMetodoSolicitado(metodo);
        ordem.setValor(pedido.getTotal());
        ordem.setMoeda("AOA");
        ordem.setPedido(pedido);
        ordem.setTokenQr("ord-" + pedido.getNumero().toLowerCase());
        ordem.setCodigoCurto("OP-" + pedido.getId());
        ordem.setCriadoPorOrigem(OperationalOrigem.TENANT_CASHIER);
        return ordemPagamentoRepository.saveAndFlush(ordem);
    }
}
