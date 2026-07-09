package com.restaurante.service;

import com.restaurante.dto.request.ProdutoRequest;
import com.restaurante.dto.response.ProdutoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.CategoriaProduto;
import com.restaurante.model.entity.Produto;
import com.restaurante.repository.CategoriaProdutoRepository;
import com.restaurante.repository.ProdutoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.CategoriaProdutoLegacy;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para ProdutoService
 * Exemplo de estrutura de testes para o projeto
 */
@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantGuard tenantGuard;

    @Mock
    private StorageService storageService;

    @Mock
    private CategoriaProdutoRepository categoriaProdutoRepository;

    @Mock
    private CategoriaProdutoService categoriaProdutoService;

    @InjectMocks
    private ProdutoService produtoService;

    private ProdutoRequest produtoRequest;
    private Produto produto;

    @BeforeEach
    void setUp() {
        produtoRequest = ProdutoRequest.builder()
                .codigo("TESTE001")
                .nome("Produto Teste")
                .descricao("Descrição do produto teste")
                .preco(new BigDecimal("25.90"))
                .categoria(CategoriaProdutoLegacy.PRATO_PRINCIPAL)
                .tempoPreparoMinutos(20)
                .disponivel(true)
                .build();

        produto = new Produto();
        produto.setCodigo("TESTE001");
        produto.setNome("Produto Teste");
        produto.setDescricao("Descrição do produto teste");
        produto.setPreco(new BigDecimal("25.90"));
        produto.setCategoria(CategoriaProdutoLegacy.PRATO_PRINCIPAL);
        produto.setTempoPreparoMinutos(20);
        produto.setDisponivel(true);
        produto.setAtivo(true);
        // Simula ID gerado pelo banco
        produto.setId(1L);
        Tenant legacy = new Tenant();
        legacy.setId(99L);
        when(tenantRepository.findByTenantCode("LEGACY")).thenReturn(Optional.of(legacy));

        CategoriaProduto cp = new CategoriaProduto();
        cp.setId(10L);
        lenient().when(categoriaProdutoRepository.findBySlugAndTenantId(any(), anyLong())).thenReturn(Optional.of(cp));
    }

    @Test
    void deveCriarProdutoComSucesso() {
        // Arrange
        when(produtoRepository.existsByCodigoAndTenantId(eq(produtoRequest.getCodigo()), anyLong())).thenReturn(false);
        when(produtoRepository.save(any(Produto.class))).thenReturn(produto);

        // Act
        ProdutoResponse response = produtoService.criar(produtoRequest);

        // Assert
        assertNotNull(response);
        assertEquals(produtoRequest.getCodigo(), response.getCodigo());
        assertEquals(produtoRequest.getNome(), response.getNome());
        assertEquals(produtoRequest.getPreco(), response.getPreco());
        verify(produtoRepository, times(1)).save(any(Produto.class));
    }

    @Test
    void deveLancarExcecaoQuandoCodigoJaExiste() {
        // Arrange
        when(produtoRepository.existsByCodigoAndTenantId(eq(produtoRequest.getCodigo()), anyLong())).thenReturn(true);

        // Act & Assert
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> produtoService.criar(produtoRequest)
        );

        assertTrue(exception.getMessage().contains("Já existe um produto com o código"));
        verify(produtoRepository, never()).save(any(Produto.class));
    }

    @Test
    void deveAlterarDisponibilidadeComSucesso() {
        // Arrange
        when(produtoRepository.findByIdAndTenantId(eq(1L), anyLong())).thenReturn(Optional.of(produto));
        when(produtoRepository.save(any(Produto.class))).thenReturn(produto);

        // Act
        ProdutoResponse response = produtoService.alterarDisponibilidade(1L, false);

        // Assert
        assertNotNull(response);
        assertFalse(response.getDisponivel());
        verify(produtoRepository, times(1)).save(any(Produto.class));
    }
}
