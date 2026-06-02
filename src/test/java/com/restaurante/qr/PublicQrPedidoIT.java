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
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.SubPedidoRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.service.QrCodeOperacionalService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
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
class PublicQrPedidoIT extends PostgresTestcontainersConfig {

    @Autowired ObjectMapper objectMapper;
    @Autowired TestRestTemplate restTemplate;

    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CozinhaRepository cozinhaRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired SubPedidoRepository subPedidoRepository;
    @Autowired QrCodeOperacionalService qrCodeOperacionalService;

    @Test
    void publicQr_canCreatePedido_withoutPayment_andPedidoIsTenantScoped() throws Exception {
        Tenant tenantA = criarTenant("Banca da Tia Rosa", "banca-tia-rosa", "TIA-ROSA");
        Instituicao instA = criarInstituicao(tenantA, "Banca da Tia Rosa", "TR", "NIF-TR-101", "+244900000101");
        UnidadeAtendimento unidadeA = criarUnidade(instA, "Unidade A", TipoUnidadeAtendimento.RESTAURANTE);
        criarCozinhaVinculada(unidadeA, "Bar A", TipoCozinha.BAR_PREP);

        CategoriaProduto catA = criarCategoria(tenantA, "Bebidas", "bebidas");
        Produto prodA = criarProduto(tenantA, catA, "AGUA-500", "Água 500ml A", new BigDecimal("10.00"));

        QrCodeOperacional qrA = qrCodeOperacionalService.criarQr(
                tenantA.getId(), instA.getId(), unidadeA.getId(), null, QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR A"
        );

        String payload = """
                {
                  "clienteNome": "Edson",
                  "clienteTelefone": "+244900000000",
                  "observacao": "Sem cebola",
                  "itens": [
                    { "produtoId": %d, "quantidade": 2, "observacao": "Gelado" }
                  ]
                }
                """.formatted(prodA.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Idempotency-Key", "idem-key-00000001");
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);

        ResponseEntity<String> resp = restTemplate.postForEntity("/public/q/{token}/pedidos", entity, String.class, qrA.getToken());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode json = objectMapper.readTree(resp.getBody());
        assertThat(json.path("success").asBoolean()).isTrue();
        Long pedidoId = json.at("/data/pedidoId").asLong();
        assertThat(pedidoId).isPositive();
        assertThat(json.at("/data/statusFinanceiro").asText()).isEqualTo("NAO_PAGO");
        assertThat(json.at("/data/total").asText()).isNotBlank();
        assertThat(json.at("/data/itens").isArray()).isTrue();
        assertThat(json.at("/data/itens/0/produtoId").asLong()).isEqualTo(prodA.getId());

        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow();
        assertThat(pedido.getTenant().getId()).isEqualTo(tenantA.getId());
        assertThat(pedido.getSessaoConsumo().getInstituicao().getId()).isEqualTo(instA.getId());
        assertThat(pedido.getStatus().name()).isEqualTo("CRIADO");

        // Produção: subpedidos devem ter unidadeProducao resolvida (rota ou default)
        var subs = subPedidoRepository.findByPedidoIdOrderByCreatedAtAsc(pedido.getId());
        assertThat(subs).isNotEmpty();
        assertThat(subs).allMatch(sp -> sp.getUnidadeProducao() != null);
    }

    @Test
    void publicQr_rejectsCrossTenantProduto() throws Exception {
        Tenant tenantA = criarTenant("Banca da Tia Rosa X", "banca-tia-rosa-x", "TIA-ROSA-X");
        Tenant tenantB = criarTenant("Bar do João X", "bar-do-joao-x", "BAR-JOAO-X");

        Instituicao instA = criarInstituicao(tenantA, "Inst A", "TA", "NIF-TA-201", "+244900000201");
        Instituicao instB = criarInstituicao(tenantB, "Inst B", "TB", "NIF-TB-202", "+244900000202");

        UnidadeAtendimento unidadeA = criarUnidade(instA, "Unidade A", TipoUnidadeAtendimento.RESTAURANTE);
        UnidadeAtendimento unidadeB = criarUnidade(instB, "Unidade B", TipoUnidadeAtendimento.BAR);
        criarCozinhaVinculada(unidadeA, "Bar A", TipoCozinha.BAR_PREP);
        criarCozinhaVinculada(unidadeB, "Bar B", TipoCozinha.BAR_PREP);

        CategoriaProduto catA = criarCategoria(tenantA, "Bebidas", "bebidas");
        CategoriaProduto catB = criarCategoria(tenantB, "Bebidas", "bebidas");
        Produto prodB = criarProduto(tenantB, catB, "AGUA-500", "Água 500ml B", new BigDecimal("10.00"));

        QrCodeOperacional qrA = qrCodeOperacionalService.criarQr(
                tenantA.getId(), instA.getId(), unidadeA.getId(), null, QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR A"
        );

        String payload = """
                { "itens": [ { "produtoId": %d, "quantidade": 1 } ] }
                """.formatted(prodB.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Idempotency-Key", "idem-key-00000002");
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);

        ResponseEntity<String> resp = restTemplate.postForEntity("/public/q/{token}/pedidos", entity, String.class, qrA.getToken());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode json = objectMapper.readTree(resp.getBody());
        assertThat(json.path("message").asText()).contains("Produto inválido");
    }

    @Test
    void publicQr_invalidTokenDoesNotCreatePedido() {
        String payload = """
                { "itens": [ { "produtoId": 1, "quantidade": 1 } ] }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);

        ResponseEntity<String> resp = restTemplate.postForEntity("/public/q/{token}/pedidos", entity, String.class, "q_INVALIDO_123");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
        p.setDescricao(null);
        p.setPreco(preco);
        p.setCategoria(CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA);
        p.setCategoriaProduto(categoriaProduto);
        p.setDisponivel(true);
        p.setAtivo(true);
        return produtoRepository.saveAndFlush(p);
    }
}
