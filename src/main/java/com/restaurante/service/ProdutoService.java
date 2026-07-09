package com.restaurante.service;

import com.restaurante.dto.request.ProdutoRequest;
import com.restaurante.dto.response.ProdutoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
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
import lombok.extern.slf4j.Slf4j;
import com.restaurante.service.storage.StorageService;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service para operações de negócio com Produto
 */
@Service
@RequiredArgsConstructor
public class ProdutoService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProdutoService.class);

    private final ProdutoRepository produtoRepository;
    private final CategoriaProdutoRepository categoriaProdutoRepository;
    private final CategoriaProdutoService categoriaProdutoService;
    private final TenantRepository tenantRepository;
    private final TenantGuard tenantGuard;
    private final StorageService storageService;
    private final TenantLimitService tenantLimitService;
    private final OperationalEventLogService operationalEventLogService;

    private static final String LEGACY_TENANT_CODE = "LEGACY";


    /**
     * [Tenant-aware] Cria produto usando TenantContext obrigatório (sem fallback LEGACY).
     */
    @Transactional
    public ProdutoResponse criarTenantAware(ProdutoRequest request) {
        Long tenantId = TenantContextHolder.require().tenantId();
        if (tenantId == null) {
            throw new BusinessException("TenantContext ausente para criação de produto.");
        }
        tenantGuard.assertTenantActive(tenantId);
        tenantGuard.assertCurrentUserBelongsToTenant(tenantId);

        if (produtoRepository.existsByCodigoAndTenantId(request.getCodigo(), tenantId)) {
            throw new BusinessException("Já existe um produto com o código: " + request.getCodigo() + " neste tenant.");
        }
        tenantLimitService.assertCanCreateProduto(tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + tenantId));

        Produto produto = new Produto();
        produto.setTenant(tenant);
        produto.setCodigo(request.getCodigo());
        produto.setNome(request.getNome());
        produto.setDescricao(request.getDescricao());
        produto.setPreco(request.getPreco());
        CategoriaProduto categoriaProduto = resolveCategoriaProdutoTenantAware(tenantId, request);
        produto.setCategoriaProduto(categoriaProduto);
        produto.setCategoria(resolveLegacyEnumForCompat(request.getCategoria(), categoriaProduto));
        produto.setUrlImagem(request.getUrlImagem());
        produto.setTempoPreparoMinutos(request.getTempoPreparoMinutos());
        produto.setDisponivel(request.getDisponivel() != null ? request.getDisponivel() : true);
        produto.setAtivo(true);

        Produto saved = produtoRepository.save(produto);
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.PRODUTO_CRIADO,
                OperationalEntityType.PRODUTO,
                saved.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Produto criado",
                Map.of("codigo", saved.getCodigo(), "nome", saved.getNome()),
                null,
                null
        );
        return mapToResponse(saved);
    }

    /**
     * [Tenant-aware] Lista produtos disponíveis do tenant atual (TenantContext obrigatório).
     */
    @Transactional(readOnly = true)
    public Page<ProdutoResponse> listarDisponiveisDoTenant(Pageable pageable) {
        Long tenantId = requireTenantId("listagem de produtos");
        return produtoRepository.findByTenantIdAndDisponivelTrueAndAtivoTrue(tenantId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<ProdutoResponse> listarTodosDoTenant(Pageable pageable) {
        Long tenantId = requireTenantId("listagem de produtos");
        return produtoRepository.findByTenantId(tenantId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<ProdutoResponse> listarDisponiveisDoTenantPorCategoriaProduto(Long categoriaProdutoId, Pageable pageable) {
        Long tenantId = requireTenantId("listagem de produtos");
        return produtoRepository.findByTenantIdAndCategoriaProdutoIdAndDisponivelTrueAndAtivoTrue(tenantId, categoriaProdutoId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public ProdutoResponse buscarPorIdDoTenant(Long id) {
        Long tenantId = requireTenantId("leitura de produto");
        return mapToResponse(buscarPorIdTenantAware(id, tenantId));
    }

    @Transactional
    public ProdutoResponse atualizarTenantAware(Long id, ProdutoRequest request) {
        Long tenantId = requireTenantId("atualização de produto");
        Produto produto = buscarPorIdTenantAware(id, tenantId);

        produtoRepository.findByCodigoAndTenantId(request.getCodigo(), tenantId)
                .ifPresent(p -> {
                    if (!p.getId().equals(id)) {
                        throw new BusinessException("Já existe outro produto com o código: " + request.getCodigo() + " neste tenant.");
                    }
                });

        CategoriaProduto categoriaProduto = resolveCategoriaProdutoTenantAware(tenantId, request);
        produto.setCodigo(request.getCodigo());
        produto.setNome(request.getNome());
        produto.setDescricao(request.getDescricao());
        produto.setPreco(request.getPreco());
        produto.setCategoriaProduto(categoriaProduto);
        produto.setCategoria(resolveLegacyEnumForCompat(request.getCategoria(), categoriaProduto));
        produto.setUrlImagem(request.getUrlImagem());
        produto.setTempoPreparoMinutos(request.getTempoPreparoMinutos());
        if (request.getDisponivel() != null) {
            produto.setDisponivel(request.getDisponivel());
        }

        Produto saved = produtoRepository.save(produto);
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.PRODUTO_ATUALIZADO,
                OperationalEntityType.PRODUTO,
                saved.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Produto atualizado",
                Map.of("codigo", saved.getCodigo(), "nome", saved.getNome()),
                null,
                null
        );
        return mapToResponse(saved);
    }

    @Transactional
    public ProdutoResponse alterarDisponibilidadeTenantAware(Long id, Boolean disponivel) {
        Long tenantId = requireTenantId("alteração de disponibilidade de produto");
        Produto produto = buscarPorIdTenantAware(id, tenantId);
        produto.setDisponivel(Boolean.TRUE.equals(disponivel));
        Produto saved = produtoRepository.save(produto);
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.PRODUTO_DISPONIBILIDADE_ALTERADA,
                OperationalEntityType.PRODUTO,
                saved.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Disponibilidade do produto alterada",
                Map.of("codigo", saved.getCodigo(), "disponivel", saved.getDisponivel()),
                null,
                null
        );
        return mapToResponse(saved);
    }

    @Transactional
    public ProdutoResponse alterarAtivoTenantAware(Long id, Boolean ativo) {
        Long tenantId = requireTenantId("alteração de estado de produto");
        Produto produto = buscarPorIdTenantAware(id, tenantId);
        boolean novoAtivo = Boolean.TRUE.equals(ativo);
        produto.setAtivo(novoAtivo);
        if (!novoAtivo) {
            produto.setDisponivel(false);
        }
        Produto saved = produtoRepository.save(produto);
        operationalEventLogService.logGenericForTenant(
                tenantId,
                novoAtivo ? OperationalEventType.PRODUTO_ATIVADO : OperationalEventType.PRODUTO_DESATIVADO,
                OperationalEntityType.PRODUTO,
                saved.getId(),
                OperationalOrigem.TENANT_ADMIN,
                novoAtivo ? "Produto ativado" : "Produto desativado",
                Map.of("codigo", saved.getCodigo(), "ativo", saved.getAtivo(), "disponivel", saved.getDisponivel()),
                null,
                null
        );
        return mapToResponse(saved);
    }

    @Transactional
    public void desativarTenantAware(Long id) {
        alterarAtivoTenantAware(id, false);
    }

    @Transactional
    public ProdutoResponse atualizarImagemTenantAware(Long id, MultipartFile file) {
        Long tenantId = requireTenantId("atualização de imagem de produto");
        Produto produto = buscarPorIdTenantAware(id, tenantId);
        if (produto.getUrlImagem() != null && !produto.getUrlImagem().isEmpty()) {
            storageService.deleteFile(produto.getUrlImagem());
        }
        String urlImagem = storageService.uploadFile(file, "produtos");
        produto.setUrlImagem(urlImagem);
        Produto saved = produtoRepository.save(produto);
        operationalEventLogService.logGenericForTenant(
                tenantId,
                OperationalEventType.PRODUTO_IMAGEM_ATUALIZADA,
                OperationalEntityType.PRODUTO,
                saved.getId(),
                OperationalOrigem.TENANT_ADMIN,
                "Imagem do produto atualizada",
                Map.of("codigo", saved.getCodigo(), "urlImagem", saved.getUrlImagem()),
                null,
                null
        );
        return mapToResponse(saved);
    }

    /**
     * Cria um novo produto
     */
    @Transactional
    public ProdutoResponse criar(ProdutoRequest request) {
        Tenant tenant = resolveTenantForWriteOperation();
        log.info("Criando novo produto (tenantId={}): {}", tenant.getId(), request.getCodigo());

        if (produtoRepository.existsByCodigoAndTenantId(request.getCodigo(), tenant.getId())) {
            throw new BusinessException("Já existe um produto com o código: " + request.getCodigo() + " neste tenant.");
        }

        if (request.getCategoria() == null) {
            throw new BusinessException("Categoria (legado) é obrigatória para endpoints legados.");
        }

        Produto produto = new Produto();
        produto.setTenant(tenant);
        produto.setCodigo(request.getCodigo());
        produto.setNome(request.getNome());
        produto.setDescricao(request.getDescricao());
        produto.setPreco(request.getPreco());
        produto.setCategoria(request.getCategoria());
        produto.setCategoriaProduto(resolveCategoriaProdutoFromLegacyEnum(tenant.getId(), request.getCategoria()));
        produto.setUrlImagem(request.getUrlImagem());
        produto.setImagensGaleria(request.getImagensGaleria());
        produto.setTempoPreparoMinutos(request.getTempoPreparoMinutos());
        produto.setDisponivel(request.getDisponivel() != null ? request.getDisponivel() : true);
        produto.setAtivo(true);

        produto = produtoRepository.save(produto);
        log.info("Produto criado com sucesso - ID: {}", produto.getId());

        return mapToResponse(produto);
    }

    /**
     * Atualiza um produto existente
     */
    @Transactional
    public ProdutoResponse atualizar(Long id, ProdutoRequest request) {
        Tenant tenant = resolveTenantForWriteOperation();
        log.info("Atualizando produto ID: {} (tenantId={})", id, tenant.getId());

        Produto produto = buscarPorIdTenantAware(id, tenant.getId());

        // Verifica se o código já existe em outro produto
        produtoRepository.findByCodigoAndTenantId(request.getCodigo(), tenant.getId())
                .ifPresent(p -> {
                    if (!p.getId().equals(id)) {
                        throw new BusinessException("Já existe outro produto com o código: " + request.getCodigo() + " neste tenant.");
                    }
                });

        produto.setCodigo(request.getCodigo());
        produto.setNome(request.getNome());
        produto.setDescricao(request.getDescricao());
        produto.setPreco(request.getPreco());
        if (request.getCategoria() == null) {
            throw new BusinessException("Categoria (legado) é obrigatória para endpoints legados.");
        }
        produto.setCategoria(request.getCategoria());
        produto.setCategoriaProduto(resolveCategoriaProdutoFromLegacyEnum(tenant.getId(), request.getCategoria()));
        produto.setUrlImagem(request.getUrlImagem());
        
        if (request.getImagensGaleria() != null) {
            produto.setImagensGaleria(request.getImagensGaleria());
        }
        
        produto.setTempoPreparoMinutos(request.getTempoPreparoMinutos());
        
        if (request.getDisponivel() != null) {
            produto.setDisponivel(request.getDisponivel());
        }

        produto = produtoRepository.save(produto);
        log.info("Produto atualizado com sucesso - ID: {}", produto.getId());

        return mapToResponse(produto);
    }

    /**
     * Busca produto por ID
     */
    @Transactional(readOnly = true)
    public Produto buscarPorId(Long id) {
        Long tenantId = resolveTenantIdForRead().orElseGet(this::resolveLegacyTenantId);
        return buscarPorIdTenantAware(id, tenantId);
    }

    /**
     * Busca todos os produtos disponíveis com paginação
     */
    @Transactional(readOnly = true)
    public Page<ProdutoResponse> listarDisponiveis(Pageable pageable) {
        Long tenantId = resolveTenantIdForRead().orElseGet(this::resolveLegacyTenantId);
        log.info("Listando produtos disponíveis (tenantId={}) - Página: {}", tenantId, pageable.getPageNumber());
        return produtoRepository.findByTenantIdAndDisponivelTrueAndAtivoTrue(tenantId, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Lista TODOS os produtos (incluindo inativos/indisponíveis) — uso admin.
     */
    @Transactional(readOnly = true)
    public Page<ProdutoResponse> listarTodos(Pageable pageable) {
        Long tenantId = resolveTenantIdForRead().orElseGet(this::resolveLegacyTenantId);
        log.info("Listando todos os produtos (admin) (tenantId={}) - Página: {}", tenantId, pageable.getPageNumber());
        return produtoRepository.findByTenantId(tenantId, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Retorna o ProdutoResponse por ID.
     */
    @Transactional(readOnly = true)
    public ProdutoResponse buscarPorIdResponse(Long id) {
        return mapToResponse(buscarPorId(id));
    }

    /**
     * Busca produtos por categoria com paginação
     */
    @Transactional(readOnly = true)
    public Page<ProdutoResponse> listarPorCategoria(CategoriaProdutoLegacy categoria, Pageable pageable) {
        Long tenantId = resolveTenantIdForRead().orElseGet(this::resolveLegacyTenantId);
        log.info("Listando produtos da categoria: {} (tenantId={}) - Página: {}", categoria, tenantId, pageable.getPageNumber());
        return produtoRepository.findByTenantIdAndCategoriaAndDisponivelTrueAndAtivoTrue(tenantId, categoria, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Busca produtos por nome (busca parcial) com paginação
     */
    @Transactional(readOnly = true)
    public Page<ProdutoResponse> buscarPorNome(String nome, Pageable pageable) {
        Long tenantId = resolveTenantIdForRead().orElseGet(this::resolveLegacyTenantId);
        log.info("Buscando produtos com nome: {} (tenantId={}) - Página: {}", nome, tenantId, pageable.getPageNumber());
        return produtoRepository.findByTenantIdAndNomeContainingIgnoreCaseAndDisponivelTrueAndAtivoTrue(tenantId, nome, pageable)
                .map(this::mapToResponse);
    }


    /**
     * Altera disponibilidade do produto
     */
    @Transactional
    public ProdutoResponse alterarDisponibilidade(Long id, Boolean disponivel) {
        Tenant tenant = resolveTenantForWriteOperation();
        log.info("Alterando disponibilidade do produto ID: {} para {} (tenantId={})", id, disponivel, tenant.getId());

        Produto produto = buscarPorIdTenantAware(id, tenant.getId());
        produto.setDisponivel(disponivel);
        produto = produtoRepository.save(produto);

        return mapToResponse(produto);
    }

    /**
     * Desativa um produto (soft delete)
     */
    @Transactional
    public void desativar(Long id) {
        Tenant tenant = resolveTenantForWriteOperation();
        log.info("Desativando produto ID: {} (tenantId={})", id, tenant.getId());

        Produto produto = buscarPorIdTenantAware(id, tenant.getId());
        produto.setAtivo(false);
        produto.setDisponivel(false);
        produtoRepository.save(produto);
    }

    /**
     * Faz o upload e atualiza a imagem do produto.
     * @param id ID do produto
     * @param file Arquivo binário da imagem
     * @return Produto atualizado
     */
    @Transactional
    public ProdutoResponse atualizarImagem(Long id, MultipartFile file) {
        Tenant tenant = resolveTenantForWriteOperation();
        log.info("Iniciando processo de upload de imagem para o produto ID: {} (tenantId={})", id, tenant.getId());

        Produto produto = buscarPorIdTenantAware(id, tenant.getId());
        
        // Se já tiver uma imagem, removemos a anterior do MinIO para poupar espaço
        if (produto.getUrlImagem() != null && !produto.getUrlImagem().isEmpty()) {
            log.debug("Removendo imagem antiga do produto: {}", produto.getUrlImagem());
            storageService.deleteFile(produto.getUrlImagem());
        }
        
        // Faz o novo upload
        String urlImagem = storageService.uploadFile(file, "produtos");
        
        // Atualiza o produto
        produto.setUrlImagem(urlImagem);
        produto = produtoRepository.save(produto);
        
        log.info("Imagem do produto {} atualizada com sucesso: {}", id, urlImagem);
        return mapToResponse(produto);
    }

    /**
     * Mapeia Produto para ProdutoResponse
     */
    private ProdutoResponse mapToResponse(Produto produto) {
        ProdutoResponse response = new ProdutoResponse();
        response.setId(produto.getId());
        response.setCodigo(produto.getCodigo());
        response.setNome(produto.getNome());
        response.setDescricao(produto.getDescricao());
        response.setPreco(produto.getPreco());
        response.setCategoria(produto.getCategoria());
        if (produto.getCategoriaProduto() != null) {
            response.setCategoriaProdutoId(produto.getCategoriaProduto().getId());
            response.setCategoriaProdutoNome(produto.getCategoriaProduto().getNome());
            response.setCategoriaProdutoSlug(produto.getCategoriaProduto().getSlug());
        }
        response.setUrlImagem(produto.getUrlImagem());
        response.setImagensGaleria(produto.getImagensGaleria());
        response.setTempoPreparoMinutos(produto.getTempoPreparoMinutos());
        response.setDisponivel(produto.getDisponivel());
        response.setAtivo(produto.getAtivo());
        response.setCreatedAt(produto.getCreatedAt());
        response.setUpdatedAt(produto.getUpdatedAt());
        return response;
    }

    private CategoriaProduto resolveCategoriaProdutoTenantAware(Long tenantId, ProdutoRequest request) {
        if (request.getCategoriaProdutoId() != null) {
            return categoriaProdutoRepository.findByIdAndTenantId(request.getCategoriaProdutoId(), tenantId)
                    .orElseThrow(() -> new BusinessException("CategoriaProduto não encontrada no tenant."));
        }
        return categoriaProdutoService.getOrCreateDefaultDoTenant(tenantId);
    }

    private CategoriaProduto resolveCategoriaProdutoFromLegacyEnum(Long tenantId, CategoriaProdutoLegacy legacy) {
        String slug = legacyEnumToSlug(legacy);
        return categoriaProdutoRepository.findBySlugAndTenantId(slug, tenantId)
                .orElseGet(() -> categoriaProdutoService.getOrCreateDefaultDoTenant(tenantId));
    }

    private CategoriaProdutoLegacy resolveLegacyEnumForCompat(CategoriaProdutoLegacy provided, CategoriaProduto categoriaProduto) {
        if (provided != null) {
            return provided;
        }
        // Compatibilidade: tenta derivar do slug; se não houver match, usa OUTROS.
        CategoriaProdutoLegacy derived = slugToLegacyEnum(categoriaProduto != null ? categoriaProduto.getSlug() : null);
        return derived != null ? derived : CategoriaProdutoLegacy.OUTROS;
    }

    private static String legacyEnumToSlug(CategoriaProdutoLegacy legacy) {
        return switch (legacy) {
            case ENTRADA -> "entrada";
            case PRATO_PRINCIPAL -> "prato-principal";
            case ACOMPANHAMENTO -> "acompanhamento";
            case SOBREMESA -> "sobremesa";
            case BEBIDA_ALCOOLICA -> "bebida-alcoolica";
            case BEBIDA_NAO_ALCOOLICA -> "bebida-nao-alcoolica";
            case LANCHE -> "lanche";
            case PIZZA -> "pizza";
            case OUTROS -> "outros";
        };
    }

    private static CategoriaProdutoLegacy slugToLegacyEnum(String slug) {
        if (slug == null) return null;
        return switch (slug) {
            case "entrada" -> CategoriaProdutoLegacy.ENTRADA;
            case "prato-principal" -> CategoriaProdutoLegacy.PRATO_PRINCIPAL;
            case "acompanhamento" -> CategoriaProdutoLegacy.ACOMPANHAMENTO;
            case "sobremesa" -> CategoriaProdutoLegacy.SOBREMESA;
            case "bebida-alcoolica" -> CategoriaProdutoLegacy.BEBIDA_ALCOOLICA;
            case "bebida-nao-alcoolica" -> CategoriaProdutoLegacy.BEBIDA_NAO_ALCOOLICA;
            case "lanche" -> CategoriaProdutoLegacy.LANCHE;
            case "pizza" -> CategoriaProdutoLegacy.PIZZA;
            case "outros" -> CategoriaProdutoLegacy.OUTROS;
            case "geral" -> CategoriaProdutoLegacy.OUTROS;
            default -> null;
        };
    }

    private Produto buscarPorIdTenantAware(Long id, Long tenantId) {
        return produtoRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto", "id", id));
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

    private java.util.Optional<Long> resolveTenantIdForRead() {
        return TenantContextHolder.get()
                .map(ctx -> ctx.tenantId())
                .filter(java.util.Objects::nonNull);
    }

    private Tenant resolveTenantForWriteOperation() {
        return TenantContextHolder.get()
                .map(ctx -> ctx.tenantId())
                .filter(java.util.Objects::nonNull)
                .map(this::resolveTenantValidatedForWrite)
                .orElseGet(() -> tenantRepository.findByTenantCode(LEGACY_TENANT_CODE)
                        .orElseThrow(() -> new BusinessException("Tenant LEGACY não encontrado para compatibilidade.")));
    }

    private Tenant resolveTenantValidatedForWrite(Long tenantId) {
        tenantGuard.assertTenantActive(tenantId);
        tenantGuard.assertCurrentUserBelongsToTenant(tenantId);
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("Tenant não encontrado: " + tenantId));
    }

    private Long resolveLegacyTenantId() {
        return tenantRepository.findByTenantCode(LEGACY_TENANT_CODE)
                .map(Tenant::getId)
                .orElseThrow(() -> new BusinessException("Tenant LEGACY não encontrado para compatibilidade."));
    }
}
