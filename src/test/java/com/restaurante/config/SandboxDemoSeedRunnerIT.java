package com.restaurante.config;

import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.QrCodeOperacionalRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test
    void seedCreatesRestAndPontoTenantsAndCanRunAgain() {
        runner.seedDemoTenants();

        Tenant rest = tenantRepository.findBySlug(SandboxDemoSeedRunner.REST_SLUG).orElseThrow();
        Tenant ponto = tenantRepository.findBySlug(SandboxDemoSeedRunner.PONTO_SLUG).orElseThrow();

        assertThat(rest.getTemplateCode()).isEqualTo("CONSUMA_REST");
        assertThat(ponto.getTemplateCode()).isEqualTo("CONSUMA_PONTO");

        assertThat(categoriaProdutoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(rest.getId())).hasSizeGreaterThanOrEqualTo(4);
        assertThat(categoriaProdutoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(ponto.getId())).hasSizeGreaterThanOrEqualTo(3);
        assertThat(produtoRepository.findByTenantIdAndDisponivelTrueAndAtivoTrue(rest.getId())).hasSizeGreaterThanOrEqualTo(4);
        assertThat(produtoRepository.findByTenantIdAndDisponivelTrueAndAtivoTrue(ponto.getId())).hasSizeGreaterThanOrEqualTo(3);
        assertThat(qrCodeOperacionalRepository.findByTenantIdAndAtivoTrueAndRevogadoFalse(rest.getId())).isNotEmpty();
        assertThat(qrCodeOperacionalRepository.findByTenantIdAndAtivoTrueAndRevogadoFalse(ponto.getId())).isNotEmpty();

        assertThat(pedidoRepository.findByTenantId(rest.getId(), org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
                .isGreaterThanOrEqualTo(2);
        assertThat(pedidoRepository.findByTenantId(ponto.getId(), org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
                .isGreaterThanOrEqualTo(2);
        assertThat(pedidoRepository.findByTenantId(rest.getId(), org.springframework.data.domain.Pageable.unpaged()).stream()
                .anyMatch(p -> p.getStatusFinanceiro() == StatusFinanceiroPedido.PAGO)).isTrue();
        assertThat(pedidoRepository.findByTenantId(ponto.getId(), org.springframework.data.domain.Pageable.unpaged()).stream()
                .anyMatch(p -> p.getStatusFinanceiro() == StatusFinanceiroPedido.PAGO)).isTrue();
        assertThat(pagamentoGatewayRepository.findAll()).isNotEmpty();
    }
}
