package com.restaurante.cardapio;

import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.Subscricao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.DispositivoOperacionalRepository;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.QrCodeOperacionalRepository;
import com.restaurante.repository.SubscricaoRepository;
import com.restaurante.repository.TenantLimiteOverrideRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.service.TenantLimitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantLimitCardapioTest {

    @Mock TenantRepository tenantRepository;
    @Mock SubscricaoRepository subscricaoRepository;
    @Mock TenantLimiteOverrideRepository tenantLimiteOverrideRepository;
    @Mock InstituicaoRepository instituicaoRepository;
    @Mock UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    @Mock CategoriaProdutoRepository categoriaProdutoRepository;
    @Mock ProdutoRepository produtoRepository;
    @Mock TenantUserRepository tenantUserRepository;
    @Mock QrCodeOperacionalRepository qrCodeOperacionalRepository;
    @Mock DispositivoOperacionalRepository dispositivoOperacionalRepository;
    @InjectMocks TenantLimitService service;

    @Test
    void assertCanCreateCategoriaProduto_bloqueiaQuandoLimiteAtingido() {
        mockPlanoAtivo(10L, 2, 20);
        when(categoriaProdutoRepository.countByTenantIdAndAtivoTrue(10L)).thenReturn(2L);

        assertThatThrownBy(() -> service.assertCanCreateCategoriaProduto(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Limite de categorias");
    }

    @Test
    void assertCanCreateProduto_bloqueiaQuandoLimiteAtingido() {
        mockPlanoAtivo(10L, 5, 3);
        when(produtoRepository.countByTenantIdAndAtivoTrue(10L)).thenReturn(3L);

        assertThatThrownBy(() -> service.assertCanCreateProduto(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Limite de produtos");
    }

    private void mockPlanoAtivo(Long tenantId, int maxCategorias, int maxProdutos) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setEstado(TenantEstado.ATIVO);
        Plano plano = new Plano();
        plano.setCodigo("PILOTO");
        plano.setNome("Piloto");
        plano.setPrecoMensal(BigDecimal.ZERO);
        plano.setMaxInstituicoes(1);
        plano.setMaxUnidadesAtendimento(1);
        plano.setMaxProdutos(maxProdutos);
        plano.setMaxCategorias(maxCategorias);
        plano.setMaxUsuarios(2);
        plano.setMaxQrCodes(2);
        plano.setMaxDispositivos(2);
        Subscricao subscricao = new Subscricao();
        subscricao.setTenant(tenant);
        subscricao.setPlano(plano);
        subscricao.setEstado(SubscricaoEstado.ATIVA);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(subscricaoRepository.findByTenantIdAndEstado(tenantId, SubscricaoEstado.ATIVA)).thenReturn(Optional.of(subscricao));
        when(tenantLimiteOverrideRepository.findByTenantIdAndAtivoTrue(tenantId)).thenReturn(Optional.empty());
    }
}
