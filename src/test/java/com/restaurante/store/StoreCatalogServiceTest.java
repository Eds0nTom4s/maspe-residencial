package com.restaurante.store;

import com.restaurante.model.entity.Produto;
import com.restaurante.model.enums.CategoriaProduto;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.store.dto.StoreProductDTO;
import com.restaurante.store.mapper.StoreMapper;
import com.restaurante.store.service.StoreCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StoreCatalogServiceTest {

    @Test
    void catalogoRetornaApenasCategoriasDaLoja() {
        ProdutoRepository produtoRepository = mock(ProdutoRepository.class);
        StoreMapper mapper = mock(StoreMapper.class);
        StoreCatalogService service = new StoreCatalogService(produtoRepository, mapper);

        Produto camisola = Produto.builder()
                .codigo("GDSE-HOME")
                .nome("Camisola Principal")
                .preco(new BigDecimal("25000"))
                .categoria(CategoriaProduto.VESTUARIO)
                .disponivel(true)
                .ativo(true)
                .build();
        camisola.setId(1L);

        StoreProductDTO dto = new StoreProductDTO();
        dto.setId(1L);
        dto.setNome("Camisola Principal");

        when(produtoRepository.findByCategoriaInAndDisponivelTrueAndAtivoTrue(
                eq(StoreCatalogService.STORE_CATEGORIES), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(camisola)));
        when(mapper.toProductDTO(camisola)).thenReturn(dto);

        List<StoreProductDTO> result = service.listCatalog();

        assertEquals(1, result.size());
        assertEquals("Camisola Principal", result.get(0).getNome());
        verify(produtoRepository).findByCategoriaInAndDisponivelTrueAndAtivoTrue(
                eq(StoreCatalogService.STORE_CATEGORIES), any(Pageable.class));
    }
}
