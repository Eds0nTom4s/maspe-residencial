package com.restaurante.service;

import com.restaurante.dto.request.CategoriaProdutoRequest;
import com.restaurante.dto.response.CategoriaProdutoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaProdutoService {

    private final CategoriaProdutoRepository categoriaProdutoRepository;
    private final TenantRepository tenantRepository;
    private final TenantGuard tenantGuard;

    private static final String DEFAULT_SLUG = "geral";

    @Transactional
    public CategoriaProdutoResponse criarTenantAware(CategoriaProdutoRequest request) {
        Long tenantId = TenantContextHolder.require().tenantId();
        if (tenantId == null) {
            throw new BusinessException("TenantContext ausente para criação de categoria.");
        }
        tenantGuard.assertTenantActive(tenantId);
        tenantGuard.assertCurrentUserBelongsToTenant(tenantId);

        if (categoriaProdutoRepository.existsBySlugAndTenantId(request.getSlug(), tenantId)) {
            throw new BusinessException("Já existe uma categoria com o slug: " + request.getSlug() + " neste tenant.");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + tenantId));

        CategoriaProduto cp = new CategoriaProduto();
        cp.setTenant(tenant);
        cp.setNome(request.getNome());
        cp.setSlug(request.getSlug());
        cp.setDescricao(request.getDescricao());
        cp.setOrdem(request.getOrdem() != null ? request.getOrdem() : 0);
        cp.setAtivo(true);

        return mapToResponse(categoriaProdutoRepository.save(cp));
    }

    @Transactional(readOnly = true)
    public List<CategoriaProdutoResponse> listarAtivasDoTenant() {
        Long tenantId = TenantContextHolder.require().tenantId();
        if (tenantId == null) {
            throw new BusinessException("TenantContext ausente para listagem de categorias.");
        }
        tenantGuard.assertTenantActive(tenantId);
        tenantGuard.assertCurrentUserBelongsToTenant(tenantId);
        return categoriaProdutoRepository.findByTenantIdAndAtivoTrueOrderByOrdemAsc(tenantId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoriaProduto buscarPorIdDoTenant(Long id) {
        Long tenantId = TenantContextHolder.require().tenantId();
        if (tenantId == null) {
            throw new BusinessException("TenantContext ausente para leitura de categoria.");
        }
        tenantGuard.assertTenantActive(tenantId);
        tenantGuard.assertCurrentUserBelongsToTenant(tenantId);
        return categoriaProdutoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("CategoriaProduto", "id", id));
    }

    @Transactional
    public CategoriaProduto getOrCreateDefaultDoTenant(Long tenantId) {
        return categoriaProdutoRepository.findBySlugAndTenantId(DEFAULT_SLUG, tenantId)
                .orElseGet(() -> {
                    Tenant tenant = tenantRepository.findById(tenantId)
                            .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + tenantId));
                    CategoriaProduto cp = new CategoriaProduto();
                    cp.setTenant(tenant);
                    cp.setNome("Geral");
                    cp.setSlug(DEFAULT_SLUG);
                    cp.setDescricao("Categoria default para itens sem categoria explícita.");
                    cp.setOrdem(0);
                    cp.setAtivo(true);
                    return categoriaProdutoRepository.save(cp);
                });
    }

    private CategoriaProdutoResponse mapToResponse(CategoriaProduto cp) {
        CategoriaProdutoResponse r = new CategoriaProdutoResponse();
        r.setId(cp.getId());
        r.setNome(cp.getNome());
        r.setSlug(cp.getSlug());
        r.setDescricao(cp.getDescricao());
        r.setOrdem(cp.getOrdem());
        r.setAtivo(cp.getAtivo());
        r.setCreatedAt(cp.getCreatedAt());
        r.setUpdatedAt(cp.getUpdatedAt());
        return r;
    }
}
