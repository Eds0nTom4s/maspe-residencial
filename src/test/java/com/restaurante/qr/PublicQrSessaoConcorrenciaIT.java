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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=servlet"
)
@ActiveProfiles("it-postgres")
class PublicQrSessaoConcorrenciaIT extends PostgresTestcontainersConfig {

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
    void qrMesa_parallelAnonimoAndPedido_keepsSingleOpenSession() throws Exception {
        Tenant tenant = criarTenant("Tenant Conc", "tenant-conc", "CONC");
        Instituicao inst = criarInstituicao(tenant, "Inst Conc", "IC", "NIF-IC-001", "+244900002011");
        UnidadeAtendimento ua = criarUnidade(inst, "UA Conc", TipoUnidadeAtendimento.RESTAURANTE);
        criarCozinhaVinculada(ua, "Cozinha Conc", TipoCozinha.CENTRAL);

        Mesa mesa = criarMesa(inst, ua, "Mesa Conc", 1, "QR-MESA-CONC-1");
        CategoriaProduto cat = criarCategoria(tenant, "Pratos", "pratos-conc");
        Produto prod = criarProduto(tenant, cat, "PRATO-CONC", "Prato Conc", new BigDecimal("25.00"));
        QrCodeOperacional qr = qrCodeOperacionalService.criarQr(
                tenant.getId(), inst.getId(), ua.getId(), mesa.getId(), QrCodeOperacionalTipo.MESA, "QR Mesa Conc"
        );

        int anonymousCalls = 3;
        int orderCalls = 3;
        int totalCalls = anonymousCalls + orderCalls;

        CountDownLatch ready = new CountDownLatch(totalCalls);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(totalCalls);
        List<Callable<CallResult>> tasks = new ArrayList<>();

        for (int i = 0; i < anonymousCalls; i++) {
            tasks.add(() -> {
                ready.countDown();
                await(start);
                ResponseEntity<String> resp = restTemplate.postForEntity(
                        "/public/q/{token}/consumos/anonimo",
                        new HttpEntity<>(new HttpHeaders()),
                        String.class,
                        qr.getToken()
                );
                return new CallResult(false, resp.getStatusCode(), resp.getBody());
            });
        }
        for (int i = 0; i < orderCalls; i++) {
            String idemKey = "idem-conc-" + i + "-" + System.nanoTime();
            String payload = """
                    { "itens": [ { "produtoId": %d, "quantidade": 1 } ] }
                    """.formatted(prod.getId());
            tasks.add(() -> {
                ready.countDown();
                await(start);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.add("Idempotency-Key", idemKey);
                ResponseEntity<String> resp = restTemplate.postForEntity(
                        "/public/q/{token}/pedidos",
                        new HttpEntity<>(payload, headers),
                        String.class,
                        qr.getToken()
                );
                return new CallResult(true, resp.getStatusCode(), resp.getBody());
            });
        }

        List<Future<CallResult>> futures = tasks.stream().map(executor::submit).toList();
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<CallResult> results = new ArrayList<>();
        for (Future<CallResult> future : futures) {
            results.add(future.get(20, TimeUnit.SECONDS));
        }
        executor.shutdownNow();

        assertThat(results).allSatisfy(result -> assertThat(result.status()).isEqualTo(HttpStatus.CREATED));

        List<SessaoConsumo> abertas = sessaoConsumoRepository.findAllByTenantIdAndMesaIdAndStatus(
                tenant.getId(),
                mesa.getId(),
                StatusSessaoConsumo.ABERTA
        );
        assertThat(abertas).hasSize(1);
        Long sessaoId = abertas.getFirst().getId();

        for (CallResult result : results) {
            JsonNode json = objectMapper.readTree(result.body());
            if (!result.order()) {
                assertThat(json.at("/data/id").asLong()).isEqualTo(sessaoId);
                continue;
            }
            Long pedidoId = json.at("/data/pedidoId").asLong();
            Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow();
            assertThat(pedido.getSessaoConsumo()).isNotNull();
            assertThat(pedido.getSessaoConsumo().getId()).isEqualTo(sessaoId);
        }
    }

    private static void await(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread interrompida durante teste de concorrência", e);
        }
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
        p.setCategoria(CategoriaProdutoLegacy.PRATO_PRINCIPAL);
        p.setCategoriaProduto(categoriaProduto);
        p.setDisponivel(true);
        p.setAtivo(true);
        return produtoRepository.saveAndFlush(p);
    }

    private record CallResult(boolean order, HttpStatusCode status, String body) {}
}
