package com.restaurante.store.service;

import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.enums.CategoriaProduto;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.store.dto.StoreProductDTO;
import com.restaurante.store.mapper.StoreMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StoreCatalogService {

    public static final List<CategoriaProduto> STORE_CATEGORIES = List.of(
            CategoriaProduto.VESTUARIO,
            CategoriaProduto.EQUIPAMENTO_DESPORTIVO,
            CategoriaProduto.ACESSORIO,
            CategoriaProduto.COLECCIONAVEL
    );

    private final ProdutoRepository produtoRepository;
    private final StoreMapper mapper;

    public StoreCatalogService(ProdutoRepository produtoRepository, StoreMapper mapper) {
        this.produtoRepository = produtoRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<StoreProductDTO> listCatalog() {
        return produtoRepository
                .findByCategoriaInAndDisponivelTrueAndAtivoTrue(STORE_CATEGORIES, PageRequest.of(0, 500))
                .getContent()
                .stream()
                .map(mapper::toProductDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public StoreProductDTO getProduct(Long produtoId) {
        Produto produto = produtoRepository.findById(produtoId)
                .filter(this::isStoreProduct)
                .filter(p -> Boolean.TRUE.equals(p.getAtivo()) && Boolean.TRUE.equals(p.getDisponivel()))
                .orElseThrow(() -> new ResourceNotFoundException("Produto da loja não encontrado"));
        return mapper.toProductDTO(produto);
    }

    public boolean isStoreProduct(Produto produto) {
        return produto != null && STORE_CATEGORIES.contains(produto.getCategoria());
    }
}
