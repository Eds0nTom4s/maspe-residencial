package com.restaurante.financeiro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.financeiro.gateway.appypay.AppyPayClient;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeResponse;
import com.restaurante.financeiro.paymentmethod.repository.TenantPaymentMethodRepository;
import com.restaurante.financeiro.paymentmethod.service.TenantPaymentMethodBootstrapService;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.PaymentMethodCode;
import com.restaurante.model.enums.PaymentMethodStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=servlet"
)
@ActiveProfiles("it-postgres")
class PublicQrPaymentMethodsIT extends PostgresTestcontainersConfig {

    @Autowired ObjectMapper objectMapper;
    @Autowired TestRestTemplate restTemplate;

    @Autowired TenantRepository tenantRepository;
    @Autowired InstituicaoRepository instituicaoRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired QrCodeOperacionalService qrCodeOperacionalService;

    @Autowired TenantPaymentMethodBootstrapService bootstrapService;
    @Autowired TenantPaymentMethodRepository tenantPaymentMethodRepository;

    @MockBean AppyPayClient appyPayClient;

    @Test
    void public_qr_lists_only_active_methods_and_blocks_appypay_when_inactive() throws Exception {
        when(appyPayClient.createCharge(any())).thenReturn(AppyPayChargeResponse.builder()
                .chargeId("ch_123")
                .status("PENDING")
                .paymentMethod("REF")
                .entity("10100")
                .reference("999123456")
                .paymentUrl("https://pay.local/abc")
                .build());

        Tenant tenant = criarTenant("Banca2", "banca2", "TI2");
        Instituicao inst = criarInstituicao(tenant, "InstA", "IA2");
        UnidadeAtendimento ua = criarUnidade(inst, "UA", TipoUnidadeAtendimento.RESTAURANTE);
        CategoriaProduto cat = criarCategoria(tenant, "Bebidas", "bebidas");
        Produto prod = criarProduto(tenant, cat, "AGUA", "Água", new BigDecimal("10.00"));

        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(
                tenant.getId(), inst.getId(), ua.getId(), null, QrCodeOperacionalTipo.UNIDADE_ATENDIMENTO, "QR"
        );

        // bootstrap defaults e depois desativa appypay para QR
        bootstrapService.ensureDefaults(tenant.getId());
        var appy = tenantPaymentMethodRepository.findByTenantIdAndCode(tenant.getId(), PaymentMethodCode.APPYPAY).orElseThrow();
        appy.setStatus(PaymentMethodStatus.INACTIVE);
        appy.setEnabledForQr(false);
        tenantPaymentMethodRepository.saveAndFlush(appy);

        ResponseEntity<String> methodsResp = restTemplate.getForEntity(
                "/api/public/q/{token}/payment-methods?destination=PEDIDO",
                String.class,
                qr.getToken()
        );
        assertThat(methodsResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode list = objectMapper.readTree(methodsResp.getBody()).at("/data");
        assertThat(list.isArray()).isTrue();
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(0).at("/code").asText()).isIn("CASH", "TPA");

        // tentar iniciar pagamento gateway deve falhar (APPYPAY inativo)
        Long pedidoId = criarPedido(qr.getToken(), "idem-order-0001", prod.getId());
        String payPayload = """
                { "metodoPagamento": "REF", "telefone": "+244900000000" }
                """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Idempotency-Key", "idem-pay-00000001");
        HttpEntity<String> entity = new HttpEntity<>(payPayload, headers);

        ResponseEntity<String> r = restTemplate.postForEntity(
                "/api/public/q/{token}/pedidos/{pedidoId}/pagamentos",
                entity, String.class, qr.getToken(), pedidoId
        );
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private Tenant criarTenant(String nome, String slug, String code) {
        Tenant t = new Tenant();
        t.setNome(nome);
        t.setSlug(slug);
        t.setTenantCode(code);
        t.setEstado(TenantEstado.ATIVO);
        t.setTipo(TenantTipo.VENDEDOR_RUA);
        return tenantRepository.save(t);
    }

    private Instituicao criarInstituicao(Tenant tenant, String nome, String sigla) {
        Instituicao i = new Instituicao();
        i.setTenant(tenant);
        i.setNome(nome);
        i.setSigla(sigla);
        i.setNif("NIF-" + sigla);
        i.setTelefoneAutorizacao("+244900010001");
        return instituicaoRepository.save(i);
    }

    private UnidadeAtendimento criarUnidade(Instituicao inst, String nome, TipoUnidadeAtendimento tipo) {
        UnidadeAtendimento ua = new UnidadeAtendimento();
        ua.setInstituicao(inst);
        ua.setNome(nome);
        ua.setTipo(tipo);
        return unidadeAtendimentoRepository.save(ua);
    }

    private CategoriaProduto criarCategoria(Tenant tenant, String nome, String slug) {
        CategoriaProduto c = new CategoriaProduto();
        c.setTenant(tenant);
        c.setNome(nome);
        c.setSlug(slug);
        return categoriaProdutoRepository.save(c);
    }

    private Produto criarProduto(Tenant tenant, CategoriaProduto cat, String code, String nome, BigDecimal preco) {
        Produto p = new Produto();
        p.setTenant(tenant);
        p.setCategoria(CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA);
        p.setCategoriaProduto(cat);
        p.setCodigo(code);
        p.setNome(nome);
        p.setPreco(preco);
        p.setAtivo(true);
        return produtoRepository.save(p);
    }

    private Long criarPedido(String token, String idempotencyKey, Long produtoId) throws Exception {
        String payload = """
                {
                  "itens": [
                    { "produtoId": %d, "quantidade": 1 }
                  ]
                }
                """.formatted(produtoId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Idempotency-Key", idempotencyKey);
        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/api/public/q/{token}/pedidos",
                new HttpEntity<>(payload, headers),
                String.class,
                token
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(resp.getBody()).at("/data/pedidoId").asLong();
    }
}
