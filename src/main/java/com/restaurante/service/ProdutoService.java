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

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service para operações de negócio com Produto
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProdutoService {

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

        Produto produto = Produto.builder()
                .codigo(request.getCodigo())
                .nome(request.getNome())
                .descricao(request.getDescricao())
                .preco(request.getPreco())
                .categoria(request.getCategoria())
                .urlImagem(request.getUrlImagem())
                .tempoPreparoMinutos(request.getTempoPreparoMinutos())
                .disponivel(request.getDisponivel() != null ? request.getDisponivel() : true)
                .ativo(true)
                .build();

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
     * Busca todos os produtos disponíveis
     */
    @Transactional(readOnly = true)
    public List<ProdutoResponse> listarDisponiveis() {
        log.info("Listando produtos disponíveis");
        return produtoRepository.findByDisponivelTrueAndAtivoTrue()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Busca produtos por categoria
     */
    @Transactional(readOnly = true)
    public List<ProdutoResponse> listarPorCategoria(CategoriaProduto categoria) {
        log.info("Listando produtos da categoria: {}", categoria);
        return produtoRepository.findByCategoriaAndDisponivelTrueAndAtivoTrue(categoria)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Busca produtos por nome (busca parcial)
     */
    @Transactional(readOnly = true)
    public List<ProdutoResponse> buscarPorNome(String nome) {
        log.info("Buscando produtos com nome: {}", nome);
        return produtoRepository.findByNomeContainingIgnoreCaseAndDisponivelTrueAndAtivoTrue(nome)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
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
        return ProdutoResponse.builder()
                .id(produto.getId())
                .codigo(produto.getCodigo())
                .nome(produto.getNome())
                .descricao(produto.getDescricao())
                .preco(produto.getPreco())
                .categoria(produto.getCategoria())
                .urlImagem(produto.getUrlImagem())
                .tempoPreparoMinutos(produto.getTempoPreparoMinutos())
                .disponivel(produto.getDisponivel())
                .ativo(produto.getAtivo())
                .createdAt(produto.getCreatedAt())
                .updatedAt(produto.getUpdatedAt())
                .build();
    }
}
