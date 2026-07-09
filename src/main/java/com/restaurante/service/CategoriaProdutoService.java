package com.restaurante.service;

import com.restaurante.dto.request.CategoriaProdutoRequest;
import com.restaurante.dto.response.CategoriaProdutoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoriaProdutoService {

    private final CategoriaProdutoRepository categoriaProdutoRepository;
    private final TenantRepository tenantRepository;
    private final TenantGuard tenantGuard;
    private final TenantLimitService tenantLimitService;
    private final OperationalEventLogService operationalEventLogService;
    private final ProdutoRepository produtoRepository;

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
        tenantLimitService.assertCanCreateCategoriaProduto(tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + tenantId));

        CategoriaProduto cp = new CategoriaProduto();
        cp.setTenant(tenant);
        cp.setNome(request.getNome());
        cp.setSlug(request.getSlug());
        cp.setDescricao(request.getDescricao());
        cp.setOrdem(request.getOrdem() != null ? request.getOrdem() : 0);
        cp.setAtivo(true);

        CategoriaProduto saved = categoriaProdutoRepository.save(cp);
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.CATEGORIA_CRIADA,
                OperationalEntityType.CATEGORIA_PRODUTO,
                saved.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Categoria de produto criada",
                Map.of("slug", saved.getSlug(), "nome", saved.getNome()),
                null,
                null
        );
        return mapToResponse(saved);
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
    public List<CategoriaProdutoResponse> listarTodasDoTenant() {
        Long tenantId = requireTenantId("listagem de categorias");
        return categoriaProdutoRepository.findByTenantId(tenantId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoriaProdutoResponse buscarResponsePorIdDoTenant(Long id) {
        return mapToResponse(buscarPorIdDoTenant(id));
    }

    @Transactional(readOnly = true)
    public CategoriaProduto buscarPorIdDoTenant(Long id) {
        Long tenantId = requireTenantId("leitura de categoria");
        return categoriaProdutoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("CategoriaProduto", "id", id));
    }

    @Transactional
    public CategoriaProdutoResponse atualizarTenantAware(Long id, CategoriaProdutoRequest request) {
        Long tenantId = requireTenantId("atualização de categoria");
        CategoriaProduto cp = categoriaProdutoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("CategoriaProduto", "id", id));

        categoriaProdutoRepository.findBySlugAndTenantId(request.getSlug(), tenantId)
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new BusinessException("Já existe uma categoria com o slug: " + request.getSlug() + " neste tenant.");
                    }
                });

        cp.setNome(request.getNome());
        cp.setSlug(request.getSlug());
        cp.setDescricao(request.getDescricao());
        cp.setOrdem(request.getOrdem() != null ? request.getOrdem() : 0);

        CategoriaProduto saved = categoriaProdutoRepository.save(cp);
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.CATEGORIA_ATUALIZADA,
                OperationalEntityType.CATEGORIA_PRODUTO,
                saved.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Categoria de produto atualizada",
                Map.of("slug", saved.getSlug(), "nome", saved.getNome()),
                null,
                null
        );
        return mapToResponse(saved);
    }

    @Transactional
    public CategoriaProdutoResponse alterarAtivoTenantAware(Long id, boolean ativo) {
        Long tenantId = requireTenantId("alteração de estado da categoria");
        CategoriaProduto cp = categoriaProdutoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("CategoriaProduto", "id", id));
        cp.setAtivo(ativo);
        CategoriaProduto saved = categoriaProdutoRepository.save(cp);
        operationalEventLogService.logGenericForTenant(
                tenantId,
                ativo ? OperationalEventType.CATEGORIA_ATIVADA : OperationalEventType.CATEGORIA_DESATIVADA,
                OperationalEntityType.CATEGORIA_PRODUTO,
                saved.getId(),
                OperationalOrigem.TENANT_ADMIN,
                ativo ? "Categoria de produto ativada" : "Categoria de produto desativada",
                Map.of("slug", saved.getSlug(), "nome", saved.getNome(), "ativo", saved.getAtivo()),
                null,
                null
        );
        return mapToResponse(saved);
    }

    @Transactional
    public void desativarTenantAware(Long id) {
        Long tenantId = requireTenantId("remoção de categoria");
        CategoriaProduto cp = categoriaProdutoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("CategoriaProduto", "id", id));
        long produtosAtivos = produtoRepository.countByTenantIdAndCategoriaProdutoIdAndAtivoTrue(tenantId, id);
        if (produtosAtivos > 0) {
            throw new BusinessException("Não é possível desativar categoria com produtos ativos vinculados.");
        }
        cp.setAtivo(false);
        categoriaProdutoRepository.save(cp);
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.CATEGORIA_DESATIVADA,
                OperationalEntityType.CATEGORIA_PRODUTO,
                cp.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Categoria de produto removida logicamente",
                Map.of("slug", cp.getSlug(), "nome", cp.getNome()),
                null,
                null
        );
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

    private Long requireTenantId(String operacao) {
        Long tenantId = TenantContextHolder.require().tenantId();
        if (tenantId == null) {
            throw new BusinessException("TenantContext ausente para " + operacao + ".");
        }
        tenantGuard.assertTenantActive(tenantId);
        tenantGuard.assertCurrentUserBelongsToTenant(tenantId);
        return tenantId;
    }
}
