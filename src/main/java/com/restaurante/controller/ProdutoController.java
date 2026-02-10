package com.restaurante.controller;

import com.restaurante.dto.request.ProdutoRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.ProdutoResponse;
import com.restaurante.model.enums.CategoriaProduto;
import com.restaurante.service.ProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST para operações com Produto (Cardápio)
 */
@RestController
@RequestMapping("/produtos")
@RequiredArgsConstructor
@Tag(name = "Produtos", description = "Endpoints para gestão do cardápio")
public class ProdutoController {

    private final ProdutoService produtoService;

    /**
     * Cria um novo produto
     * POST /api/produtos
     */
    @PostMapping
    @PreAuthorize("hasRole('GERENTE')")
    @Operation(summary = "Criar produto", description = "Adiciona um novo item ao cardápio")
    public ResponseEntity<ApiResponse<ProdutoResponse>> criar(@Valid @RequestBody ProdutoRequest request) {
        ProdutoResponse produto = produtoService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Produto criado com sucesso", produto));
    }

    /**
     * Atualiza um produto existente
     * PUT /api/produtos/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('GERENTE')")
    @Operation(summary = "Atualizar produto", description = "Atualiza os dados de um produto existente")
    public ResponseEntity<ApiResponse<ProdutoResponse>> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProdutoRequest request) {
        ProdutoResponse produto = produtoService.atualizar(id, request);
        return ResponseEntity.ok(ApiResponse.success("Produto atualizado com sucesso", produto));
    }

    /**
     * Lista todos os produtos disponíveis
     * GET /api/produtos
     */
    @GetMapping
    @Operation(summary = "Listar produtos disponíveis", description = "Retorna todos os produtos ativos e disponíveis")
    public ResponseEntity<ApiResponse<List<ProdutoResponse>>> listarDisponiveis() {
        List<ProdutoResponse> produtos = produtoService.listarDisponiveis();
        return ResponseEntity.ok(ApiResponse.success("Produtos listados com sucesso", produtos));
    }

    /**
     * Lista produtos por categoria
     * GET /api/produtos/categoria/{categoria}
     */
    @GetMapping("/categoria/{categoria}")
    @Operation(summary = "Listar produtos por categoria", description = "Filtra produtos por categoria específica")
    public ResponseEntity<ApiResponse<List<ProdutoResponse>>> listarPorCategoria(
            @PathVariable CategoriaProduto categoria) {
        List<ProdutoResponse> produtos = produtoService.listarPorCategoria(categoria);
        return ResponseEntity.ok(ApiResponse.success("Produtos listados com sucesso", produtos));
    }

    /**
     * Busca produtos por nome
     * GET /api/produtos/buscar?nome=
     */
    @GetMapping("/buscar")
    @Operation(summary = "Buscar produtos por nome", description = "Busca produtos que contenham o nome informado")
    public ResponseEntity<ApiResponse<List<ProdutoResponse>>> buscarPorNome(@RequestParam String nome) {
        List<ProdutoResponse> produtos = produtoService.buscarPorNome(nome);
        return ResponseEntity.ok(ApiResponse.success("Produtos encontrados", produtos));
    }

    /**
     * Altera disponibilidade do produto
     * PATCH /api/produtos/{id}/disponibilidade
     */
    @PatchMapping("/{id}/disponibilidade")
    @Operation(summary = "Alterar disponibilidade", description = "Liga/desliga a disponibilidade de um produto")
    public ResponseEntity<ApiResponse<ProdutoResponse>> alterarDisponibilidade(
            @PathVariable Long id,
            @RequestParam Boolean disponivel) {
        ProdutoResponse produto = produtoService.alterarDisponibilidade(id, disponivel);
        return ResponseEntity.ok(ApiResponse.success("Disponibilidade atualizada", produto));
    }

    /**
     * Desativa um produto
     * DELETE /api/produtos/{id}
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Desativar produto", description = "Remove produto do cardápio (soft delete)")
    public ResponseEntity<ApiResponse<Void>> desativar(@PathVariable Long id) {
        produtoService.desativar(id);
        return ResponseEntity.ok(ApiResponse.success("Produto desativado com sucesso", null));
    }
}
