package com.restaurante.billing;

import com.restaurante.billing.service.UsageMeteringService;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.TipoPagamentoFinanceiro;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.UsageEvent;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "consuma.billing.enabled=true"
})
public class UsageMeteringServiceTest {

    @Autowired private UsageMeteringService meteringService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Autowired private PagamentoGatewayRepository pagamentoGatewayRepository;

    @Test
    @Transactional
    void registraPaymentConfirmedEIdempotente() {
        Tenant tenant = criarTenant();
        Instituicao inst = criarInstituicao(tenant);
        UnidadeAtendimento ua = criarUnidade(inst);
        Pagamento pg = criarPagamentoConfirmado(tenant);

        UsageEvent e1 = meteringService.recordPaymentConfirmed(tenant.getId(), ua.getId(), pg.getId(), LocalDateTime.now());
        UsageEvent e2 = meteringService.recordPaymentConfirmed(tenant.getId(), ua.getId(), pg.getId(), LocalDateTime.now());

        assertThat(e1.getId()).isNotNull();
        assertThat(e2.getId()).isEqualTo(e1.getId());
        assertThat(e1.getIdempotencyKey()).isEqualTo(UsageMeteringService.idempotencyKeyForPaymentConfirmed(tenant.getId(), pg.getId()));
    }

    private Tenant criarTenant() {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);
        Tenant t = new Tenant();
        t.setNome("Tenant Billing " + suffix);
        t.setSlug("tenant-billing-" + suffix);
        t.setTenantCode("B" + suffix);
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }

    private Instituicao criarInstituicao(Tenant tenant) {
        String suffix = String.valueOf(System.nanoTime() % 1_000_000);
        Instituicao i = new Instituicao();
        i.setTenant(tenant);
        i.setNome("Inst");
        i.setSigla("IB" + suffix);
        i.setNif("50" + suffix);
        i.setTelefoneAutorizacao("+2449" + suffix);
        i.setAtiva(true);
        return instituicaoRepository.saveAndFlush(i);
    }

    private UnidadeAtendimento criarUnidade(Instituicao inst) {
        UnidadeAtendimento u = new UnidadeAtendimento();
        u.setNome("UA");
        u.setTipo(com.restaurante.model.enums.TipoUnidadeAtendimento.RESTAURANTE);
        u.setAtiva(true);
        u.setInstituicao(inst);
        return unidadeAtendimentoRepository.saveAndFlush(u);
    }

    private Pagamento criarPagamentoConfirmado(Tenant tenant) {
        Pagamento pg = Pagamento.builder()
                .tenant(tenant)
                .tipoPagamento(TipoPagamentoFinanceiro.PRE_PAGO)
                .amount(new BigDecimal("10.00"))
                .status(StatusPagamentoGateway.PENDENTE)
                .observacoes("TEST")
                .build();
        pg.confirmar();
        return pagamentoGatewayRepository.saveAndFlush(pg);
    }
}
