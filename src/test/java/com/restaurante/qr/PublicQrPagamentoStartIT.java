package com.restaurante.qr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import com.restaurante.financeiro.gateway.appypay.AppyPayClient;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeRequest;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeResponse;
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
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.service.QrCodeOperacionalService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import com.restaurante.testsupport.UniqueTestData;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=servlet"
)
@ActiveProfiles("it-postgres")
class PublicQrPagamentoStartIT extends PostgresTestcontainersConfig {

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
    @Autowired QrCodeOperacionalService qrCodeOperacionalService;

    @MockBean AppyPayClient appyPayClient;

    @Test
    void startPayment_isTenantAware_idempotent_andRespectsExternalReferenceLimit() throws Exception {
        when(appyPayClient.createCharge(any())).thenReturn(AppyPayChargeResponse.builder()
                .chargeId("ch_123")
                .merchantTransactionId("IGNORED")
                .status("PENDING")
                .paymentMethod("REF")
                .entity("10100")
                .reference("999123456")
                .paymentUrl("https://pay.local/abc")
                .build());

        Tenant tenantA = criarTenant("Banca", "banca", "TIA");
        Instituicao instA = criarInstituicao(tenantA, "InstA", "IA", "NIF-PA-001", "+244900010001");
        UnidadeAtendimento uaA = criarUnidade(instA, "UA", TipoUnidadeAtendimento.RESTAURANTE);
        criarCozinhaVinculada(uaA, "Bar", TipoCozinha.BAR_PREP);
        CategoriaProduto catA = criarCategoria(tenantA, "Bebidas", "bebidas");
        Produto prodA = criarProduto(tenantA, catA, "AGUA", "Água", new BigDecimal("10.00"));

        QrCodeOperacional qrA = qrCodeOperacionalService.criarQr(
                tenantA.getId(), instA.getId(), uaA.getId(), null, QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR"
        );

        Long pedidoId = criarPedido(qrA.getToken(), "idem-order-0001", prodA.getId());
        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow();

        String payPayload = """
                { "metodoPagamento": "REF", "telefone": "+244900000000" }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Idempotency-Key", "idem-pay-00000001");
        HttpEntity<String> entity = new HttpEntity<>(payPayload, headers);

        ResponseEntity<String> r1 = restTemplate.postForEntity(
                "/public/q/{token}/pedidos/{pedidoId}/pagamentos",
                entity, String.class, qrA.getToken(), pedidoId
        );
        ResponseEntity<String> r2 = restTemplate.postForEntity(
                "/public/q/{token}/pedidos/{pedidoId}/pagamentos",
                entity, String.class, qrA.getToken(), pedidoId
        );

        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode j1 = objectMapper.readTree(r1.getBody());
        JsonNode j2 = objectMapper.readTree(r2.getBody());
        long pagamentoId1 = j1.at("/data/pagamentoId").asLong();
        long pagamentoId2 = j2.at("/data/pagamentoId").asLong();
        assertThat(pagamentoId1).isEqualTo(pagamentoId2);

        Pagamento pg = pagamentoGatewayRepository.findById(pagamentoId1).orElseThrow();
        assertThat(pg.getTenant().getId()).isEqualTo(tenantA.getId());
        assertThat(pg.getPedido().getId()).isEqualTo(pedidoId);
        assertThat(pg.getMetodo()).isEqualTo(MetodoPagamentoAppyPay.REF);
        assertThat(pg.getExternalReference()).hasSizeLessThanOrEqualTo(15);

        // gateway chamado apenas 1x (idempotência)
        verify(appyPayClient, times(1)).createCharge(any());

        ArgumentCaptor<AppyPayChargeRequest> captor = ArgumentCaptor.forClass(AppyPayChargeRequest.class);
        verify(appyPayClient).createCharge(captor.capture());
        assertThat(captor.getValue().getMerchantTransactionId()).isEqualTo(pg.getExternalReference());
    }

    @Test
    void cannotStartPaymentCrossTenant() throws Exception {
        when(appyPayClient.createCharge(any())).thenReturn(AppyPayChargeResponse.builder()
                .chargeId("ch_x")
                .merchantTransactionId("IGNORED")
                .status("PENDING")
                .paymentMethod("REF")
                .entity("10100")
                .reference("999123456")
                .build());

        Tenant tenantA = criarTenant("TenantA", "ta", "TA");
        Tenant tenantB = criarTenant("TenantB", "tb", "TB");

        Instituicao instA = criarInstituicao(tenantA, "InstA2", "IA2", "NIF-PA-002", "+244900010002");
        Instituicao instB = criarInstituicao(tenantB, "InstB2", "IB2", "NIF-PB-002", "+244900010003");

        UnidadeAtendimento uaA = criarUnidade(instA, "UAA", TipoUnidadeAtendimento.RESTAURANTE);
        UnidadeAtendimento uaB = criarUnidade(instB, "UAB", TipoUnidadeAtendimento.BAR);
        criarCozinhaVinculada(uaA, "BarA", TipoCozinha.BAR_PREP);
        criarCozinhaVinculada(uaB, "BarB", TipoCozinha.BAR_PREP);

        CategoriaProduto catB = criarCategoria(tenantB, "Bebidas", "bebidas");
        Produto prodB = criarProduto(tenantB, catB, "AGUA", "Água", new BigDecimal("10.00"));

        QrCodeOperacional qrA = qrCodeOperacionalService.criarQr(
                tenantA.getId(), instA.getId(), uaA.getId(), null, QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR A"
        );
        QrCodeOperacional qrB = qrCodeOperacionalService.criarQr(
                tenantB.getId(), instB.getId(), uaB.getId(), null, QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR B"
        );

        Long pedidoB = criarPedido(qrB.getToken(), "idem-order-b-0001", prodB.getId());

        String payPayload = """
                { "metodoPagamento": "REF", "telefone": "+244900000000" }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Idempotency-Key", "idem-pay-cross-0001");
        HttpEntity<String> entity = new HttpEntity<>(payPayload, headers);

        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/public/q/{token}/pedidos/{pedidoId}/pagamentos",
                entity, String.class, qrA.getToken(), pedidoB
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
