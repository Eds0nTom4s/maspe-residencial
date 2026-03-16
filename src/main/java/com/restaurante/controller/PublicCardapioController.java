package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.ProdutoResponse;
import com.restaurante.model.enums.CategoriaProduto;
import com.restaurante.service.ProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller REST PÚBLICO para operações com o Cardápio
 * Este controller não requer Duração/Autenticação (JWT)
 */
@RestController
@RequestMapping("/public/cardapio")
@RequiredArgsConstructor
@Tag(name = "Cardápio Público", description = "Endpoints de acesso público (sem autenticação) ao Cardápio")
public class PublicCardapioController {

    private final ProdutoService produtoService;

    /**
     * Lista todos os produtos disponíveis no cardápio
     * GET /api/public/cardapio
     */
    @GetMapping
    @Operation(summary = "Listar produtos do cardápio", description = "Retorna todos os produtos ativos e disponíveis abertamente")
    public ResponseEntity<ApiResponse<List<ProdutoResponse>>> listarDisponiveis() {
        List<ProdutoResponse> produtos = produtoService.listarDisponiveis(PageRequest.of(0, 500)).getContent();
        return ResponseEntity.ok(ApiResponse.success("Produtos do cardápio listados com sucesso", produtos));
    }

    /**
     * Lista produtos por categoria
     * GET /api/public/cardapio/categoria/{categoria}
     */
    @GetMapping("/categoria/{categoria}")
    @Operation(summary = "Listar produtos por categoria", description = "Filtra produtos públicos por categoria específica")
    public ResponseEntity<ApiResponse<List<ProdutoResponse>>> listarPorCategoria(
            @PathVariable CategoriaProduto categoria) {
        List<ProdutoResponse> produtos = produtoService.listarPorCategoria(categoria, PageRequest.of(0, 500)).getContent();
        return ResponseEntity.ok(ApiResponse.success("Produtos filtrados por categoria", produtos));
    }

    /**
     * Busca produtos por nome
     * GET /api/public/cardapio/buscar?nome=
     */
    @GetMapping("/buscar")
    @Operation(summary = "Buscar produtos no cardápio", description = "Procura por um termo específico no cardápio")
    public ResponseEntity<ApiResponse<List<ProdutoResponse>>> buscarPorNome(@RequestParam String nome) {
        List<ProdutoResponse> produtos = produtoService.buscarPorNome(nome, PageRequest.of(0, 500)).getContent();
        return ResponseEntity.ok(ApiResponse.success("Produtos encontrados", produtos));
    }

    /**
     * Lista de todas as categorias base para a UI construir as Abas do Front-end
     * GET /api/public/cardapio/categorias
     */
    @GetMapping("/categorias")
    @Operation(summary = "Listar categorias do cardápio", description = "Retorna a lista de todas as categorias de produtos disponíveis para a interface")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> listarCategoriasEnum() {
        List<Map<String, String>> categorias = Arrays.stream(CategoriaProduto.values())
                .map(cat -> Map.of("id", cat.name(), "descricao", cat.getDescricao()))
                .collect(Collectors.toList());
                
        return ResponseEntity.ok(ApiResponse.success("Categorias listadas", categorias));
    }
}
