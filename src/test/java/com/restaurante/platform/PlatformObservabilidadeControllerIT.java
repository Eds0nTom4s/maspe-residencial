package com.restaurante.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.financeiro.enums.PagamentoPollingStatus;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.TipoPagamentoFinanceiro;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.repository.*;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
class PlatformObservabilidadeControllerIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CozinhaRepository cozinhaRepository;
    @Autowired UnidadeProducaoRepository unidadeProducaoRepository;
    @Autowired SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired SubPedidoRepository subPedidoRepository;
    @Autowired UserRepository userRepository;
    @Autowired TurnoOperacionalRepository turnoOperacionalRepository;
    @Autowired DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @Autowired PagamentoGatewayRepository pagamentoGatewayRepository;

    @AfterEach
    void cleanupContext() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "platform-admin")
    void platformAdmin_canSeeGlobalHealth_andAlerts_andNoSensitiveFields() throws Exception {
        Tenant tenantA = criarTenant("Tenant A", "tobsA", "TOA");
        Tenant tenantB = criarTenant("Tenant B", "tobsB", "TOB");

        Instituicao instA = criarInstituicao(tenantA, "InstA", "IAO", "NIF-IAO-1", "+244900100001");
        UnidadeAtendimento uaA = criarUnidadeAtendimento(instA, "UA-A", TipoUnidadeAtendimento.RESTAURANTE);
        Cozinha cozinha = criarCozinha("Cozinha A", TipoCozinha.CENTRAL);
        uaA.adicionarCozinha(cozinha);
        unidadeAtendimentoRepository.saveAndFlush(uaA);

        UnidadeProducao up = criarUnidadeProducao(tenantA, instA, uaA, "UP-A", "UPA");

        User user = criarUser("userA", uaA);
        TurnoOperacional turno = criarTurnoAberto(tenantA, instA, uaA, user);

        // Device offline
        DispositivoOperacional device = criarDevicePos(tenantA, instA, uaA, up, "POS-A", "POSA");
        device.setStatus(DispositivoStatus.ATIVO);
        device.setUltimoHeartbeatEm(LocalDateTime.now().minusMinutes(20));
        dispositivoOperacionalRepository.saveAndFlush(device);

        // Pagamento crítico (MAX_ATTEMPTS_REACHED)
        Pagamento pag = Pagamento.builder()
                .tenant(tenantA)
                .pedido(null)
                .fundoConsumo(null)
                .cliente(null)
                .tipoPagamento(TipoPagamentoFinanceiro.POS_PAGO)
                .metodo(null)
                .amount(new BigDecimal("10.00"))
                .status(StatusPagamentoGateway.PENDENTE)
                .externalReference("REFTOA1")
                .observacoes("test")
                .build();
        pag.setPollingStatus(PagamentoPollingStatus.MAX_ATTEMPTS_REACHED);
        pag.setPollingEnabled(true);
        pagamentoGatewayRepository.saveAndFlush(pag);

        // SubPedido atrasado em produção
        SessaoConsumo sessao = criarSessao(tenantA, instA, uaA);
        Pedido pedido = criarPedido(tenantA, sessao, "PED-OBS-001");
        SubPedido sub = criarSubPedidoAtrasado(tenantA, pedido, uaA, cozinha, up, "PED-OBS-001-1");
        subPedidoRepository.saveAndFlush(sub);

        TenantContextHolder.set(new TenantContext(
                null, null, 999L, Set.of("ROLE_ADMIN"),
                TenantResolutionSource.JWT, true, false
        ));

        String saude = mockMvc.perform(get("/platform/observabilidade/saude")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(saude);
        assertThat(root.at("/data/totalTenants").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(root.at("/data/pagamentosCriticos").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(root.at("/data/devicesOffline").asLong()).isGreaterThanOrEqualTo(1);

        String alertas = mockMvc.perform(get("/platform/observabilidade/alertas")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode aRoot = objectMapper.readTree(alertas);
        assertThat(aRoot.at("/data/content").size()).isGreaterThan(0);

        String devices = mockMvc.perform(get("/platform/observabilidade/tenants/{tenantId}/devices", tenantA.getId())
                        .param("offline", "true")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode dRoot = objectMapper.readTree(devices);
        String payload = dRoot.toString();
        assertThat(payload).doesNotContain("deviceToken");
        assertThat(payload).doesNotContain("activationCode");
        assertThat(payload).doesNotContain("deviceTokenHash");

        String prod = mockMvc.perform(get("/platform/observabilidade/tenants/{tenantId}/producao", tenantA.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode pRoot = objectMapper.readTree(prod);
        assertThat(pRoot.at("/data/atrasados").asLong()).isGreaterThanOrEqualTo(1);

        // Tenant B deve existir e não contaminar tenant A
        String tenants = mockMvc.perform(get("/platform/observabilidade/tenants")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode tRoot = objectMapper.readTree(tenants);
        assertThat(tRoot.at("/data/content").size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @WithMockUser(username = "tenant-owner")
    void tenantUser_isBlockedFromPlatformObservabilidade() throws Exception {
        TenantContextHolder.set(new TenantContext(
                10L, "T10", 111L, Set.of("TENANT_OWNER"),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(get("/platform/observabilidade/saude")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
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

    private Instituicao criarInstituicao(Tenant tenant, String nome, String sigla, String nif, String tel) {
        Instituicao i = Instituicao.builder()
                .tenant(tenant)
                .nome(nome)
                .sigla(sigla)
                .nif(nif)
                .telefoneAutorizacao(tel)
                .ativa(true)
                .build();
        return instituicaoRepository.saveAndFlush(i);
    }

    private UnidadeAtendimento criarUnidadeAtendimento(Instituicao inst, String nome, TipoUnidadeAtendimento tipo) {
        UnidadeAtendimento ua = UnidadeAtendimento.builder()
                .nome(nome)
                .tipo(tipo)
                .ativa(true)
                .instituicao(inst)
                .build();
        return unidadeAtendimentoRepository.saveAndFlush(ua);
    }

    private Cozinha criarCozinha(String nome, TipoCozinha tipo) {
        Cozinha c = new Cozinha();
        c.setNome(nome);
        c.setTipo(tipo);
        c.setAtiva(true);
        return cozinhaRepository.saveAndFlush(c);
    }

    private UnidadeProducao criarUnidadeProducao(Tenant tenant, Instituicao inst, UnidadeAtendimento ua, String nome, String codigo) {
        UnidadeProducao up = new UnidadeProducao();
        up.setTenant(tenant);
        up.setInstituicao(inst);
        up.setUnidadeAtendimento(ua);
        up.setNome(nome);
        up.setCodigo(codigo);
        up.setTipo(UnidadeProducaoTipo.COZINHA);
        up.setAtivo(true);
        up.setOrdem(1);
        return unidadeProducaoRepository.saveAndFlush(up);
    }

    private User criarUser(String username, UnidadeAtendimento ua) {
        User u = User.builder()
                .username(username)
                .password("x")
                .telefone("+244900000" + Math.abs(username.hashCode() % 1000))
                .unidadeAtendimento(ua)
                .roles(Set.of(Role.ROLE_ADMIN))
                .ativo(true)
                .build();
        return userRepository.saveAndFlush(u);
    }

    private TurnoOperacional criarTurnoAberto(Tenant tenant, Instituicao inst, UnidadeAtendimento ua, User user) {
        TurnoOperacional t = new TurnoOperacional();
        t.setTenant(tenant);
        t.setInstituicao(inst);
        t.setUnidadeAtendimento(ua);
        t.setAbertoPor(user);
        t.setNome("Turno Piloto");
        t.setTipo(TurnoOperacionalTipo.DIARIO);
        t.setStatus(TurnoOperacionalStatus.ABERTO);
        t.setAbertoEm(LocalDateTime.now().minusMinutes(10));
        return turnoOperacionalRepository.saveAndFlush(t);
    }

    private DispositivoOperacional criarDevicePos(Tenant tenant, Instituicao inst, UnidadeAtendimento ua, UnidadeProducao up, String nome, String codigo) {
        DispositivoOperacional d = new DispositivoOperacional();
        d.setTenant(tenant);
        d.setInstituicao(inst);
        d.setUnidadeAtendimento(ua);
        d.setUnidadeProducao(up);
        d.setNome(nome);
        d.setCodigo(codigo);
        d.setTipo(DispositivoTipo.POS);
        d.setStatus(DispositivoStatus.ATIVO);
        d.setTokenVersion(1);
        return dispositivoOperacionalRepository.saveAndFlush(d);
    }

    private SessaoConsumo criarSessao(Tenant tenant, Instituicao inst, UnidadeAtendimento ua) {
        SessaoConsumo s = new SessaoConsumo();
        s.setTenant(tenant);
        s.setInstituicao(inst);
        s.setUnidadeAtendimento(ua);
        s.setStatus(StatusSessaoConsumo.ABERTA);
        s.setModoAnonimo(true);
        s.setTipoSessao(TipoSessao.PRE_PAGO);
        return sessaoConsumoRepository.saveAndFlush(s);
    }

    private Pedido criarPedido(Tenant tenant, SessaoConsumo sessao, String numero) {
        Pedido p = Pedido.builder()
                .numero(numero)
                .sessaoConsumo(sessao)
                .status(StatusPedido.EM_ANDAMENTO)
                .statusFinanceiro(StatusFinanceiroPedido.NAO_PAGO)
                .tipoPagamento(TipoPagamentoPedido.POS_PAGO)
                .total(new BigDecimal("20.00"))
                .build();
        p.setTenant(tenant);
        return pedidoRepository.saveAndFlush(p);
    }

    private SubPedido criarSubPedidoAtrasado(Tenant tenant, Pedido pedido, UnidadeAtendimento ua, Cozinha cozinha, UnidadeProducao up, String numero) {
        SubPedido sp = new SubPedido();
        sp.setTenant(tenant);
        sp.setPedido(pedido);
        sp.setUnidadeAtendimento(ua);
        sp.setCozinha(cozinha);
        sp.setUnidadeProducao(up);
        sp.setNumero(numero);
        sp.setStatus(StatusSubPedido.EM_PREPARACAO);
        sp.setIniciadoEm(LocalDateTime.now().minusMinutes(60));
        return sp;
    }
}
