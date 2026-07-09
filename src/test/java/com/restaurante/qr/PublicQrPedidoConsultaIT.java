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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT: GET público de pedido via QR token.
 *
 * <p>Valida:
 * <ul>
 *   <li>200 quando pedido pertence ao QR/tenant</li>
 *   <li>404 quando pedido não pertence ao QR (cross-tenant)</li>
 *   <li>404 quando pedido inexistente</li>
 *   <li>Sem JWT — endpoint público</li>
 *   <li>Preflight OPTIONS — CORS headers presentes</li>
 * </ul>
 *
 * <p>Estratégia de segurança: o QR token (UUID não enumerável) actua como scope de tenant.
 * Um atacante com ID de pedido mas sem o token QR correcto não consegue aceder ao pedido.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=servlet"
)
@ActiveProfiles("it-postgres")
class PublicQrPedidoConsultaIT extends PostgresTestcontainersConfig {

    @Autowired ObjectMapper objectMapper;
    @Autowired TestRestTemplate restTemplate;

    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CozinhaRepository cozinhaRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired QrCodeOperacionalService qrCodeOperacionalService;

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário 1: GET pedido existente → 200 com payload mínimo
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getPedidoPublico_quandoPedidoExiste_retorna200ComPayloadMinimo() throws Exception {
        Tenant tenant = criarTenant("Consulta Pedido OK", "consulta-pedido-ok", "CPO");
        Instituicao inst = criarInstituicao(tenant, "Inst CPO", "CPO", "NIF-CPO-001");
        UnidadeAtendimento ua = criarUnidade(inst, "UA CPO", TipoUnidadeAtendimento.RESTAURANTE);
        criarCozinhaVinculada(ua, "Bar CPO", TipoCozinha.BAR_PREP);
        CategoriaProduto cat = criarCategoria(tenant, "Bebidas CPO", "bebidas-cpo");
        Produto prod = criarProduto(tenant, cat, "AGUA-CPO", "Água CPO", new BigDecimal("15.00"));
        publicarCardapioForTest(tenant.getId());

        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(
                tenant.getId(), inst.getId(), ua.getId(), null,
                QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR CPO"
        );

        // Criar pedido via POST público
        Long pedidoId = criarPedidoViaPost(qr.getToken(), "idem-cpo-get-001", prod.getId());
        assertThat(pedidoId).isPositive();

        // GET público — sem Authorization header
        ResponseEntity<String> resp = restTemplate.getForEntity(
                "/public/q/{token}/pedidos/{pedidoId}",
                String.class,
                qr.getToken(),
                pedidoId
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = objectMapper.readTree(resp.getBody());
        assertThat(json.path("success").asBoolean()).isTrue();

        JsonNode data = json.at("/data");
        assertThat(data.at("/pedidoId").asLong()).isEqualTo(pedidoId);
        assertThat(data.at("/statusOperacional").asText()).isNotBlank();
        assertThat(data.at("/statusFinanceiro").asText()).isNotBlank();
        assertThat(data.at("/total").asText()).isNotBlank();
        assertThat(data.at("/itens").isArray()).isTrue();
        assertThat(data.at("/itens").size()).isGreaterThan(0);
        assertThat(data.at("/itens/0/nome").asText()).isNotBlank();
        assertThat(data.at("/itens/0/quantidade").asInt()).isGreaterThan(0);

        // Não deve expor dados sensíveis
        assertThat(data.has("tenantId")).isFalse();
        assertThat(data.has("evidencias")).isFalse();
        assertThat(data.has("hmacSignature")).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário 2: GET pedido de outro tenant → 404 (cross-tenant isolation)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getPedidoPublico_quandoPedidoNaoPertenceAoQr_retorna404() throws Exception {
        // Tenant A — cria QR e pedido
        Tenant tenantA = criarTenant("Tenant Cross A", "cross-a", "CROSA");
        Instituicao instA = criarInstituicao(tenantA, "Inst A Cross", "IAC", "NIF-CROSA-001");
        UnidadeAtendimento uaA = criarUnidade(instA, "UA A Cross", TipoUnidadeAtendimento.RESTAURANTE);
        criarCozinhaVinculada(uaA, "Bar A Cross", TipoCozinha.BAR_PREP);
        CategoriaProduto catA = criarCategoria(tenantA, "Bebidas A Cross", "bebidas-a-cross");
        Produto prodA = criarProduto(tenantA, catA, "AGUA-CROSA", "Água A Cross", new BigDecimal("10.00"));
        publicarCardapioForTest(tenantA.getId());
        QrCodeOperacional qrA = qrCodeOperacionalService.criarQr(
                tenantA.getId(), instA.getId(), uaA.getId(), null,
                QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR A Cross"
        );
        Long pedidoAId = criarPedidoViaPost(qrA.getToken(), "idem-cross-a-001", prodA.getId());

        // Tenant B — cria QR separado
        Tenant tenantB = criarTenant("Tenant Cross B", "cross-b", "CROSB");
        Instituicao instB = criarInstituicao(tenantB, "Inst B Cross", "IBC", "NIF-CROSB-001");
        UnidadeAtendimento uaB = criarUnidade(instB, "UA B Cross", TipoUnidadeAtendimento.RESTAURANTE);
        criarCozinhaVinculada(uaB, "Bar B Cross", TipoCozinha.BAR_PREP);
        QrCodeOperacional qrB = qrCodeOperacionalService.criarQr(
                tenantB.getId(), instB.getId(), uaB.getId(), null,
                QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR B Cross"
        );

        // Tentar aceder ao pedido do Tenant A usando o QR do Tenant B → deve retornar 404
        ResponseEntity<String> resp = restTemplate.getForEntity(
                "/public/q/{token}/pedidos/{pedidoId}",
                String.class,
                qrB.getToken(),   // token do tenant B
                pedidoAId         // ID do pedido do tenant A
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário 3: GET pedido inexistente → 404
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getPedidoPublico_quandoPedidoInexistente_retorna404() throws Exception {
        Tenant tenant = criarTenant("Tenant 404 Pedido", "tenant-404-ped", "T404P");
        Instituicao inst = criarInstituicao(tenant, "Inst 404P", "I4P", "NIF-T404P-001");
        UnidadeAtendimento ua = criarUnidade(inst, "UA 404P", TipoUnidadeAtendimento.RESTAURANTE);
        criarCozinhaVinculada(ua, "Bar 404P", TipoCozinha.BAR_PREP);
        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(
                tenant.getId(), inst.getId(), ua.getId(), null,
                QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR 404P"
        );

        // ID inexistente
        ResponseEntity<String> resp = restTemplate.getForEntity(
                "/public/q/{token}/pedidos/{pedidoId}",
                String.class,
                qr.getToken(),
                Long.MAX_VALUE // ID que não existe
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário 4: GET sem Authorization header → 200 (endpoint público)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getPedidoPublico_semJwt_retorna200ParaPedidoValido() throws Exception {
        Tenant tenant = criarTenant("Tenant Sem JWT", "tenant-sem-jwt", "TSJWT");
        Instituicao inst = criarInstituicao(tenant, "Inst SemJWT", "ISJ", "NIF-TSJWT-001");
        UnidadeAtendimento ua = criarUnidade(inst, "UA SemJWT", TipoUnidadeAtendimento.RESTAURANTE);
        criarCozinhaVinculada(ua, "Bar SemJWT", TipoCozinha.BAR_PREP);
        CategoriaProduto cat = criarCategoria(tenant, "Bebidas SemJWT", "bebidas-semjwt");
        Produto prod = criarProduto(tenant, cat, "AGUA-SJ", "Água SemJWT", new BigDecimal("8.00"));
        publicarCardapioForTest(tenant.getId());
        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(
                tenant.getId(), inst.getId(), ua.getId(), null,
                QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR SemJWT"
        );

        Long pedidoId = criarPedidoViaPost(qr.getToken(), "idem-semjwt-001", prod.getId());

        // GET explicitamente sem header Authorization
        HttpHeaders headers = new HttpHeaders();
        // Propositalmente vazio — sem Authorization
        ResponseEntity<String> resp = restTemplate.exchange(
                "/public/q/{token}/pedidos/{pedidoId}",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class,
                qr.getToken(),
                pedidoId
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cenário 5: Preflight OPTIONS → headers CORS presentes (sem 401/403)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void preflightCors_publicQrPedido_retornaHeadersPermitidosSem401() throws Exception {
        Tenant tenant = criarTenant("Tenant CORS", "tenant-cors-pd", "TCRSPD");
        Instituicao inst = criarInstituicao(tenant, "Inst CORS PD", "ICP", "NIF-TCRSPD-001");
        UnidadeAtendimento ua = criarUnidade(inst, "UA CORS PD", TipoUnidadeAtendimento.RESTAURANTE);
        criarCozinhaVinculada(ua, "Bar CORS PD", TipoCozinha.BAR_PREP);
        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(
                tenant.getId(), inst.getId(), ua.getId(), null,
                QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR CORS PD"
        );

        HttpHeaders preflightHeaders = new HttpHeaders();
        preflightHeaders.set("Origin", "http://localhost:5173");
        preflightHeaders.set("Access-Control-Request-Method", "GET");
        preflightHeaders.set("Access-Control-Request-Headers", "Content-Type");

        // GET endpoint preflight
        ResponseEntity<String> respGet = restTemplate.exchange(
                "/public/q/{token}/pedidos/{pedidoId}",
                HttpMethod.OPTIONS,
                new HttpEntity<>(preflightHeaders),
                String.class,
                qr.getToken(),
                999999L
        );
        assertThat(respGet.getStatusCode().value()).isIn(200, 204);
        assertThat(respGet.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(respGet.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);

        // POST endpoint preflight (criação de pedido)
        HttpHeaders preflightHeadersPost = new HttpHeaders();
        preflightHeadersPost.set("Origin", "http://localhost:5173");
        preflightHeadersPost.set("Access-Control-Request-Method", "POST");
        preflightHeadersPost.set("Access-Control-Request-Headers", "Content-Type");

        ResponseEntity<String> respPost = restTemplate.exchange(
                "/public/q/{token}/pedidos",
                HttpMethod.OPTIONS,
                new HttpEntity<>(preflightHeadersPost),
                String.class,
                qr.getToken()
        );
        assertThat(respPost.getStatusCode().value()).isIn(200, 204);
        assertThat(respPost.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(respPost.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Long criarPedidoViaPost(String token, String idemKey, Long produtoId) throws Exception {
        String payload = """
                { "itens": [ { "produtoId": %d, "quantidade": 2, "observacao": "IT consulta" } ] }
                """.formatted(produtoId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Idempotency-Key", idemKey);
        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/public/q/{token}/pedidos",
                new HttpEntity<>(payload, headers),
                String.class,
                token
        );
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

    private Instituicao criarInstituicao(Tenant tenant, String nome, String sigla, String nif) {
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
        p.setCodigo(UniqueTestData.uniqueSuffix() + "-" + codigo);
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
