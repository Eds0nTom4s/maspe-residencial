package com.restaurante.service;

import com.restaurante.dto.request.VariacaoProdutoRequest;
import com.restaurante.dto.response.VariacaoProdutoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.entity.VariacaoProduto;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.VariacaoProdutoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VariacaoProdutoService {

    private final VariacaoProdutoRepository variacaoProdutoRepository;
    private final ProdutoRepository produtoRepository;

    @Transactional
    public VariacaoProdutoResponse criar(Long produtoId, VariacaoProdutoRequest request) {
        log.info("Adicionando nova variação ao produto ID: {}", produtoId);

        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto", "id", produtoId));

        if (request.getSku() != null && !request.getSku().isBlank()) {
            variacaoProdutoRepository.findBySkuAndAtivoTrue(request.getSku())
                    .ifPresent(v -> {
                        throw new BusinessException("Já existe uma variação activa com o SKU: " + request.getSku());
                    });
        }

        VariacaoProduto variacao = VariacaoProduto.builder()
                .produto(produto)
                .tipo(request.getTipo())
                .valor(request.getValor())
                .tamanho(request.getTamanho())
                .cor(request.getCor())
                .sku(request.getSku())
                .preco(request.getPreco())
                .stock(request.getStock())
                .ativo(request.getAtivo() != null ? request.getAtivo() : true)
                .build();

        variacao = variacaoProdutoRepository.save(variacao);
        
        return mapToResponse(variacao);
    }

    @Transactional
    public VariacaoProdutoResponse atualizar(Long produtoId, Long id, VariacaoProdutoRequest request) {
        log.info("Atualizando variação ID: {} do produto ID: {}", id, produtoId);

        VariacaoProduto variacao = variacaoProdutoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Variação", "id", id));

        if (!variacao.getProduto().getId().equals(produtoId)) {
            throw new BusinessException("Variação não pertence ao produto informado");
        }

        if (request.getSku() != null && !request.getSku().isBlank() && !request.getSku().equals(variacao.getSku())) {
            variacaoProdutoRepository.findBySkuAndAtivoTrue(request.getSku())
                    .ifPresent(v -> {
                        if (!v.getId().equals(id)) {
                            throw new BusinessException("Já existe outra variação activa com o SKU: " + request.getSku());
                        }
                    });
        }

        variacao.setTipo(request.getTipo());
        variacao.setValor(request.getValor());
        variacao.setTamanho(request.getTamanho());
        variacao.setCor(request.getCor());
        variacao.setSku(request.getSku());
        variacao.setPreco(request.getPreco());
        variacao.setStock(request.getStock());
        
        if (request.getAtivo() != null) {
            variacao.setAtivo(request.getAtivo());
        }

        variacao = variacaoProdutoRepository.save(variacao);
        
        return mapToResponse(variacao);
    }

    @Transactional
    public VariacaoProdutoResponse atualizarStock(Long produtoId, Long id, Integer stock) {
        log.info("Atualizando stock da variação ID: {} para {}", id, stock);

        VariacaoProduto variacao = variacaoProdutoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Variação", "id", id));

        if (!variacao.getProduto().getId().equals(produtoId)) {
            throw new BusinessException("Variação não pertence ao produto informado");
        }
        
        if (stock < 0) {
            throw new BusinessException("O stock não pode ser negativo");
        }

        variacao.setStock(stock);
        variacao = variacaoProdutoRepository.save(variacao);
        
        return mapToResponse(variacao);
    }

    @Transactional(readOnly = true)
    public List<VariacaoProdutoResponse> listarPorProduto(Long produtoId, boolean apenasAtivos) {
        List<VariacaoProduto> variacoes = apenasAtivos 
            ? variacaoProdutoRepository.findByProdutoIdAndAtivoTrue(produtoId)
            : variacaoProdutoRepository.findByProdutoId(produtoId);
            
        return variacoes.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void desativar(Long produtoId, Long id) {
        log.info("Desativando variação ID: {}", id);

        VariacaoProduto variacao = variacaoProdutoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Variação", "id", id));

        if (!variacao.getProduto().getId().equals(produtoId)) {
            throw new BusinessException("Variação não pertence ao produto informado");
        }

        variacao.setAtivo(false);
        variacaoProdutoRepository.save(variacao);
    }

    private VariacaoProdutoResponse mapToResponse(VariacaoProduto variacao) {
        return VariacaoProdutoResponse.builder()
                .id(variacao.getId())
                .produtoId(variacao.getProduto().getId())
                .tipo(variacao.getTipo())
                .valor(variacao.getValor())
                .tamanho(variacao.getTamanho())
                .cor(variacao.getCor())
                .sku(variacao.getSku())
                .preco(variacao.getPreco())
                .stock(variacao.getStock())
                .ativo(variacao.getAtivo())
                // .versao(variacao.getVersao()) // Ignorando mapear a versão explicitamente se n houver getter/setter manual. O Hibernate gere internamente, mas no DTO pode ser null, o que está tudo bem.
                .build();
    }
}
