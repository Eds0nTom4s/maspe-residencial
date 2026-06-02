package com.restaurante.qr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.repository.CategoriaProdutoRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=servlet"
)
@ActiveProfiles("it-postgres")
class PublicQrCardapioIT extends PostgresTestcontainersConfig {

    @Autowired ObjectMapper objectMapper;
    @Autowired TestRestTemplate restTemplate;

    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired QrCodeOperacionalService qrCodeOperacionalService;

    @Test
    void publicCardapioByQr_isIsolatedByTenant_andInvalidOrRevokedTokensReturn404() throws Exception {
        Tenant tenantA = criarTenant("Banca da Tia Rosa", "banca-tia-rosa", "TIA-ROSA");
        Tenant tenantB = criarTenant("Bar do João", "bar-do-joao", "BAR-JOAO");

        Instituicao instA = criarInstituicao(tenantA, "Banca da Tia Rosa", "TR", "NIF-TR-001", "+244900000001");
        Instituicao instB = criarInstituicao(tenantB, "Bar do João", "BJ", "NIF-BJ-001", "+244900000002");

        UnidadeAtendimento unidadeA = criarUnidade(instA, "Unidade A", TipoUnidadeAtendimento.RESTAURANTE);
        UnidadeAtendimento unidadeB = criarUnidade(instB, "Unidade B", TipoUnidadeAtendimento.BAR);

        CategoriaProduto catA = criarCategoria(tenantA, "Bebidas", "bebidas");
        CategoriaProduto catB = criarCategoria(tenantB, "Bebidas", "bebidas");

        criarProduto(tenantA, catA, "AGUA-500", "Água 500ml A");
        criarProduto(tenantB, catB, "AGUA-500", "Água 500ml B");

        QrCodeOperacional qrA = qrCodeOperacionalService.criarQr(
                tenantA.getId(), instA.getId(), unidadeA.getId(), null, QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR A"
        );
        QrCodeOperacional qrB = qrCodeOperacionalService.criarQr(
                tenantB.getId(), instB.getId(), unidadeB.getId(), null, QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR B"
        );

        // Tenant A: só vê produto A
        ResponseEntity<String> respA = restTemplate.getForEntity("/public/q/{token}/cardapio", String.class, qrA.getToken());
        assertThat(respA.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode jsonA = objectMapper.readTree(respA.getBody());
        assertThat(jsonA.path("success").asBoolean()).isTrue();
        assertThat(jsonA.at("/data/qr/token").asText()).isEqualTo(qrA.getToken());
        assertThat(jsonA.at("/data/categorias").isArray()).isTrue();
        assertThat(jsonA.at("/data/categorias/0/produtos").toString()).contains("Água 500ml A");
        assertThat(jsonA.at("/data/categorias/0/produtos").toString()).doesNotContain("Água 500ml B");

        // Tenant B: só vê produto B
        ResponseEntity<String> respB = restTemplate.getForEntity("/public/q/{token}/cardapio", String.class, qrB.getToken());
        assertThat(respB.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode jsonB = objectMapper.readTree(respB.getBody());
        assertThat(jsonB.at("/data/categorias/0/produtos").toString()).contains("Água 500ml B");
        assertThat(jsonB.at("/data/categorias/0/produtos").toString()).doesNotContain("Água 500ml A");

        // Token inválido: 404
        ResponseEntity<String> invalid = restTemplate.getForEntity("/public/q/{token}/cardapio", String.class, "q_INVALIDO_123");
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // QR revogado: 404 (falha fechada)
        qrCodeOperacionalService.revogar(qrA.getId());
        ResponseEntity<String> revoked = restTemplate.getForEntity("/public/q/{token}/cardapio", String.class, qrA.getToken());
        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void publicQrResolver_returnsPublicContext() throws Exception {
        Tenant tenantA = criarTenant("Banca da Tia Rosa 2", "banca-tia-rosa-2", "TIA-ROSA-2");
        Instituicao instA = criarInstituicao(tenantA, "Banca da Tia Rosa 2", "TR2", "NIF-TR-002", "+244900000003");
        UnidadeAtendimento unidadeA = criarUnidade(instA, "Unidade A2", TipoUnidadeAtendimento.RESTAURANTE);

        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(
                tenantA.getId(), instA.getId(), unidadeA.getId(), null, QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR META"
        );

        ResponseEntity<String> resp = restTemplate.getForEntity("/public/q/{token}", String.class, qr.getToken());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(resp.getBody());
        assertThat(json.path("success").asBoolean()).isTrue();
        assertThat(json.at("/data/token").asText()).isEqualTo(qr.getToken());
        assertThat(json.at("/data/tenantCode").asText()).isEqualTo(tenantA.getTenantCode());
        assertThat(json.at("/data/instituicaoNome").asText()).isEqualTo(instA.getNome());
        assertThat(json.at("/data/unidadeAtendimentoNome").asText()).isEqualTo(unidadeA.getNome());
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

    private CategoriaProduto criarCategoria(Tenant tenant, String nome, String slug) {
        CategoriaProduto c = new CategoriaProduto();
        c.setTenant(tenant);
        c.setNome(nome);
        c.setSlug(UniqueTestData.uniqueSlug(slug));
        c.setOrdem(0);
        c.setAtivo(true);
        return categoriaProdutoRepository.saveAndFlush(c);
    }

    private Produto criarProduto(Tenant tenant, CategoriaProduto categoriaProduto, String codigo, String nome) {
        Produto p = new Produto();
        p.setTenant(tenant);
        p.setCodigo(codigo);
        p.setNome(nome);
        p.setDescricao(null);
        p.setPreco(new BigDecimal("10.00"));
        p.setCategoria(CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA);
        p.setCategoriaProduto(categoriaProduto);
        p.setDisponivel(true);
        p.setAtivo(true);
        return produtoRepository.saveAndFlush(p);
    }
}
