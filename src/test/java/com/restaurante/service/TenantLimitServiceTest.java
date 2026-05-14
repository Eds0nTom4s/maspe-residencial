package com.restaurante.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.Subscricao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantLimiteOverride;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.SubscricaoRepository;
import com.restaurante.repository.TenantLimiteOverrideRepository;
import com.restaurante.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantLimitServiceTest {

    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private SubscricaoRepository subscricaoRepository;
    @Mock
    private TenantLimiteOverrideRepository tenantLimiteOverrideRepository;
    @Mock
    private InstituicaoRepository instituicaoRepository;

    @InjectMocks
    private TenantLimitService tenantLimitService;

    @Test
    void shouldFailWhenTenantHasNoActiveSubscription() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setEstado(TenantEstado.ATIVO);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(subscricaoRepository.findByTenantIdAndEstado(1L, SubscricaoEstado.ATIVA)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> tenantLimitService.getEffectiveLimits(1L));
        assertTrue(ex.getMessage().contains("subscrição ativa"));
    }

    @Test
    void shouldUseOverrideWhenPresentOtherwisePlan() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setEstado(TenantEstado.ATIVO);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        Plano plano = new Plano();
        plano.setCodigo("PILOTO");
        plano.setNome("Plano Piloto");
        plano.setPrecoMensal(BigDecimal.ZERO);
        plano.setMaxInstituicoes(1);
        plano.setMaxUnidadesAtendimento(3);
        plano.setMaxProdutos(100);
        plano.setMaxUsuarios(5);
        plano.setMaxQrCodes(10);
        plano.setMaxDispositivos(3);

        Subscricao subs = new Subscricao();
        subs.setTenant(tenant);
        subs.setPlano(plano);
        subs.setEstado(SubscricaoEstado.ATIVA);
        subs.setInicioEm(LocalDate.now());
        when(subscricaoRepository.findByTenantIdAndEstado(1L, SubscricaoEstado.ATIVA)).thenReturn(Optional.of(subs));

        TenantLimiteOverride override = new TenantLimiteOverride();
        override.setMaxInstituicoes(3);
        override.setMaxProdutos(null);
        when(tenantLimiteOverrideRepository.findByTenantIdAndAtivoTrue(1L)).thenReturn(Optional.of(override));

        TenantLimitService.EffectiveTenantLimits limits = tenantLimitService.getEffectiveLimits(1L);
        assertEquals(3, limits.maxInstituicoes());
        assertEquals(100, limits.maxProdutos());
    }

    @Test
    void shouldBlockWhenInstituicaoLimitExceeded() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setEstado(TenantEstado.ATIVO);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));

        Plano plano = new Plano();
        plano.setCodigo("PILOTO");
        plano.setNome("Plano Piloto");
        plano.setPrecoMensal(BigDecimal.ZERO);
        plano.setMaxInstituicoes(1);
        plano.setMaxUnidadesAtendimento(3);
        plano.setMaxProdutos(100);
        plano.setMaxUsuarios(5);
        plano.setMaxQrCodes(10);
        plano.setMaxDispositivos(3);

        Subscricao subs = new Subscricao();
        subs.setTenant(tenant);
        subs.setPlano(plano);
        subs.setEstado(SubscricaoEstado.ATIVA);
        subs.setInicioEm(LocalDate.now());
        when(subscricaoRepository.findByTenantIdAndEstado(1L, SubscricaoEstado.ATIVA)).thenReturn(Optional.of(subs));

        when(tenantLimiteOverrideRepository.findByTenantIdAndAtivoTrue(1L)).thenReturn(Optional.empty());
        when(instituicaoRepository.countByTenantId(1L)).thenReturn(1L);

        assertThrows(BusinessException.class, () -> tenantLimitService.assertCanCreateInstituicao(1L));
    }
}

