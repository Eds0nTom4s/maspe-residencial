package com.restaurante.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.DeviceCapability;
import com.restaurante.model.enums.DispositivoStatus;
import com.restaurante.model.enums.DispositivoTipo;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.device.DevicePrincipal;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.QrCodeOperacionalService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
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
class DeviceFilaDiffSyncIT extends PostgresTestcontainersConfig {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CozinhaRepository cozinhaRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired com.restaurante.repository.ProdutoRepository produtoRepository;
    @Autowired SubPedidoRepository subPedidoRepository;
    @Autowired QrCodeOperacionalService qrCodeOperacionalService;

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(username = "kitchen-user")
    void device_canFetchFilaDiff_afterKitchenStatusChange() throws Exception {
        Setup setup = setupTenantAndPedido("diff-1", "DF1");

        TenantContextHolder.set(new TenantContext(
                setup.tenant.getId(), setup.tenant.getTenantCode(), 2001L,
                Set.of(TenantUserRole.TENANT_KITCHEN.name()),
                TenantResolutionSource.JWT, false, false
        ));

        mockMvc.perform(patch("/tenant/producao/subpedidos/" + setup.subPedidoId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_PREPARACAO\",\"motivo\":\"Inicio\"}"))
                .andExpect(status().isOk());

        DevicePrincipal device = new DevicePrincipal(
                99L,
                "KDS-99",
                setup.tenant.getId(),
                setup.tenant.getTenantCode(),
                setup.instituicaoId,
                setup.unidadeAtendimentoId,
                setup.unidadeProducaoId,
                DispositivoTipo.KDS,
                DispositivoStatus.ATIVO,
                List.of(DeviceCapability.VIEW_PRODUCTION),
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );

        String diff = mockMvc.perform(get("/device/sync/producao/fila/diff")
                        .with(authentication(auth))
                        .param("sinceEventId", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(diff);
        assertThat(json.at("/data/eventos").isArray()).isTrue();
        assertThat(json.at("/data/eventos").size()).isGreaterThanOrEqualTo(1);
        assertThat(json.at("/data/affectedSubPedidoIds").toString()).contains(setup.subPedidoId.toString());
        assertThat(json.at("/fullSyncRequired").asBoolean()).isFalse();
    }

    @Test
    void diff_withUnknownSinceEventId_requiresFullSync() throws Exception {
        Setup setup = setupTenantAndPedido("diff-2", "DF2");

        DevicePrincipal device = new DevicePrincipal(
                98L,
                "KDS-98",
                setup.tenant.getId(),
                setup.tenant.getTenantCode(),
                setup.instituicaoId,
                setup.unidadeAtendimentoId,
                setup.unidadeProducaoId,
                DispositivoTipo.KDS,
                DispositivoStatus.ATIVO,
                List.of(DeviceCapability.VIEW_PRODUCTION),
                1
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                device, "N/A", List.of(new SimpleGrantedAuthority("ROLE_DEVICE"))
        );

        String diff = mockMvc.perform(get("/device/sync/producao/fila/diff")
                        .with(authentication(auth))
                        .param("sinceEventId", "99999999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(diff);
        assertThat(json.at("/fullSyncRequired").asBoolean()).isTrue();
        assertThat(json.at("/fullSyncReason").asText()).isEqualTo("VERSION_MISMATCH");
    }

    private Setup setupTenantAndPedido(String slug, String tenantCode) throws Exception {
        Tenant tenant = criarTenant("Tenant " + slug, slug + "-" + System.nanoTime(), tenantCode + (System.nanoTime() % 1000));
        Instituicao inst = criarInstituicao(tenant, "Inst " + slug, tenantCode.substring(0, Math.min(3, tenantCode.length())), "NIF-" + tenantCode, "+244900" + Math.abs(slug.hashCode() % 1_000_000));
        UnidadeAtendimento unidade = criarUnidade(inst, "Unidade " + slug, TipoUnidadeAtendimento.RESTAURANTE);
        criarCozinhaVinculada(unidade, "Cozinha " + slug, TipoCozinha.CENTRAL);

        CategoriaProduto cat = criarCategoria(tenant, "Geral", "geral-" + (System.nanoTime() % 100_000));
        Produto prod = criarProduto(tenant, cat, "P1-" + tenantCode + (System.nanoTime() % 100_000), "Produto " + tenantCode, new BigDecimal("10.00"));

        var qr = qrCodeOperacionalService.criarQr(
                tenant.getId(), inst.getId(), unidade.getId(), null, QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR " + slug
        );

        String payload = """
                { "itens": [ { "produtoId": %d, "quantidade": 1 } ] }
                """.formatted(prod.getId());

        String resp = mockMvc.perform(post("/public/q/" + qr.getToken() + "/pedidos")
                        .header("Idempotency-Key", "idem-" + slug + "-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resp);
        Long pedidoId = json.at("/data/pedidoId").asLong();
        List<SubPedido> subs = subPedidoRepository.findByPedidoIdOrderByCreatedAtAsc(pedidoId);
        SubPedido sp = subs.getFirst();
        Long unidadeProducaoId = sp.getUnidadeProducao() != null ? sp.getUnidadeProducao().getId() : null;

        return new Setup(tenant, inst.getId(), unidade.getId(), pedidoId, sp.getId(), unidadeProducaoId);
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

    private record Setup(Tenant tenant, Long instituicaoId, Long unidadeAtendimentoId, Long pedidoId, Long subPedidoId, Long unidadeProducaoId) {}
}

