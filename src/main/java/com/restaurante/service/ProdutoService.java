package com.restaurante.service;

import com.restaurante.dto.request.ProdutoRequest;
import com.restaurante.dto.response.ProdutoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.enums.CategoriaProduto;
import com.restaurante.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service para operações de negócio com Produto
 */
@Service
@RequiredArgsConstructor
public class ProdutoService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProdutoService.class);

    private final ProdutoRepository produtoRepository;

    /**
     * Cria um novo produto
     */
    @Transactional
    public ProdutoResponse criar(ProdutoRequest request) {
        log.info("Criando novo produto: {}", request.getCodigo());

        if (produtoRepository.existsByCodigo(request.getCodigo())) {
            throw new BusinessException("Já existe um produto com o código: " + request.getCodigo());
        }

        Produto produto = new Produto();
        produto.setCodigo(request.getCodigo());
        produto.setNome(request.getNome());
        produto.setDescricao(request.getDescricao());
        produto.setPreco(request.getPreco());
        produto.setCategoria(request.getCategoria());
        produto.setUrlImagem(request.getUrlImagem());
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
        log.info("Atualizando produto ID: {}", id);

        Produto produto = buscarPorId(id);

        // Verifica se o código já existe em outro produto
        produtoRepository.findByCodigo(request.getCodigo())
                .ifPresent(p -> {
                    if (!p.getId().equals(id)) {
                        throw new BusinessException("Já existe outro produto com o código: " + request.getCodigo());
                    }
                });

        produto.setCodigo(request.getCodigo());
        produto.setNome(request.getNome());
        produto.setDescricao(request.getDescricao());
        produto.setPreco(request.getPreco());
        produto.setCategoria(request.getCategoria());
        produto.setUrlImagem(request.getUrlImagem());
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
        return produtoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto", "id", id));
    }

    /**
     * Busca todos os produtos disponíveis com paginação
     */
    @Transactional(readOnly = true)
    public Page<ProdutoResponse> listarDisponiveis(Pageable pageable) {
        log.info("Listando produtos disponíveis - Página: {}", pageable.getPageNumber());
        return produtoRepository.findByDisponivelTrueAndAtivoTrue(pageable)
                .map(this::mapToResponse);
    }

    /**
     * Lista TODOS os produtos (incluindo inativos/indisponíveis) — uso admin.
     */
    @Transactional(readOnly = true)
    public Page<ProdutoResponse> listarTodos(Pageable pageable) {
        log.info("Listando todos os produtos (admin) - Página: {}", pageable.getPageNumber());
        return produtoRepository.findAll(pageable)
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
    public Page<ProdutoResponse> listarPorCategoria(CategoriaProduto categoria, Pageable pageable) {
        log.info("Listando produtos da categoria: {} - Página: {}", categoria, pageable.getPageNumber());
        return produtoRepository.findByCategoriaAndDisponivelTrueAndAtivoTrue(categoria, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Busca produtos por nome (busca parcial) com paginação
     */
    @Transactional(readOnly = true)
    public Page<ProdutoResponse> buscarPorNome(String nome, Pageable pageable) {
        log.info("Buscando produtos com nome: {} - Página: {}", nome, pageable.getPageNumber());
        return produtoRepository.findByNomeContainingIgnoreCaseAndDisponivelTrueAndAtivoTrue(nome, pageable)
                .map(this::mapToResponse);
    }


    /**
     * Altera disponibilidade do produto
     */
    @Transactional
    public ProdutoResponse alterarDisponibilidade(Long id, Boolean disponivel) {
        log.info("Alterando disponibilidade do produto ID: {} para {}", id, disponivel);
        
        Produto produto = buscarPorId(id);
        produto.setDisponivel(disponivel);
        produto = produtoRepository.save(produto);

        return mapToResponse(produto);
    }

    /**
     * Desativa um produto (soft delete)
     */
    @Transactional
    public void desativar(Long id) {
        log.info("Desativando produto ID: {}", id);
        
        Produto produto = buscarPorId(id);
        produto.setAtivo(false);
        produto.setDisponivel(false);
        produtoRepository.save(produto);
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
        response.setUrlImagem(produto.getUrlImagem());
        response.setTempoPreparoMinutos(produto.getTempoPreparoMinutos());
        response.setDisponivel(produto.getDisponivel());
        response.setAtivo(produto.getAtivo());
        response.setCreatedAt(produto.getCreatedAt());
        response.setUpdatedAt(produto.getUpdatedAt());
        return response;
    }
}
