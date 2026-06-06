package com.restaurante.config;

import com.restaurante.businesstemplate.BusinessTemplateService;
import com.restaurante.businesstemplate.dto.BusinessTemplateProvisionRequest;
import com.restaurante.dto.request.PublicQrPedidoItemRequest;
import com.restaurante.dto.request.PublicQrPedidoRequest;
import com.restaurante.dto.response.PublicQrPedidoResponse;
import com.restaurante.financeiro.service.OrdemPagamentoService;
import com.restaurante.inventory.service.TenantInventoryPolicyService;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.OrdemPagamento;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.QrCodeOperacional;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.model.enums.MetodoPagamentoManual;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.CozinhaRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.QrCodeOperacionalRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantResolutionSource;
import com.restaurante.service.PublicQrPedidoService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
@Profile("sandbox")
@ConditionalOnProperty(prefix = "consuma.sandbox.demo-seed", name = "enabled", havingValue = "true")
@Order(110)
@RequiredArgsConstructor
public class SandboxDemoSeedRunner {

    private static final Logger log = LoggerFactory.getLogger(SandboxDemoSeedRunner.class);

    public static final String REST_SLUG = "consuma-rest-demo";
    public static final String PONTO_SLUG = "consuma-ponto-demo";
    public static final String REST_CODE = "CONSUMA_REST_DEMO";
    public static final String PONTO_CODE = "CONSUMA_PONTO_DEMO";
    public static final String OWNER_EMAIL = "demo@consuma.local";
    public static final String OWNER_PASSWORD = "DemoConsuma@2026";

    private final BusinessTemplateService businessTemplateService;
    private final TenantRepository tenantRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final CategoriaProdutoRepository categoriaProdutoRepository;
    private final ProdutoRepository produtoRepository;
    private final QrCodeOperacionalRepository qrCodeOperacionalRepository;
    private final CozinhaRepository cozinhaRepository;
    private final PublicQrPedidoService publicQrPedidoService;
    private final OrdemPagamentoService ordemPagamentoService;
    private final PedidoRepository pedidoRepository;
    private final TenantInventoryPolicyService tenantInventoryPolicyService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedOnReady() {
        seedDemoTenants();
    }

    @Transactional
    public void seedDemoTenants() {
        Tenant rest = ensureTenant("CONSUMA_REST_V1", restRequest(), REST_SLUG);
        Tenant ponto = ensureTenant("CONSUMA_PONTO_V1", pontoRequest(), PONTO_SLUG);

        disableStockControlForDemo(rest);
        disableStockControlForDemo(ponto);

        ensureOperationalKitchens(rest);
        ensureOperationalKitchens(ponto);

        List<Produto> restProducts = ensureRestCatalog(rest);
        List<Produto> pontoProducts = ensurePontoCatalog(ponto);

        seedOrder(rest, restProducts, "demo-rest-pago", true, MetodoPagamentoManual.TPA);
        seedOrder(rest, restProducts, "demo-rest-aberto", false, null);
        seedOrder(ponto, pontoProducts, "demo-ponto-pago", true, MetodoPagamentoManual.CASH);
        seedOrder(ponto, pontoProducts, "demo-ponto-aberto", false, null);

        log.info("[sandbox-demo-seed] tenants demo garantidos: restSlug={}, pontoSlug={}", REST_SLUG, PONTO_SLUG);
    }

    private Tenant ensureTenant(String templateCode, BusinessTemplateProvisionRequest request, String slug) {
        return tenantRepository.findBySlug(slug).orElseGet(() -> {
            Optional<TenantContext> previous = TenantContextHolder.get();
            try {
                TenantContextHolder.set(new TenantContext(
                        null, null, 0L, Set.of("ROLE_ADMIN"),
                        TenantResolutionSource.JWT, true, false
                ));
                businessTemplateService.provision(templateCode, request);
                return tenantRepository.findBySlug(slug)
                        .orElseThrow(() -> new IllegalStateException("Tenant demo não criado: " + slug));
            } finally {
                if (previous.isPresent()) {
                    TenantContextHolder.set(previous.get());
                } else {
                    TenantContextHolder.clear();
                }
            }
        });
    }

    private void disableStockControlForDemo(Tenant tenant) {
        var policy = tenantInventoryPolicyService.getOrCreateDefault(tenant);
        policy.setStockControlEnabled(Boolean.FALSE);
    }

    private BusinessTemplateProvisionRequest restRequest() {
        return BusinessTemplateProvisionRequest.builder()
                .planoCodigo("PILOTO")
                .tenant(BusinessTemplateProvisionRequest.TenantInfo.builder()
                        .nomeNegocio("CONSUMA Rest Demo")
                        .slug(REST_SLUG)
                        .tenantCode(REST_CODE)
                        .tipo(TenantTipo.RESTAURANTE)
                        .telefone("+244930000101")
                        .email("rest.demo@consuma.local")
                        .build())
                .owner(owner())
                .rest(BusinessTemplateProvisionRequest.RestOptions.builder()
                        .temMesas(true)
                        .quantidadeMesas(6)
                        .gerarQrPorMesa(true)
                        .temBarSeparado(true)
                        .exigeTurnoAberto(false)
                        .entrega(BusinessTemplateProvisionRequest.RestDeliveryOption.MANUAL)
                        .build())
                .build();
    }

