package com.restaurante.cardapio;

import com.restaurante.dto.request.AtualizarCardapioAparenciaRequest;
import com.restaurante.dto.request.AtualizarLimitesCardapioRequest;
import com.restaurante.dto.response.PlatformCardapioLimitsResponse;
import com.restaurante.dto.response.TenantCardapioStatusResponse;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.service.storage.StorageService;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantCardapioConfig;
import com.restaurante.model.entity.TenantLimiteOverride;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantCardapioConfigRepository;
import com.restaurante.repository.TenantLimiteOverrideRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.service.TenantCardapioConfigService;
import com.restaurante.service.TenantLimitService;
import com.restaurante.service.operacional.OperationalEventLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantCardapioConfigServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock TenantCardapioConfigRepository tenantCardapioConfigRepository;
    @Mock TenantLimiteOverrideRepository tenantLimiteOverrideRepository;
    @Mock CategoriaProdutoRepository categoriaProdutoRepository;
    @Mock ProdutoRepository produtoRepository;
    @Mock TenantLimitService tenantLimitService;
    @Mock OperationalEventLogService operationalEventLogService;
    @Mock StorageService storageService;
    @InjectMocks TenantCardapioConfigService service;

    @Test
    void status_bloqueiaPublicacaoSemCategoriaOuProdutoDisponivel() {
        Tenant tenant = tenant(10L);
        TenantCardapioConfig config = config(tenant, false);
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant));
        when(tenantCardapioConfigRepository.findByTenantId(10L)).thenReturn(Optional.of(config));
        when(tenantLimitService.getEffectiveLimits(10L)).thenReturn(limits(10L, 8, 40));
        when(categoriaProdutoRepository.countByTenantIdAndAtivoTrue(10L)).thenReturn(0L);
        when(produtoRepository.countByTenantIdAndAtivoTrue(10L)).thenReturn(0L);
        when(produtoRepository.countByTenantIdAndDisponivelTrueAndAtivoTrue(10L)).thenReturn(0L);

        TenantCardapioStatusResponse status = service.status(10L);

        assertThat(status.getPublicado()).isFalse();
        assertThat(status.getPodePublicar()).isFalse();
        assertThat(status.getBloqueios()).contains(
                "Nenhuma categoria ativa cadastrada.",
                "Nenhum produto ativo e disponível cadastrado."
        );
    }

    @Test
    void alterarLimitesPlatform_rejeitaReducaoAbaixoDoUsoAtual() {
        Tenant tenant = tenant(10L);
        AtualizarLimitesCardapioRequest request = new AtualizarLimitesCardapioRequest();
        request.setMaxCategorias(2);
        request.setMaxProdutos(40);
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant));
        when(categoriaProdutoRepository.countByTenantIdAndAtivoTrue(10L)).thenReturn(3L);
        when(produtoRepository.countByTenantIdAndAtivoTrue(10L)).thenReturn(6L);

        assertThatThrownBy(() -> service.alterarLimitesPlatform(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Novo limite de categorias");
    }

    @Test
    void atualizarAparencia_atualizaUrlBannerEMaxItensPorPedido() {
        Tenant tenant = tenant(10L);
        TenantCardapioConfig config = config(tenant, false);
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant));
        when(tenantCardapioConfigRepository.findByTenantId(10L)).thenReturn(Optional.of(config));
        when(tenantCardapioConfigRepository.saveAndFlush(any(TenantCardapioConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantLimitService.getEffectiveLimits(10L)).thenReturn(limits(10L, 8, 40));
        when(categoriaProdutoRepository.countByTenantIdAndAtivoTrue(10L)).thenReturn(1L);
        when(produtoRepository.countByTenantIdAndAtivoTrue(10L)).thenReturn(1L);
        when(produtoRepository.countByTenantIdAndDisponivelTrueAndAtivoTrue(10L)).thenReturn(1L);
        TenantContextHolder.set(new TenantContext(10L, "tenant-10", 1L, java.util.Set.of("TENANT_OWNER"), com.restaurante.security.tenant.TenantResolutionSource.JWT, false, false));

        AtualizarCardapioAparenciaRequest request = new AtualizarCardapioAparenciaRequest();
        request.setUrlBanner("https://cdn.exemplo.com/banner.jpg");
        request.setMaxItensPorPedido(12);

        TenantCardapioStatusResponse status = service.atualizarAparenciaCurrentTenant(request);

        assertThat(status.getUrlBanner()).isEqualTo("https://cdn.exemplo.com/banner.jpg");
        assertThat(status.getMaxItensPorPedido()).isEqualTo(12);
        assertThat(config.getUrlBanner()).isEqualTo("https://cdn.exemplo.com/banner.jpg");
        assertThat(config.getMaxItensPorPedido()).isEqualTo(12);
        TenantContextHolder.clear();
    }

    @Test
    void uploadBanner_fazUploadNoStorageEAtualizaConfig() {
        Tenant tenant = tenant(10L);
        TenantCardapioConfig config = config(tenant, false);
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile("file", "banner.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant));
        when(tenantCardapioConfigRepository.findByTenantId(10L)).thenReturn(Optional.of(config));
        when(tenantCardapioConfigRepository.saveAndFlush(any(TenantCardapioConfig.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantLimitService.getEffectiveLimits(10L)).thenReturn(limits(10L, 8, 40));
        when(categoriaProdutoRepository.countByTenantIdAndAtivoTrue(10L)).thenReturn(1L);
        when(produtoRepository.countByTenantIdAndAtivoTrue(10L)).thenReturn(1L);
        when(produtoRepository.countByTenantIdAndDisponivelTrueAndAtivoTrue(10L)).thenReturn(1L);
        when(storageService.uploadFile(any(MultipartFile.class), any())).thenReturn("https://minio/banner.jpg");
        TenantContextHolder.set(new TenantContext(10L, "tenant-10", 1L, java.util.Set.of("TENANT_OWNER"), com.restaurante.security.tenant.TenantResolutionSource.JWT, false, false));

        TenantCardapioStatusResponse status = service.uploadBannerCurrentTenant(file);

        assertThat(status.getUrlBanner()).isEqualTo("https://minio/banner.jpg");
        assertThat(config.getUrlBanner()).isEqualTo("https://minio/banner.jpg");
        verify(storageService).uploadFile(file, "cardapio-banners");
        TenantContextHolder.clear();
    }

    @Test
    void alterarLimitesPlatform_atualizaOverrideExistenteEAudita() {
        Tenant tenant = tenant(10L);
        TenantLimiteOverride override = new TenantLimiteOverride();
        override.setId(77L);
        override.setTenant(tenant);
        override.setAtivo(true);
        AtualizarLimitesCardapioRequest request = new AtualizarLimitesCardapioRequest();
        request.setMaxCategorias(8);
        request.setMaxProdutos(40);
        request.setMotivo("fase cardapio");
        request.setConfiguradoPor("platform-admin");
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(tenant));
        when(categoriaProdutoRepository.countByTenantIdAndAtivoTrue(10L)).thenReturn(3L);
        when(produtoRepository.countByTenantIdAndAtivoTrue(10L)).thenReturn(6L);
        when(tenantLimiteOverrideRepository.findByTenantIdAndAtivoTrue(10L)).thenReturn(Optional.of(override));
        when(tenantLimiteOverrideRepository.saveAndFlush(any(TenantLimiteOverride.class))).thenAnswer(inv -> inv.getArgument(0));

        PlatformCardapioLimitsResponse response = service.alterarLimitesPlatform(10L, request);

        assertThat(response.getMaxCategorias()).isEqualTo(8);
        assertThat(response.getMaxProdutos()).isEqualTo(40);
        ArgumentCaptor<TenantLimiteOverride> captor = ArgumentCaptor.forClass(TenantLimiteOverride.class);
        verify(tenantLimiteOverrideRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getMotivo()).isEqualTo("fase cardapio");
        verify(operationalEventLogService).logGenericForTenant(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private Tenant tenant(Long id) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setNome("Tenant");
        tenant.setTelefone("+244900000000");
        tenant.setEstado(TenantEstado.ATIVO);
        return tenant;
    }

    private TenantCardapioConfig config(Tenant tenant, boolean publicado) {
        TenantCardapioConfig config = new TenantCardapioConfig();
        config.setId(55L);
        config.setTenant(tenant);
        config.setCardapioPublicado(publicado);
        return config;
    }

    private TenantLimitService.EffectiveTenantLimits limits(Long tenantId, int maxCategorias, int maxProdutos) {
        return new TenantLimitService.EffectiveTenantLimits(tenantId, 1, 1, maxProdutos, maxCategorias, 1, 1, 1);
    }
}
