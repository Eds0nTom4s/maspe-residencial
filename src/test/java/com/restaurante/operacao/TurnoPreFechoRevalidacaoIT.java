package com.restaurante.operacao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.FecharTurnoRequest;
import com.restaurante.dto.request.ProvisionarTenantRequest;
import com.restaurante.dto.request.PublicQrPedidoItemRequest;
import com.restaurante.dto.request.PublicQrPedidoRequest;
import com.restaurante.dto.response.ProvisionarTenantResponse;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.entity.TenantCardapioConfig;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TipoSessao;
import com.restaurante.model.enums.TurnoOperacionalTipo;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.repository.TenantCardapioConfigRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.SessaoConsumoAutoClosureService;
import com.restaurante.service.TenantOperationalModulesService;
import com.restaurante.service.TenantProvisioningService;
import com.restaurante.service.TenantSessaoConsumoConfigService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.User;
import com.restaurante.repository.UserRepository;
import com.restaurante.dto.request.ChecklistItemRespostaRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
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

import static com.restaurante.testsupport.MockMvcTenantSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PROMPT-BACKEND-CONSUMA-TURNO-PRE-FECHO-REVALIDACAO-001
 *
 * Revalida o pre-fecho de turno apos o auto-fecho de sessao PONTO:
 *
 * Antes: pedido finalizado -> sessao continuava ABERTA -> bloqueava turno.
 * Depois: pedido finalizado -> SessaoConsumoAutoClosureService encerra sessao ->
 *         pre-fecho nao conta sessao encerrada -> turno fica desbloqueado.
 *
 * Cobre:
 * - Cenario A: sessao ABERTA bloqueia pre-fecho
 * - Cenario B: apos auto-fecho, pre-fecho desbloqueia
 * - Cenario C: AGUARDANDO_PAGAMENTO tambem bloqueia (sessao ainda ativa)
 * - Cenario D: sessao ENCERRADA nao bloqueia
 * - Cenario E: sessao REST nao e auto-encerrada
 * - Cenario F: fecho normal possivel apos resolucao de pendencias
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.main.web-application-type=servlet"
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("it-postgres")
@DisplayName("TurnoPreFechoRevalidacao - pre-fecho apos auto-fecho de sessao PONTO")
@WithMockUser(username = "tenant-owner")
class TurnoPreFechoRevalidacaoIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired SubPedidoRepository subPedidoRepository;
    @Autowired SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired CozinhaRepository cozinhaRepository;
    @Autowired TenantCardapioConfigRepository tenantCardapioConfigRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired UserRepository userRepository;
    @Autowired SessaoConsumoAutoClosureService sessaoConsumoAutoClosureService;
    @Autowired TenantOperationalModulesService tenantOperationalModulesService;
    @Autowired TenantSessaoConsumoConfigService tenantSessaoConsumoConfigService;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    // =======================================================================
    // CENARIO A - sessao ABERTA com pedido pendente bloqueia pre-fecho
    // =======================================================================
    @Test
    @Transactional
    @DisplayName("A. Sessao PONTO ABERTA com pedido pendente -> bloqueia pre-fecho")
    void cenarioA_sessaoAbertaPedidoPendente_bloqueiaPreFecho() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pre-fecho-A");
        User owner = userOf(prov);
        long turnoId = abrirTurno(prov, owner);

        // Criar pedido via QR -> sessao fica ABERTA
        publicarCardapio(prov);
        long pedidoId = criarPedidoQr(prov, turnoId);
        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow();
        assertThat(pedido.getSessaoConsumo()).isNotNull();
        assertThat(pedido.getSessaoConsumo().getStatus()).isEqualTo(StatusSessaoConsumo.ABERTA);

        // Pre-fecho deve bloquear
        var pre = getPreFecho(prov, owner, turnoId);
        long sessoesAbertas = pre.at("/data/sessoesAbertas").asLong();
        boolean podeFechar = pre.at("/data/podeFechar").asBoolean();
        assertThat(sessoesAbertas).isGreaterThan(0);
        assertThat(podeFechar).isFalse();
        assertThat(pre.at("/data/bloqueios").toString()).contains("ABERTA");
    }

    // =======================================================================
    // CENARIO B - apos auto-fecho de sessao PONTO, pre-fecho desbloqueia
    // =======================================================================
    @Test
    @Transactional
    @DisplayName("B. Apos auto-fecho da sessao PONTO, pre-fecho nao acusa sessao como aberta")
    void cenarioB_aposAutoFecho_preFechoDesbloqueado() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pre-fecho-B");
        User owner = userOf(prov);
        long turnoId = abrirTurno(prov, owner);

        publicarCardapio(prov);
        long pedidoId = criarPedidoQr(prov, turnoId);
        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow();
        SessaoConsumo sessao = pedido.getSessaoConsumo();
        assertThat(sessao).isNotNull();

        // Resolver o pedido e acionar o auto-fecho real aprovado para CONSUMA PONTO.
        List<SubPedido> sps = subPedidoRepository.findByPedidoIdOrderByCreatedAtAsc(pedidoId);
        sps.forEach(s -> s.setStatus(StatusSubPedido.ENTREGUE));
        subPedidoRepository.saveAllAndFlush(sps);
        pedido.setStatus(StatusPedido.FINALIZADO);
        pedido.setStatusFinanceiro(StatusFinanceiroPedido.PAGO);
        pedidoRepository.saveAndFlush(pedido);
        sessaoConsumoAutoClosureService.tryAutoCloseSessaoConsumo(sessao.getId());
        sessaoConsumoRepository.flush();

        SessaoConsumo sessaoAtualizada = sessaoConsumoRepository.findById(sessao.getId()).orElseThrow();
        assertThat(sessaoAtualizada.getStatus()).isEqualTo(StatusSessaoConsumo.ENCERRADA);

        // Pre-fecho nao deve contabilizar sessao ENCERRADA
        var pre = getPreFecho(prov, owner, turnoId);
        long sessoesAbertas = pre.at("/data/sessoesAbertas").asLong();
        assertThat(sessoesAbertas).isZero();
        // Sem pedido pendente e sem sessao aberta -> podeFechar = true
        assertThat(pre.at("/data/podeFechar").asBoolean()).isTrue();
    }

    @Test
    @Transactional
    @DisplayName("B2. Fecho forcado tenta auto-fecho PONTO elegivel antes de fechar turno")
    void cenarioB2_fechoForcadoTentaAutoFechoPontoAntesDeFechar() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pre-fecho-B2");
        User owner = userOf(prov);
        long turnoId = abrirTurno(prov, owner);

        publicarCardapio(prov);
        long pedidoId = criarPedidoQr(prov, turnoId);
        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow();
        SessaoConsumo sessao = pedido.getSessaoConsumo();
        assertThat(sessao).isNotNull();
        assertThat(sessao.getStatus()).isEqualTo(StatusSessaoConsumo.ABERTA);

        List<SubPedido> sps = subPedidoRepository.findByPedidoIdOrderByCreatedAtAsc(pedidoId);
        sps.forEach(s -> s.setStatus(StatusSubPedido.ENTREGUE));
        subPedidoRepository.saveAllAndFlush(sps);
        pedido.setStatus(StatusPedido.FINALIZADO);
        pedido.setStatusFinanceiro(StatusFinanceiroPedido.PAGO);
        pedidoRepository.saveAndFlush(pedido);

        var preAntes = getPreFecho(prov, owner, turnoId);
        assertThat(preAntes.at("/data/sessoesAbertas").asLong()).isGreaterThan(0);

        setTenantCtx(prov);
        FecharTurnoRequest req = fecharReq(true);
        req.setMotivoFechoForcado("Fecho forcado apos resolucao PONTO elegivel");
        String closeResp = mockMvc.perform(post("/tenant/operacao/turnos/" + turnoId + "/fechar")
                        .with(tenantHeaders(prov.getTenantId(), prov.getTenantCode()))
                        .with(authUser(owner, TenantUserRole.TENANT_OWNER.name()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        SessaoConsumo sessaoAtualizada = sessaoConsumoRepository.findById(sessao.getId()).orElseThrow();
        assertThat(sessaoAtualizada.getStatus()).isEqualTo(StatusSessaoConsumo.ENCERRADA);

        var root = objectMapper.readTree(objectMapper.readTree(closeResp).at("/data/resumoJson").asText());
        assertThat(root.at("/sessoesAbertas").asLong()).isZero();
        assertThat(root.at("/fechoForcadoPolicy/sessoesAutoFechoTentadas").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(root.at("/fechoForcadoPolicy/sessoesAutoFechadas").asLong()).isGreaterThanOrEqualTo(1);
    }

    // =======================================================================
    // CENARIO C - sessao AGUARDANDO_PAGAMENTO tambem bloqueia pre-fecho
    // =======================================================================
    @Test
    @Transactional
    @DisplayName("C. Sessao AGUARDANDO_PAGAMENTO tambem bloqueia o pre-fecho")
    void cenarioC_sessaoAguardandoPagamento_bloqueiaPreFecho() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pre-fecho-C");
        User owner = userOf(prov);
        long turnoId = abrirTurno(prov, owner);

        publicarCardapio(prov);
        long pedidoId = criarPedidoQr(prov, turnoId);
        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow();
        SessaoConsumo sessao = pedido.getSessaoConsumo();
        assertThat(sessao).isNotNull();

        // Transicionar sessao para AGUARDANDO_PAGAMENTO manualmente
        sessao.setStatus(StatusSessaoConsumo.AGUARDANDO_PAGAMENTO);
        sessaoConsumoRepository.saveAndFlush(sessao);

        // Pre-fecho deve contar essa sessao como aberta (operacionalmente ativa)
        var pre = getPreFecho(prov, owner, turnoId);
        long sessoesAbertas = pre.at("/data/sessoesAbertas").asLong();
        assertThat(sessoesAbertas).isGreaterThan(0);
        assertThat(pre.at("/data/podeFechar").asBoolean()).isFalse();
        assertThat(pre.at("/data/bloqueios").toString()).contains("AGUARDANDO_PAGAMENTO");
    }

    // =======================================================================
    // CENARIO D - sessao ENCERRADA nao entra no contador
    // =======================================================================
    @Test
    @Transactional
    @DisplayName("D. Sessao ENCERRADA nao e contada no pre-fecho")
    void cenarioD_sessaoEncerrada_naoContaNoPrefecho() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pre-fecho-D");
        User owner = userOf(prov);
        long turnoId = abrirTurno(prov, owner);

        publicarCardapio(prov);
        long pedidoId = criarPedidoQr(prov, turnoId);
        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow();
        SessaoConsumo sessao = pedido.getSessaoConsumo();

        // Encerrar sessao e terminalizar pedido
        sessao.encerrar();
        sessaoConsumoRepository.saveAndFlush(sessao);

        List<SubPedido> sps = subPedidoRepository.findByPedidoIdOrderByCreatedAtAsc(pedidoId);
        sps.forEach(s -> s.setStatus(StatusSubPedido.ENTREGUE));
        subPedidoRepository.saveAllAndFlush(sps);
        pedido.setStatus(StatusPedido.FINALIZADO);
        pedidoRepository.saveAndFlush(pedido);

        var pre = getPreFecho(prov, owner, turnoId);
        assertThat(pre.at("/data/sessoesAbertas").asLong()).isZero();
    }

    // =======================================================================
    // CENARIO E - sessao EXPIRADA nao entra no contador
    // =======================================================================
    @Test
    @Transactional
    @DisplayName("E. Sessao EXPIRADA nao e contada no pre-fecho")
    void cenarioE_sessaoExpirada_naoContaNoPrefecho() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pre-fecho-E");
        User owner = userOf(prov);
        long turnoId = abrirTurno(prov, owner);

        publicarCardapio(prov);
        long pedidoId = criarPedidoQr(prov, turnoId);
        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow();
        SessaoConsumo sessao = pedido.getSessaoConsumo();

        // Expirar sessao
        sessao.setStatus(StatusSessaoConsumo.EXPIRADA);
        sessaoConsumoRepository.saveAndFlush(sessao);

        var pre = getPreFecho(prov, owner, turnoId);
        assertThat(pre.at("/data/sessoesAbertas").asLong()).isZero();
    }

    // =======================================================================
    // CENARIO F - fecho normal possivel quando nao ha pendencias
    // =======================================================================
    @Test
    @Transactional
    @DisplayName("F. Fecho normal do turno possivel quando sessao e pedidos estao resolvidos")
    void cenarioF_fechoNormalPossivel() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pre-fecho-F");
        User owner = userOf(prov);
        long turnoId = abrirTurno(prov, owner);

        publicarCardapio(prov);
        long pedidoId = criarPedidoQr(prov, turnoId);
        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow();
        SessaoConsumo sessao = pedido.getSessaoConsumo();

        // Encerrar sessao + terminalizar pedido
        sessao.encerrar();
        sessaoConsumoRepository.saveAndFlush(sessao);

        List<SubPedido> sps = subPedidoRepository.findByPedidoIdOrderByCreatedAtAsc(pedidoId);
        sps.forEach(s -> s.setStatus(StatusSubPedido.ENTREGUE));
        subPedidoRepository.saveAllAndFlush(sps);
        pedido.setStatus(StatusPedido.FINALIZADO);
        pedidoRepository.saveAndFlush(pedido);

        // Pre-fecho deve permitir fechar
        var pre = getPreFecho(prov, owner, turnoId);
        assertThat(pre.at("/data/podeFechar").asBoolean()).isTrue();
        assertThat(pre.at("/data/sessoesAbertas").asLong()).isZero();
        assertThat(pre.at("/data/bloqueios").size()).isZero();
    }

    // =======================================================================
    // CENARIO G - pedido nao terminal bloqueia mesmo sem sessao aberta
    // =======================================================================
    @Test
    @Transactional
    @DisplayName("G. Pedido nao terminal bloqueia pre-fecho (bloqueio legitimo preservado)")
    void cenarioG_pedidoNaoTerminal_bloqueia() throws Exception {
        ProvisionarTenantResponse prov = provisionTenant("pre-fecho-G");
        User owner = userOf(prov);
        long turnoId = abrirTurno(prov, owner);

        publicarCardapio(prov);
        long pedidoId = criarPedidoQr(prov, turnoId);
        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow();

        // Encerrar sessao (nao bloqueia por sessao), mas deixar pedido CRIADO
        SessaoConsumo sessao = pedido.getSessaoConsumo();
        if (sessao != null) {
            sessao.encerrar();
            sessaoConsumoRepository.saveAndFlush(sessao);
        }
        // Pedido continua em CRIADO (estado default apos QR)

        var pre = getPreFecho(prov, owner, turnoId);
        assertThat(pre.at("/data/sessoesAbertas").asLong()).isZero();
        assertThat(pre.at("/data/podeFechar").asBoolean()).isFalse();
        assertThat(pre.at("/data/bloqueios").toString()).contains("pedidos");
    }

    // =======================================================================
    // Helpers
    // =======================================================================

    private ProvisionarTenantResponse provisionTenant(String slug) {
        TenantContextHolder.set(new TenantContext(
                null, null, 1L, Set.of(Role.ROLE_ADMIN.name()),
                TenantResolutionSource.JWT, true, false
        ));
        // Slug unico por execucao para evitar colisao entre cenarios
        String uniqueSlug = slug + "-" + (System.nanoTime() % 100000L);
        String code = uniqueSlug.replaceAll("[^a-zA-Z0-9]", "")
                .substring(0, Math.min(5, uniqueSlug.replaceAll("[^a-zA-Z0-9]", "").length()))
                .toUpperCase();
        String phone = "+244900" + Math.abs(uniqueSlug.hashCode() % 1_000_000);
        ProvisionarTenantResponse prov = provisioningService.provisionar(
                ProvisionarTenantRequest.builder()
                        .tenant(ProvisionarTenantRequest.TenantInfo.builder()
                                .nome("Tenant " + uniqueSlug)
                                .slug(uniqueSlug)
                                .tenantCode(code)
                                .tipo(TenantTipo.VENDEDOR_RUA)
                                .build())
                        .planoCodigo("PILOTO")
                        .templateCodigo("RESTAURANTE_SIMPLES")
                        .instituicao(ProvisionarTenantRequest.InstituicaoInfo.builder()
                                .nome("Inst " + uniqueSlug)
                                .sigla(code)
                                .build())
                        .responsavel(ProvisionarTenantRequest.ResponsavelInfo.builder()
                                .email(uniqueSlug.toLowerCase().replaceAll("[^a-z0-9]", "") + "@owner.com")
                                .telefone(phone)
                                .criarUsuario(true)
                                .build())
                        .build()
        );
        // Setar templateCode CONSUMA_PONTO_V1 diretamente - padrao Demo Freezy
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        tenant.setTemplateCode("CONSUMA_PONTO_V1");
        tenantRepository.saveAndFlush(tenant);
        tenantOperationalModulesService.upsertForTemplate(
                tenant,
                true,
                true,
                false,
                false,
                true,
                false
        );
        tenantSessaoConsumoConfigService.upsertForTemplate(
                tenant,
                true,
                true,
                true,
                TipoSessao.POS_PAGO,
                false,
                true,
                true,
                false,
                12
        );
        return prov;
    }

    private User userOf(ProvisionarTenantResponse prov) {
        return userRepository.findById(prov.getOwnerUserId()).orElseThrow();
    }

    private void setTenantCtx(ProvisionarTenantResponse prov) {
        TenantContextHolder.set(new TenantContext(
                prov.getTenantId(), prov.getTenantCode(), prov.getOwnerUserId(),
                Set.of(Role.ROLE_GERENTE.name(), TenantUserRole.TENANT_OWNER.name()),
                TenantResolutionSource.JWT, false, false
        ));
    }

    private long abrirTurno(ProvisionarTenantResponse prov, User owner) throws Exception {
        setTenantCtx(prov);
        AbrirTurnoRequest req = new AbrirTurnoRequest();
        req.setInstituicaoId(prov.getInstituicaoId());
        req.setUnidadeAtendimentoId(prov.getUnidadeAtendimentoId());
        req.setTipo(TurnoOperacionalTipo.DIARIO);
        req.setNome("Turno Pre-Fecho IT");
        req.setChecklist(List.of(
                boolItem("DEVICE_ONLINE", true),
                boolItem("QR_VISIVEL", true),
                boolItem("CATALOGO_ATUALIZADO", true),
                boolItem("UNIDADE_PRODUCAO_ATIVA", true),
                boolItem("OPERADOR_CONFIRMOU", true)
        ));
        String resp = mockMvc.perform(post("/tenant/operacao/turnos/abrir")
                        .with(tenantHeaders(prov.getTenantId(), prov.getTenantCode()))
                        .with(authUser(owner, TenantUserRole.TENANT_OWNER.name()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).at("/data/id").asLong();
    }

    private void publicarCardapio(ProvisionarTenantResponse prov) {
        // Garantir cozinha ativa
        boolean existsCentral = !cozinhaRepository.findByUnidadeAtendimentoIdAndTipoAndAtiva(
                prov.getUnidadeAtendimentoId(), TipoCozinha.CENTRAL, true).isEmpty();
        if (!existsCentral) {
            boolean anyActive = !cozinhaRepository.findByAtivaAndTipo(true, TipoCozinha.CENTRAL).isEmpty();
            if (!anyActive) {
                Cozinha c = new Cozinha();
                c.setNome("Cozinha Central IT");
                c.setTipo(TipoCozinha.CENTRAL);
                c.setAtiva(true);
                c.setDescricao("IT fixture");
                cozinhaRepository.saveAndFlush(c);
            }
        }
        // Criar produto se nao existir
        Tenant tenant = tenantRepository.findById(prov.getTenantId()).orElseThrow();
        TenantCardapioConfig config = tenantCardapioConfigRepository.findByTenantId(prov.getTenantId())
                .orElseGet(() -> {
                    TenantCardapioConfig created = new TenantCardapioConfig();
                    created.setTenant(tenant);
                    return created;
                });
        config.setCardapioPublicado(true);
        config.setCardapioPublicadoEm(LocalDateTime.now());
        config.setCardapioPublicadoPorUserId(prov.getOwnerUserId());
        tenantCardapioConfigRepository.saveAndFlush(config);

        CategoriaProduto cat = categoriaProdutoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(prov.getTenantId())
                .stream().findFirst().orElseGet(() -> {
                    CategoriaProduto created = new CategoriaProduto();
                    created.setTenant(tenant);
                    created.setNome("Auto IT");
                    created.setSlug("auto-it-" + prov.getTenantId());
                    created.setDescricao("IT");
                    created.setOrdem(0);
                    created.setAtivo(true);
                    return categoriaProdutoRepository.saveAndFlush(created);
                });
        boolean hasProduto = !produtoRepository.findByTenantIdAndDisponivelTrueAndAtivoTrue(
                prov.getTenantId(), org.springframework.data.domain.PageRequest.of(0, 1)).hasContent();
        if (hasProduto) {
            Produto p = new Produto();
            p.setTenant(tenant);
            p.setCodigo("P-IT-" + prov.getTenantId());
            p.setNome("Produto IT");
            p.setDescricao("IT fixture");
            p.setPreco(new BigDecimal("10.00"));
            p.setCategoria(CategoriaProdutoLegacy.OUTROS);
            p.setCategoriaProduto(cat);
            p.setDisponivel(true);
            p.setAtivo(true);
            produtoRepository.saveAndFlush(p);
        }
    }

    private long criarPedidoQr(ProvisionarTenantResponse prov, long turnoId) throws Exception {
        var produtos = produtoRepository.findByTenantIdAndDisponivelTrueAndAtivoTrue(
                prov.getTenantId(), org.springframework.data.domain.PageRequest.of(0, 1)).getContent();
        assertThat(produtos).isNotEmpty();

        PublicQrPedidoRequest qrReq = new PublicQrPedidoRequest();
        PublicQrPedidoItemRequest item = new PublicQrPedidoItemRequest();
        item.setProdutoId(produtos.get(0).getId());
        item.setQuantidade(1);
        qrReq.setItens(List.of(item));
        qrReq.setIdempotencyKey("idem-prefecho-" + turnoId + "-" + System.nanoTime());

        String resp = mockMvc.perform(post("/public/q/" + prov.getQrToken() + "/pedidos")
                        .header("Idempotency-Key", qrReq.getIdempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(qrReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).at("/data/pedidoId").asLong();
    }

    private com.fasterxml.jackson.databind.JsonNode getPreFecho(
            ProvisionarTenantResponse prov, User owner, long turnoId) throws Exception {
        setTenantCtx(prov);
        String resp = mockMvc.perform(get("/tenant/operacao/turnos/" + turnoId + "/pre-fecho")
                        .with(tenantHeaders(prov.getTenantId(), prov.getTenantCode()))
                        .with(authUser(owner, TenantUserRole.TENANT_OWNER.name()))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp);
    }

    private FecharTurnoRequest fecharReq(boolean forcar) {
        FecharTurnoRequest req = new FecharTurnoRequest();
        req.setForcarFecho(forcar);
        req.setChecklist(List.of(
                boolItem("PEDIDOS_PENDENTES_VERIFICADOS", true),
                boolItem("PAGAMENTOS_PENDENTES_VERIFICADOS", true),
                boolItem("SUBPEDIDOS_EM_ABERTO_VERIFICADOS", true)
        ));
        return req;
    }

    private ChecklistItemRespostaRequest boolItem(String codigo, boolean v) {
        ChecklistItemRespostaRequest r = new ChecklistItemRespostaRequest();
        r.setCodigo(codigo);
        r.setValorBoolean(v);
        return r;
    }
}
