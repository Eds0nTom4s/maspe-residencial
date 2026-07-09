package com.restaurante.qr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.Instituicao;
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
import com.restaurante.repository.ProdutoRepository;
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
class PublicQrPedidoIdempotencyIT extends PostgresTestcontainersConfig {

    @Autowired ObjectMapper objectMapper;
    @Autowired TestRestTemplate restTemplate;

    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CozinhaRepository cozinhaRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired QrCodeOperacionalService qrCodeOperacionalService;

    @Test
    void missingIdempotencyKey_returns400() {
        Tenant tenant = criarTenant("Tenant Idem", "tenant-idem", "IDEM");
        Instituicao inst = criarInstituicao(tenant, "Inst", "ID", "NIF-ID-001", "+244900001001");
        UnidadeAtendimento ua = criarUnidade(inst, "UA", TipoUnidadeAtendimento.RESTAURANTE);
        criarCozinhaVinculada(ua, "Bar", TipoCozinha.BAR_PREP);
        CategoriaProduto cat = criarCategoria(tenant, "Bebidas", "bebidas");
        Produto prod = criarProduto(tenant, cat, "AGUA", "Água", new BigDecimal("10.00"));
        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(tenant.getId(), inst.getId(), ua.getId(), null, QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR");

        String payload = """
                { "itens": [ { "produtoId": %d, "quantidade": 1 } ] }
                """.formatted(prod.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);

        ResponseEntity<String> resp = restTemplate.postForEntity("/public/q/{token}/pedidos", entity, String.class, qr.getToken());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void sameIdempotencyKey_samePayload_returnsSamePedido() throws Exception {
        Tenant tenant = criarTenant("Tenant Idem2", "tenant-idem2", "IDEM2");
        Instituicao inst = criarInstituicao(tenant, "Inst2", "I2", "NIF-ID-002", "+244900001002");
        UnidadeAtendimento ua = criarUnidade(inst, "UA2", TipoUnidadeAtendimento.RESTAURANTE);
        criarCozinhaVinculada(ua, "Bar2", TipoCozinha.BAR_PREP);
        CategoriaProduto cat = criarCategoria(tenant, "Bebidas", "bebidas");
        Produto prod = criarProduto(tenant, cat, "AGUA", "Água", new BigDecimal("10.00"));
        publicarCardapioForTest(tenant.getId());
        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(tenant.getId(), inst.getId(), ua.getId(), null, QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR");

        String payload = """
                { "clienteNome": "Edson", "itens": [ { "produtoId": %d, "quantidade": 1 } ] }
                """.formatted(prod.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Idempotency-Key", "idem-key-same-0001");
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);

        ResponseEntity<String> r1 = restTemplate.postForEntity("/public/q/{token}/pedidos", entity, String.class, qr.getToken());
        ResponseEntity<String> r2 = restTemplate.postForEntity("/public/q/{token}/pedidos", entity, String.class, qr.getToken());
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode j1 = objectMapper.readTree(r1.getBody());
        JsonNode j2 = objectMapper.readTree(r2.getBody());
        assertThat(j1.at("/data/pedidoId").asLong()).isEqualTo(j2.at("/data/pedidoId").asLong());
        assertThat(j2.at("/data/mensagem").asText()).contains("já criado");
    }

    @Test
    void sameIdempotencyKey_differentPayload_returns409() throws Exception {
        Tenant tenant = criarTenant("Tenant Idem3", "tenant-idem3", "IDEM3");
        Instituicao inst = criarInstituicao(tenant, "Inst3", "I3", "NIF-ID-003", "+244900001003");
        UnidadeAtendimento ua = criarUnidade(inst, "UA3", TipoUnidadeAtendimento.RESTAURANTE);
        criarCozinhaVinculada(ua, "Bar3", TipoCozinha.BAR_PREP);
        CategoriaProduto cat = criarCategoria(tenant, "Bebidas", "bebidas");
        Produto prod = criarProduto(tenant, cat, "AGUA", "Água", new BigDecimal("10.00"));
        Produto prod2 = criarProduto(tenant, cat, "AGUA2", "Água 2", new BigDecimal("10.00"));
        publicarCardapioForTest(tenant.getId());
        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(tenant.getId(), inst.getId(), ua.getId(), null, QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Idempotency-Key", "idem-key-conflict-0001");

        String payload1 = """
                { "itens": [ { "produtoId": %d, "quantidade": 1 } ] }
                """.formatted(prod.getId());
        String payload2 = """
                { "itens": [ { "produtoId": %d, "quantidade": 1 } ] }
                """.formatted(prod2.getId());

        ResponseEntity<String> r1 = restTemplate.postForEntity("/public/q/{token}/pedidos", new HttpEntity<>(payload1, headers), String.class, qr.getToken());
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> r2 = restTemplate.postForEntity("/public/q/{token}/pedidos", new HttpEntity<>(payload2, headers), String.class, qr.getToken());
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
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
