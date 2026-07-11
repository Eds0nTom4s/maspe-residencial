package com.restaurante.service.operacional;

import com.restaurante.exception.OperationalCapabilityDisabledException;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantOperacaoPolicy;
import com.restaurante.model.entity.TenantOperationalModulesConfig;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.repository.TenantOperacaoPolicyRepository;
import com.restaurante.repository.TenantOperationalModulesConfigRepository;
import com.restaurante.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalCapabilitiesPolicyTest {

    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final TenantOperacaoPolicyRepository operacaoPolicyRepository = mock(TenantOperacaoPolicyRepository.class);
    private final TenantOperationalModulesConfigRepository modulesRepository = mock(TenantOperationalModulesConfigRepository.class);
    private final OperationalCapabilitiesPolicy policy = new OperationalCapabilitiesPolicy(
            tenantRepository,
            operacaoPolicyRepository,
            modulesRepository,
            new OperationalTemplatePolicy()
    );

    @BeforeEach
    void setUp() {
        when(operacaoPolicyRepository.findByTenantId(1L)).thenReturn(Optional.empty());
        when(modulesRepository.findByTenantId(1L)).thenReturn(Optional.empty());
    }

    @Test
    void pontoSemConfiguracaoTemFallbackFechado() {
        Tenant tenant = tenant("CONSUMA_PONTO", 1);

        OperationalCapabilitiesPolicy.Capabilities capabilities = policy.resolve(tenant);

        assertThat(capabilities.productionEnabled()).isFalse();
        assertThat(capabilities.kdsEnabled()).isFalse();
        assertThat(capabilities.pontoOperation()).isTrue();
        assertThat(policy.canDeliverWithoutReady(pedido(tenant))).isTrue();
    }

    @Test
    void pontoRespeitaHabilitacaoExplicitaPersistida() {
        Tenant tenant = tenant("CONSUMA_PONTO", 1);
        TenantOperacaoPolicy operacao = operacao(tenant, true, true);
        TenantOperationalModulesConfig modules = modules(tenant, true);
        when(operacaoPolicyRepository.findByTenantId(1L)).thenReturn(Optional.of(operacao));
        when(modulesRepository.findByTenantId(1L)).thenReturn(Optional.of(modules));

        OperationalCapabilitiesPolicy.Capabilities capabilities = policy.resolve(tenant);

        assertThat(capabilities.productionEnabled()).isTrue();
        assertThat(capabilities.kdsEnabled()).isTrue();
        assertThat(policy.canDeliverWithoutReady(pedido(tenant))).isFalse();
    }

    @Test
    void kdsNuncaFicaActivoQuandoProducaoEstaDesactivada() {
        Tenant tenant = tenant("CONSUMA_PONTO_V1", 1);
        when(operacaoPolicyRepository.findByTenantId(1L))
                .thenReturn(Optional.of(operacao(tenant, false, true)));
        when(modulesRepository.findByTenantId(1L))
                .thenReturn(Optional.of(modules(tenant, true)));

        OperationalCapabilitiesPolicy.Capabilities capabilities = policy.resolve(tenant);

        assertThat(capabilities.productionEnabled()).isFalse();
        assertThat(capabilities.kdsEnabled()).isFalse();
    }

    @Test
    void restSemConfiguracaoPreservaFallbackLegado() {
        Tenant tenant = tenant("CONSUMA_REST", 1);

        OperationalCapabilitiesPolicy.Capabilities capabilities = policy.resolve(tenant);

        assertThat(capabilities.productionEnabled()).isTrue();
        assertThat(capabilities.kdsEnabled()).isTrue();
    }

    @Test
    void vendedorRuaSemConfiguracaoTambemTemFallbackPontoFechado() {
        Tenant tenant = tenant("RESTAURANTE_SIMPLES", 1);
        tenant.setTipo(TenantTipo.VENDEDOR_RUA);

        OperationalCapabilitiesPolicy.Capabilities capabilities = policy.resolve(tenant);

        assertThat(capabilities.pontoOperation()).isTrue();
        assertThat(capabilities.productionEnabled()).isFalse();
        assertThat(capabilities.kdsEnabled()).isFalse();
    }

    @Test
    void comandoKdsDesactivadoRetornaErroDeDominioEstruturado() {
        Tenant tenant = tenant("CONSUMA_PONTO", 1);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> policy.assertKdsEnabled(1L))
                .isInstanceOf(OperationalCapabilityDisabledException.class)
                .extracting("code")
                .isEqualTo("KDS_DISABLED_FOR_OPERATION");
    }

    private Tenant tenant(String templateCode, int templateVersion) {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setTemplateCode(templateCode);
        tenant.setTemplateVersion(templateVersion);
        return tenant;
    }

    private TenantOperacaoPolicy operacao(Tenant tenant, boolean productionEnabled, boolean kdsEnabled) {
        TenantOperacaoPolicy operacao = new TenantOperacaoPolicy();
        operacao.setTenant(tenant);
        operacao.setProductionEnabled(productionEnabled);
        operacao.setKdsEnabled(kdsEnabled);
        return operacao;
    }

    private TenantOperationalModulesConfig modules(Tenant tenant, boolean kdsEnabled) {
        TenantOperationalModulesConfig modules = new TenantOperationalModulesConfig();
        modules.setTenant(tenant);
        modules.setKdsEnabled(kdsEnabled);
        return modules;
    }

    private Pedido pedido(Tenant tenant) {
        Pedido pedido = new Pedido();
        pedido.setTenant(tenant);
        return pedido;
    }
}
