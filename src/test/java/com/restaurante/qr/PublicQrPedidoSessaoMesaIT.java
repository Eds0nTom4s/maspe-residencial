package com.restaurante.qr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Mesa;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.MesaRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.service.QrCodeOperacionalService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import com.restaurante.testsupport.UniqueTestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=servlet"
)
@ActiveProfiles("it-postgres")
class PublicQrPedidoSessaoMesaIT extends PostgresTestcontainersConfig {

    @Autowired ObjectMapper objectMapper;
    @Autowired TestRestTemplate restTemplate;

    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired MesaRepository mesaRepository;
    @Autowired SessaoConsumoRepository sessaoConsumoRepository;
    @Autowired CozinhaRepository cozinhaRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired QrCodeOperacionalService qrCodeOperacionalService;

    @Test
    void qrMesa_reusesOpenSession_whenCreatingMultiplePedidos() throws Exception {
        Tenant tenant = criarTenant("Tenant Mesa", "tenant-mesa", "MESA");
        Instituicao inst = criarInstituicao(tenant, "Inst Mesa", "IM", "NIF-IM-001", "+244900002001");
        UnidadeAtendimento ua = criarUnidade(inst, "UA Mesa", TipoUnidadeAtendimento.RESTAURANTE);
        criarCozinhaVinculada(ua, "Bar Mesa", TipoCozinha.BAR_PREP);

        Mesa mesa = criarMesa(inst, ua, "Mesa 1", 1, "QR-MESA-LEGADO-1");
        SessaoConsumo sessao = new SessaoConsumo();
        sessao.setTenant(tenant);
        sessao.setMesa(mesa);
        sessao.setUnidadeAtendimento(ua);
        sessao.setInstituicao(inst);
        sessao.setModoAnonimo(true);
        sessao.setStatus(StatusSessaoConsumo.ABERTA);
        sessao.setTipoSessao(com.restaurante.model.enums.TipoSessao.POS_PAGO);
        SessaoConsumo aberta = sessaoConsumoRepository.saveAndFlush(sessao);

        CategoriaProduto cat = criarCategoria(tenant, "Bebidas", "bebidas");
        Produto prod = criarProduto(tenant, cat, "AGUA", "Água", new BigDecimal("10.00"));

        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(
                tenant.getId(), inst.getId(), ua.getId(), mesa.getId(), QrCodeOperacionalTipo.MESA, "QR Mesa"
        );

        Long p1 = criarPedido(qr.getToken(), "idem-mesa-0001", prod.getId());
        Long p2 = criarPedido(qr.getToken(), "idem-mesa-0002", prod.getId());

        Pedido pedido1 = pedidoRepository.findById(p1).orElseThrow();
        Pedido pedido2 = pedidoRepository.findById(p2).orElseThrow();
        assertThat(pedido1.getSessaoConsumo().getId()).isEqualTo(aberta.getId());
        assertThat(pedido2.getSessaoConsumo().getId()).isEqualTo(aberta.getId());
    }

    private Long criarPedido(String token, String idemKey, Long produtoId) throws Exception {
        String payload = """
                { "itens": [ { "produtoId": %d, "quantidade": 1 } ] }
                """.formatted(produtoId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Idempotency-Key", idemKey);
        ResponseEntity<String> resp = restTemplate.postForEntity("/public/q/{token}/pedidos", new HttpEntity<>(payload, headers), String.class, token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode json = objectMapper.readTree(resp.getBody());
        return json.at("/data/pedidoId").asLong();
    }

    private Tenant criarTenant(String nome, String slug, String tenantCode) {
        Tenant t = new Tenant();
        t.setNome(nome);
        t.setSlug(UniqueTestData.uniqueSlug(slug));
        t.setTenantCode(UniqueTestData.uniqueTenantCode(tenantCode));
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }

    private Instituicao criarInstituicao(Tenant tenant, String nome, String sigla, String nif, String telefoneAutorizacao) {
        Instituicao i = new Instituicao();
        i.setTenant(tenant);
        i.setNome(nome);
        i.setSigla(UniqueTestData.uniqueInstituicaoSigla(sigla));
        i.setNif(UniqueTestData.uniqueNif(nif));
        i.setTelefoneAutorizacao(UniqueTestData.uniqueTelefone());
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

    private Mesa criarMesa(Instituicao instituicao, UnidadeAtendimento ua, String referencia, Integer numero, String qrCode) {
        Mesa m = new Mesa();
        m.setTenant(instituicao.getTenant());
        m.setInstituicao(instituicao);
        m.setUnidadeAtendimento(ua);
        m.setReferencia(referencia);
        m.setNumero(numero);
        m.setQrCode(UniqueTestData.uniqueQrCode(qrCode));
        m.setAtiva(true);
        return mesaRepository.saveAndFlush(m);
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
        c.setSlug(UniqueTestData.uniqueSlug(slug));
        c.setOrdem(0);
        c.setAtivo(true);
        return categoriaProdutoRepository.saveAndFlush(c);
    }

    private Produto criarProduto(Tenant tenant, CategoriaProduto categoriaProduto, String codigo, String nome, BigDecimal preco) {
        Produto p = new Produto();
        p.setTenant(tenant);
        p.setCodigo(codigo);
        p.setNome(nome);
        p.setDescricao(null);
        p.setPreco(preco);
        p.setCategoria(CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA);
        p.setCategoriaProduto(categoriaProduto);
        p.setDisponivel(true);
        p.setAtivo(true);
        return produtoRepository.saveAndFlush(p);
    }
}
