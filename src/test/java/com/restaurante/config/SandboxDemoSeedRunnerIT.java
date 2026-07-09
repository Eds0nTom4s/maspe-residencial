package com.restaurante.config;

import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.model.enums.TurnoOperacionalStatus;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.QrCodeOperacionalRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantCardapioConfigRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.main.web-application-type=none",
                "consuma.sandbox.demo-seed.enabled=true",
                "sandbox.admin.password=SandboxAdmin@2026",
                "jwt.secret=0123456789012345678901234567890123456789012345678901234567890123",
                "consuma.device.token-hash-secret=0123456789012345678901234567890123456789012345678901234567890123",
                "consuma.sync.cursor.hmac-secret=0123456789012345678901234567890123456789012345678901234567890123",
                "consuma.financeiro.snapshot-integridade.keys.platform-snapshot-key-v1.secret=0123456789012345678901234567890123456789012345678901234567890123",
                "consuma.evidence.tx-ledger.dev-hmac-secret=0123456789012345678901234567890123456789012345678901234567890123",
                "app.payment.appypay.webhook-secret=0123456789012345678901234567890123456789012345678901234567890123"
        }
)
@ActiveProfiles({"it-postgres", "sandbox"})
class SandboxDemoSeedRunnerIT extends PostgresTestcontainersConfig {

    @Autowired SandboxDemoSeedRunner runner;
    @Autowired TenantRepository tenantRepository;
    @Autowired CategoriaProdutoRepository categoriaProdutoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired QrCodeOperacionalRepository qrCodeOperacionalRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired PagamentoGatewayRepository pagamentoGatewayRepository;
    @Autowired OrdemPagamentoRepository ordemPagamentoRepository;
    @Autowired TenantCardapioConfigRepository tenantCardapioConfigRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired TurnoOperacionalRepository turnoOperacionalRepository;
    @Autowired UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired Environment environment;

    @Test
    void seedCreatesRestAndPontoTenantsAndCanRunAgain() {
        runner.seedDemoTenants();

        Tenant rest = tenantRepository.findBySlug(SandboxDemoSeedRunner.REST_SLUG).orElseThrow();
        Tenant ponto = tenantRepository.findBySlug(SandboxDemoSeedRunner.PONTO_SLUG).orElseThrow();
        Tenant freezy = tenantRepository.findBySlug(SandboxDemoSeedRunner.FREEZY_SLUG).orElseThrow();

        assertThat(rest.getTemplateCode()).isEqualTo("CONSUMA_REST");
        assertThat(ponto.getTemplateCode()).isEqualTo("CONSUMA_PONTO");
        assertThat(freezy.getNome()).isEqualTo("FREEZY");
        assertThat(freezy.getTemplateCode()).isEqualTo("CONSUMA_PONTO");
        assertThat(freezy.getTemplateVersion()).isEqualTo(1);

        assertThat(categoriaProdutoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(rest.getId())).hasSizeGreaterThanOrEqualTo(4);
        assertThat(categoriaProdutoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(ponto.getId())).hasSizeGreaterThanOrEqualTo(3);
        assertThat(categoriaProdutoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(freezy.getId()))
                .extracting("nome")
                .containsExactly("Gelados", "Bebidas", "Combos");
        assertThat(produtoRepository.findByTenantIdAndDisponivelTrueAndAtivoTrue(rest.getId())).hasSizeGreaterThanOrEqualTo(4);
        assertThat(produtoRepository.findByTenantIdAndDisponivelTrueAndAtivoTrue(ponto.getId())).hasSizeGreaterThanOrEqualTo(3);
        assertThat(produtoRepository.findByTenantIdAndDisponivelTrueAndAtivoTrue(freezy.getId()))
                .extracting("nome")
                .containsExactlyInAnyOrder(
                        "Gelado de Baunilha",
                        "Gelado de Chocolate",
                        "Gelado de Morango",
                        "Água",
                        "Refrigerante",
                        "Combo FREEZY"
                );
        assertThat(qrCodeOperacionalRepository.findByTenantIdAndAtivoTrueAndRevogadoFalse(rest.getId())).isNotEmpty();
        assertThat(qrCodeOperacionalRepository.findByTenantIdAndAtivoTrueAndRevogadoFalse(ponto.getId())).isNotEmpty();
        assertThat(qrCodeOperacionalRepository.findByTenantIdAndAtivoTrueAndRevogadoFalse(freezy.getId()))
                .anySatisfy(qr -> {
                    assertThat(qr.getNome()).isEqualTo("QR Público FREEZY");
                    assertThat(qr.getMesa()).isNull();
                    assertThat(qr.getUnidadeAtendimento()).isNotNull();
                });

        assertThat(unidadeAtendimentoRepository.findByTenantId(freezy.getId()))
                .anySatisfy(unidade -> assertThat(unidade.getNome()).isEqualTo("FREEZY Principal"));
        assertThat(tenantCardapioConfigRepository.findByTenantId(freezy.getId()))
                .hasValueSatisfying(config -> assertThat(config.getCardapioPublicado()).isTrue());
        Long freezyOperatorUserId = tenantUserRepository.findByTenantIdWithUser(freezy.getId()).stream()
                .filter(tu -> SandboxDemoSeedRunner.FREEZY_OPERATOR_EMAIL.equals(tu.getUser().getEmail()))
                .findFirst()
                .orElseThrow()
                .getUser()
                .getId();
        assertThat(tenantUserRepository.existsByTenantIdAndUserIdAndRoleAndEstado(
                freezy.getId(),
                freezyOperatorUserId,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserEstado.ATIVO
        )).isTrue();
        assertThat(turnoOperacionalRepository.findOpenIdsByTenantAndOptionalScope(
                freezy.getId(),
                null,
                null,
                java.util.List.of(TurnoOperacionalStatus.ABERTO, TurnoOperacionalStatus.EM_FECHO)
        )).hasSize(1);

        assertThat(pedidoRepository.findByTenantId(rest.getId(), org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
                .isGreaterThanOrEqualTo(2);
        assertThat(pedidoRepository.findByTenantId(ponto.getId(), org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
                .isGreaterThanOrEqualTo(2);
        assertThat(pedidoRepository.findByTenantId(freezy.getId(), org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
                .isZero();
        assertThat(pedidoRepository.findByTenantId(rest.getId(), org.springframework.data.domain.Pageable.unpaged()).stream()
                .anyMatch(p -> p.getStatusFinanceiro() == StatusFinanceiroPedido.PAGO)).isTrue();
        assertThat(pedidoRepository.findByTenantId(ponto.getId(), org.springframework.data.domain.Pageable.unpaged()).stream()
                .anyMatch(p -> p.getStatusFinanceiro() == StatusFinanceiroPedido.PAGO)).isTrue();
        assertThat(pagamentoGatewayRepository.findAll()).noneMatch(p -> p.getTenant().getId().equals(freezy.getId()));
        assertThat(ordemPagamentoRepository.findAll()).noneMatch(o -> o.getTenant().getId().equals(freezy.getId()));
        assertThat(pagamentoGatewayRepository.findAll()).isNotEmpty();
        assertThat(environment.getProperty("consuma.demo.freezy.payment-order-expiration-minutes", Integer.class))
                .isEqualTo(SandboxDemoSeedRunner.FREEZY_PAYMENT_ORDER_EXPIRATION_MINUTES);
    }
}
