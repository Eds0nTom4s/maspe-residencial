package com.restaurante.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.dto.request.ProdutoRequest;
import com.restaurante.dto.response.ProdutoResponse;
import com.restaurante.model.enums.CategoriaProduto;
import com.restaurante.security.JwtAuthenticationFilter;
import com.restaurante.security.JwtTokenProvider;
import com.restaurante.service.ProdutoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de Controller para ProdutoController.
 * Testa endpoints REST, serialização/deserialização JSON e integração com ProdutoService.
 * 
 * @WebMvcTest - Carrega apenas camada web (controllers)
 * @MockBean ProdutoService - Mock do service layer
 * @AutoConfigureMockMvc(addFilters = false) - Desabilita filtros de segurança
 */
@WebMvcTest(controllers = ProdutoController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, JwtTokenProvider.class}
        ))
@AutoConfigureMockMvc(addFilters = false)
class ProdutoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProdutoService produtoService;

    @Test
    @DisplayName("POST /produtos - deve criar produto com sucesso")
    void deveCriarProdutoComSucesso() throws Exception {
        // Arrange
        ProdutoRequest request = ProdutoRequest.builder()
                .codigo("HAMB-001")
                .nome("Hambúrguer Artesanal")
                .descricao("Hambúrguer com carne de 180g")
                .preco(new BigDecimal("32.90"))
                .categoria(CategoriaProduto.PRATO_PRINCIPAL)
                .disponivel(true)
                .build();

        ProdutoResponse response = ProdutoResponse.builder()
                .id(1L)
                .nome("Hambúrguer Artesanal")
                .descricao("Hambúrguer com carne de 180g")
                .preco(new BigDecimal("32.90"))
                .categoria(CategoriaProduto.PRATO_PRINCIPAL)
                .disponivel(true)
                .build();

        when(produtoService.criar(any(ProdutoRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.nome").value("Hambúrguer Artesanal"))
                .andExpect(jsonPath("$.data.preco").value(32.90))
                .andExpect(jsonPath("$.data.disponivel").value(true));

        verify(produtoService, times(1)).criar(any(ProdutoRequest.class));
    }

    @Test
    @DisplayName("POST /produtos - deve retornar 400 quando dados inválidos")
    void deveRetornar400QuandoDadosInvalidos() throws Exception {
        // Arrange - Produto sem nome (campo obrigatório)
        ProdutoRequest request = ProdutoRequest.builder()
                .codigo("PROD-001")
                .preco(new BigDecimal("32.90"))
                .categoria(CategoriaProduto.PRATO_PRINCIPAL)
                .build();

        // Act & Assert
        mockMvc.perform(post("/produtos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(produtoService, never()).criar(any());
    }

    @Test
    @DisplayName("PUT /produtos/{id} - deve atualizar produto com sucesso")
    void deveAtualizarProdutoComSucesso() throws Exception {
        // Arrange
        Long produtoId = 1L;
        ProdutoRequest request = ProdutoRequest.builder()
                .codigo("HAMB-PREM")
                .nome("Hambúrguer Premium")
                .preco(new BigDecimal("39.90"))
                .categoria(CategoriaProduto.PRATO_PRINCIPAL)
                .disponivel(true)
                .build();

        ProdutoResponse response = ProdutoResponse.builder()
                .id(produtoId)
                .nome("Hambúrguer Premium")
                .preco(new BigDecimal("39.90"))
                .categoria(CategoriaProduto.PRATO_PRINCIPAL)
                .disponivel(true)
                .build();

        when(produtoService.atualizar(eq(produtoId), any(ProdutoRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(put("/produtos/{id}", produtoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(produtoId))
                .andExpect(jsonPath("$.data.nome").value("Hambúrguer Premium"))
                .andExpect(jsonPath("$.data.preco").value(39.90));

        verify(produtoService, times(1)).atualizar(eq(produtoId), any(ProdutoRequest.class));
    }

    @Test
    @DisplayName("GET /produtos - deve listar produtos disponíveis")
    void deveListarProdutosDisponiveis() throws Exception {
        // Arrange
        List<ProdutoResponse> produtos = Arrays.asList(
                ProdutoResponse.builder()
                        .id(1L)
                        .nome("Hambúrguer")
                        .preco(new BigDecimal("32.90"))
                        .categoria(CategoriaProduto.PRATO_PRINCIPAL)
                        .disponivel(true)
                        .build(),
                ProdutoResponse.builder()
                        .id(2L)
                        .nome("Refrigerante")
                        .preco(new BigDecimal("7.50"))
                        .categoria(CategoriaProduto.BEBIDA_NAO_ALCOOLICA)
                        .disponivel(true)
                        .build()
        );

        when(produtoService.listarDisponiveis()).thenReturn(produtos);

        // Act & Assert
        mockMvc.perform(get("/produtos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].nome").value("Hambúrguer"))
                .andExpect(jsonPath("$.data[1].nome").value("Refrigerante"));

        verify(produtoService, times(1)).listarDisponiveis();
    }

    @Test
    @DisplayName("GET /produtos/disponiveis - deve listar apenas produtos disponíveis")
    void deveListarApenasProdutosDisponiveis() throws Exception {
        // Arrange
        List<ProdutoResponse> produtosDisponiveis = Arrays.asList(
                ProdutoResponse.builder()
                        .id(1L)
                        .nome("Hambúrguer")
                        .disponivel(true)
                        .build()
        );

        when(produtoService.listarDisponiveis()).thenReturn(produtosDisponiveis);

        // Act & Assert
        mockMvc.perform(get("/produtos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].disponivel").value(true));

        verify(produtoService, times(1)).listarDisponiveis();
    }

    @Test
    @DisplayName("PATCH /produtos/{id}/disponibilidade - deve alterar disponibilidade")
    void deveAlterarDisponibilidade() throws Exception {
        // Arrange
        Long produtoId = 1L;
        ProdutoResponse response = ProdutoResponse.builder()
                .id(produtoId)
                .nome("Hambúrguer")
                .disponivel(false)
                .build();

        when(produtoService.alterarDisponibilidade(produtoId, false)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(patch("/produtos/{id}/disponibilidade", produtoId)
                        .param("disponivel", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(produtoId))
                .andExpect(jsonPath("$.data.disponivel").value(false));

        verify(produtoService, times(1)).alterarDisponibilidade(produtoId, false);
    }

    @Test
    @DisplayName("DELETE /produtos/{id} - deve desativar produto")
    void deveDesativarProduto() throws Exception {
        // Arrange
        Long produtoId = 1L;
        doNothing().when(produtoService).desativar(produtoId);

        // Act & Assert
        mockMvc.perform(delete("/produtos/{id}", produtoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(produtoService, times(1)).desativar(produtoId);
    }

    @Test
    @DisplayName("GET /produtos/categoria/{categoria} - deve listar produtos por categoria")
    void deveListarProdutosPorCategoria() throws Exception {
        // Arrange
        CategoriaProduto categoria = CategoriaProduto.BEBIDA_NAO_ALCOOLICA;
        List<ProdutoResponse> produtos = Arrays.asList(
                ProdutoResponse.builder()
                        .id(1L)
                        .nome("Refrigerante")
                        .categoria(categoria)
                        .build(),
                ProdutoResponse.builder()
                        .id(2L)
                        .nome("Suco")
                        .categoria(categoria)
                        .build()
        );

        when(produtoService.listarPorCategoria(categoria)).thenReturn(produtos);

        // Act & Assert
        mockMvc.perform(get("/produtos/categoria/{categoria}", "BEBIDA_NAO_ALCOOLICA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].categoria").value("BEBIDA_NAO_ALCOOLICA"))
                .andExpect(jsonPath("$.data[1].categoria").value("BEBIDA_NAO_ALCOOLICA"));

        verify(produtoService, times(1)).listarPorCategoria(categoria);
    }
}
