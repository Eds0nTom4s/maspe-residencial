package com.restaurante.service;

import com.restaurante.dto.request.ProdutoRequest;
import com.restaurante.dto.response.ProdutoResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Produto;
import com.restaurante.model.enums.CategoriaProduto;
import com.restaurante.repository.ProdutoRepository;
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
                .categoria(CategoriaProduto.PRATO_PRINCIPAL)
                .tempoPreparoMinutos(20)
                .disponivel(true)
                .build();

        produto = Produto.builder()
                .codigo("TESTE001")
                .nome("Produto Teste")
                .descricao("Descrição do produto teste")
                .preco(new BigDecimal("25.90"))
                .categoria(CategoriaProduto.PRATO_PRINCIPAL)
                .tempoPreparoMinutos(20)
                .disponivel(true)
                .ativo(true)
                .build();
        // Simula ID gerado pelo banco
        produto.setId(1L);
    }

    @Test
    void deveCriarProdutoComSucesso() {
        // Arrange
        when(produtoRepository.existsByCodigo(produtoRequest.getCodigo())).thenReturn(false);
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
        when(produtoRepository.existsByCodigo(produtoRequest.getCodigo())).thenReturn(true);

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
        when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));
        when(produtoRepository.save(any(Produto.class))).thenReturn(produto);

        // Act
        ProdutoResponse response = produtoService.alterarDisponibilidade(1L, false);

        // Assert
        assertNotNull(response);
        assertFalse(response.getDisponivel());
        verify(produtoRepository, times(1)).save(any(Produto.class));
    }
}
