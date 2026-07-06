package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.gateway.appypay.AppyPayClient;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeResponse;
import com.restaurante.financeiro.repository.PagamentoCallbackLogRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.CallbackProcessingStatus;
import com.restaurante.model.enums.QrCodeOperacionalTipo;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.StatusPedido;
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
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.service.QrCodeOperacionalService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.web-application-type=servlet",
                "app.payment.appypay.webhook-signature-required=true",
                "app.payment.appypay.webhook-secret=TEST_SECRET_123",
                "app.payment.appypay.base-url=http://localhost/appypay",
                "app.payment.appypay.token-url=http://localhost/appypay/token",
                "app.payment.appypay.client-id=TEST_CLIENT",
                "app.payment.appypay.client-secret=TEST_SECRET",
                "app.payment.appypay.resource=TEST_RESOURCE",
                "app.payment.appypay.mock=false"
        }
)
@ActiveProfiles("it-postgres")
class PagamentoCallbackInvalidSignatureIT extends PostgresTestcontainersConfig {

    @Autowired ObjectMapper objectMapper;
    @Autowired TestRestTemplate restTemplate;

    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CozinhaRepository cozinhaRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired PagamentoGatewayRepository pagamentoGatewayRepository;
    @Autowired PagamentoCallbackLogRepository callbackLogRepository;
    @Autowired QrCodeOperacionalService qrCodeOperacionalService;

    @MockBean AppyPayClient appyPayClient;

    @Test
    void invalidSignature_doesNotAlterPayment_andCreatesRawLog() throws Exception {
        when(appyPayClient.createCharge(any())).thenReturn(AppyPayChargeResponse.builder()
                .chargeId("ch_sig")
                .merchantTransactionId("IGNORED")
                .status("PENDING")
                .paymentMethod("REF")
                .entity("10100")
                .reference("999123456")
                .build());

        Tenant tenantA = criarTenant("TenantSig", "tenant-sig", "TSG");
        Instituicao instA = criarInstituicao(tenantA, "InstSig", "IS", "NIF-TSG-1", "+244900099999");
        UnidadeAtendimento uaA = criarUnidade(instA, "UA", TipoUnidadeAtendimento.RESTAURANTE);
        criarCozinhaVinculada(uaA, "Bar", TipoCozinha.BAR_PREP);
        CategoriaProduto catA = criarCategoria(tenantA, "Bebidas", "bebidas");
        Produto prodA = criarProduto(tenantA, catA, "AGUA", "Água", new BigDecimal("10.00"));
        publicarCardapioForTest(tenantA.getId());

        QrCodeOperacional qrA = qrCodeOperacionalService.criarQr(
                tenantA.getId(), instA.getId(), uaA.getId(), null, QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR"
        );

        Long pedidoId = criarPedido(qrA.getToken(), "idem-order-sig-0001", prodA.getId());
        aceitarPedidoParaPagamento(pedidoId);
        iniciarPagamento(qrA.getToken(), pedidoId, "idem-pay-sig-0001");

        Pagamento pg = pagamentoGatewayRepository.findByPedidoIdOrderByCreatedAtDesc(pedidoId).get(0);
        assertThat(pg.getStatus()).isEqualTo(StatusPagamentoGateway.PENDENTE);

        String callbackJson = """
                {
                  "chargeId": "ch_sig",
                  "merchantTransactionId": "%s",
                  "status": "CONFIRMED",
                  "amount": %d,
                  "paymentMethod": "REF"
                }
                """.formatted(pg.getExternalReference(), toCentavos(pg.getAmount()));

        // Sem header X-AppyPay-Signature => deve retornar 401
        ResponseEntity<Void> cb = restTemplate.postForEntity(
                "/pagamentos/callback",
                new HttpEntity<>(callbackJson, jsonHeaders()),
                Void.class
        );
        assertThat(cb.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Pagamento after = pagamentoGatewayRepository.findById(pg.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(StatusPagamentoGateway.PENDENTE);

        Pedido pedidoDepois = pedidoRepository.findById(pedidoId).orElseThrow();
        assertThat(pedidoDepois.getStatusFinanceiro()).isEqualTo(StatusFinanceiroPedido.PENDENTE_PAGAMENTO);

        assertThat(callbackLogRepository.findAll())
                .anyMatch(l -> l.getProcessingStatus() == CallbackProcessingStatus.INVALID_SIGNATURE && Boolean.FALSE.equals(l.getSignatureValid()));
    }

    private void iniciarPagamento(String token, Long pedidoId, String idempotencyKey) throws Exception {
        String payPayload = """
                { "metodoPagamento": "REF", "telefone": "+244900000000" }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Idempotency-Key", idempotencyKey);
        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/public/q/{token}/pedidos/{pedidoId}/pagamentos",
                new HttpEntity<>(payPayload, headers),
                String.class,
                token,
                pedidoId
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode json = objectMapper.readTree(resp.getBody());
        assertThat(json.at("/data/pagamentoId").asLong()).isPositive();
    }

    private Long criarPedido(String token, String idemKey, Long produtoId) throws Exception {
        String payload = """
                { "itens": [ { "produtoId": %d, "quantidade": 1 } ] }
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

    private void aceitarPedidoParaPagamento(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow();
        pedido.setStatus(StatusPedido.EM_ANDAMENTO);
        pedidoRepository.saveAndFlush(pedido);
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
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
        p.setCategoria(CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA);
        p.setDisponivel(true);
        p.setAtivo(true);
        p.setCategoriaProduto(categoriaProduto);
        return produtoRepository.saveAndFlush(p);
    }

    private static long toCentavos(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