    private BusinessTemplateProvisionRequest pontoRequest() {
        return BusinessTemplateProvisionRequest.builder()
                .planoCodigo("PILOTO")
                .tenant(BusinessTemplateProvisionRequest.TenantInfo.builder()
                        .nomeNegocio("CONSUMA Ponto Demo")
                        .slug(PONTO_SLUG)
                        .tenantCode(PONTO_CODE)
                        .tipo(TenantTipo.VENDEDOR_RUA)
                        .telefone("+244930000202")
                        .email("ponto.demo@consuma.local")
                        .build())
                .owner(owner())
                .ponto(BusinessTemplateProvisionRequest.PontoOptions.builder()
                        .entregaManual(true)
                        .allowPickup(true)
                        .build())
                .build();
    }

    private BusinessTemplateProvisionRequest.OwnerInfo owner() {
        return BusinessTemplateProvisionRequest.OwnerInfo.builder()
                .nome("Operador Demo CONSUMA")
                .telefone("+244930000000")
                .email(OWNER_EMAIL)
                .senhaTemporaria(OWNER_PASSWORD)
                .build();
    }

    private void ensureOperationalKitchens(Tenant tenant) {
        List<UnidadeAtendimento> unidades = unidadeAtendimentoRepository.findByTenantId(tenant.getId());
        for (UnidadeAtendimento ua : unidades) {
            if ("CONSUMA_REST".equals(tenant.getTemplateCode())) {
                linkKitchen(ua, TipoCozinha.CENTRAL, "Demo " + tenant.getTenantCode() + " Cozinha Central");
                linkKitchen(ua, TipoCozinha.BAR_PREP, "Demo " + tenant.getTenantCode() + " Bar");
                linkKitchen(ua, TipoCozinha.CONFEITARIA, "Demo " + tenant.getTenantCode() + " Confeitaria");
            } else {
                linkKitchen(ua, TipoCozinha.CENTRAL, "Demo " + tenant.getTenantCode() + " Preparação");
                linkKitchen(ua, TipoCozinha.BAR_PREP, "Demo " + tenant.getTenantCode() + " Bebidas");
            }
            unidadeAtendimentoRepository.saveAndFlush(ua);
        }
    }

    private void linkKitchen(UnidadeAtendimento ua, TipoCozinha tipo, String nome) {
        Cozinha cozinha = cozinhaRepository.findByNomeIgnoreCase(nome)
                .orElseGet(() -> {
                    Cozinha c = new Cozinha();
                    c.setNome(nome);
                    c.setTipo(tipo);
                    c.setAtiva(true);
                    c.setDescricao("Recurso operacional criado pelo seed demo sandbox.");
                    return cozinhaRepository.saveAndFlush(c);
                });
        ua.adicionarCozinha(cozinha);
    }

    private List<Produto> ensureRestCatalog(Tenant tenant) {
        CategoriaProduto comidas = ensureCategory(tenant, "Comidas", "comidas", 10);
        CategoriaProduto bebidas = ensureCategory(tenant, "Bebidas", "bebidas", 20);
        CategoriaProduto sobremesas = ensureCategory(tenant, "Sobremesas", "sobremesas", 30);
        ensureCategory(tenant, "Promoções", "promocoes", 40);
        return List.of(
                ensureProduct(tenant, comidas, "REST-MUAMBA", "Muamba de Galinha", "Prato demo CONSUMA Rest", "4500.00", CategoriaProdutoLegacy.PRATO_PRINCIPAL, 25),
                ensureProduct(tenant, comidas, "REST-FUNGE", "Funge com Calulu", "Especialidade angolana demo", "3800.00", CategoriaProdutoLegacy.PRATO_PRINCIPAL, 20),
                ensureProduct(tenant, bebidas, "REST-SUMO", "Sumo Natural", "Bebida demo", "1200.00", CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA, 5),
                ensureProduct(tenant, sobremesas, "REST-MOUSSE", "Mousse de Maracujá", "Sobremesa demo", "1500.00", CategoriaProdutoLegacy.SOBREMESA, 8)
        );
    }

    private List<Produto> ensurePontoCatalog(Tenant tenant) {
        CategoriaProduto geral = ensureCategory(tenant, "Geral", "geral", 10);
        CategoriaProduto destaques = ensureCategory(tenant, "Destaques", "destaques", 20);
        CategoriaProduto promocoes = ensureCategory(tenant, "Promoções", "promocoes", 30);
        return List.of(
                ensureProduct(tenant, geral, "PONTO-COXINHA", "Coxinha", "Lanche demo CONSUMA Ponto", "700.00", CategoriaProdutoLegacy.LANCHE, 8),
                ensureProduct(tenant, destaques, "PONTO-SANDES", "Sandes Mista", "Produto destaque demo", "1200.00", CategoriaProdutoLegacy.LANCHE, 10),
                ensureProduct(tenant, promocoes, "PONTO-SUMO", "Sumo Fresco", "Bebida demo", "600.00", CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA, 3)
        );
    }

    private CategoriaProduto ensureCategory(Tenant tenant, String nome, String slug, int ordem) {
        String normalized = slug.toLowerCase(Locale.ROOT);
        return categoriaProdutoRepository.findBySlugAndTenantId(normalized, tenant.getId())
                .orElseGet(() -> {
                    CategoriaProduto c = new CategoriaProduto();
                    c.setTenant(tenant);
                    c.setNome(nome);
                    c.setSlug(normalized);
                    c.setOrdem(ordem);
                    c.setAtivo(true);
                    return categoriaProdutoRepository.saveAndFlush(c);
                });
    }

    private Produto ensureProduct(Tenant tenant,
                                  CategoriaProduto categoria,
                                  String codigo,
                                  String nome,
                                  String descricao,
                                  String preco,
                                  CategoriaProdutoLegacy legacy,
                                  int tempoPreparoMinutos) {
        return produtoRepository.findByCodigoAndTenantId(codigo, tenant.getId())
                .orElseGet(() -> {
                    Produto p = new Produto();
                    p.setTenant(tenant);
                    p.setCategoriaProduto(categoria);
                    p.setCategoria(legacy);
                    p.setCodigo(codigo);
                    p.setNome(nome);
                    p.setDescricao(descricao);
                    p.setPreco(new BigDecimal(preco));
                    p.setTempoPreparoMinutos(tempoPreparoMinutos);
                    p.setDisponivel(true);
                    p.setAtivo(true);
                    return produtoRepository.saveAndFlush(p);
                });
    }

    private void seedOrder(Tenant tenant,
                           List<Produto> produtos,
                           String suffix,
                           boolean confirmarPagamento,
                           MetodoPagamentoManual metodoPagamento) {
        String idempotencyKey = tenant.getTenantCode() + "-" + suffix;
        PublicQrPedidoResponse response = publicQrPedidoService.criarPedidoPublicoPorQrToken(
                findPrincipalQr(tenant).getToken(),
                idempotencyKey,
                PublicQrPedidoRequest.builder()
                        .idempotencyKey(idempotencyKey)
                        .clienteNome("Cliente Demo")
                        .clienteTelefone("+244930123456")
                        .observacao("Pedido criado pelo seed demo sandbox.")
                        .itens(List.of(
                                PublicQrPedidoItemRequest.builder()
                                        .produtoId(produtos.get(0).getId())
                                        .quantidade(1)
                                        .build(),
                                PublicQrPedidoItemRequest.builder()
                                        .produtoId(produtos.get(Math.min(1, produtos.size() - 1)).getId())
                                        .quantidade(1)
                                        .build()
                        ))
                        .build()
        );

        if (confirmarPagamento) {
            Pedido pedido = pedidoRepository.findByIdAndTenantId(response.getPedidoId(), tenant.getId())
                    .orElseThrow(() -> new IllegalStateException("Pedido demo não encontrado: " + response.getPedidoId()));
            if (pedido.getStatusFinanceiro() != StatusFinanceiroPedido.PAGO) {
                var sessao = pedido.getSessaoConsumo();
                var unidade = sessao != null ? sessao.getUnidadeAtendimentoEfetiva() : null;
                OrdemPagamento ordem = ordemPagamentoService.criarOrdemPagamentoPedido(
                        tenant,
                        sessao != null ? sessao.getInstituicao() : null,
                        unidade,
                        sessao != null ? sessao.getMesa() : null,
                        pedido.getTurnoOperacional(),
                        pedido,
                        metodoPagamento,
                        OperationalOrigem.QR_PUBLICO,
                        "127.0.0.1",
                        "sandbox-demo-seed"
                );
                ordemPagamentoService.aplicarConfirmacaoManualOrdem(
                        ordem,
                        metodoPagamento,
                        ordem.getValor(),
                        "SANDBOX-DEMO-SEED",
                        "Confirmação criada pelo seed demo sandbox."
                );
            }
        }
    }

    private QrCodeOperacional findPrincipalQr(Tenant tenant) {
        return qrCodeOperacionalRepository.findByTenantIdAndAtivoTrueAndRevogadoFalse(tenant.getId())
                .stream()
                .filter(q -> q.getUnidadeAtendimento() != null && q.getMesa() == null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("QR principal demo não encontrado para tenant " + tenant.getSlug()));
    }
}
