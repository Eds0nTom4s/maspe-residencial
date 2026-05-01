package com.restaurante.controller;

import com.restaurante.dto.request.VariacaoProdutoRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.VariacaoProdutoResponse;
import com.restaurante.service.VariacaoProdutoService;
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
 * Controller REST para operações com Variações de Produto (SKU, Stock, Tamanho, Cor)
 */
@RestController
@RequestMapping("/produtos/{produtoId}/variacoes")
@RequiredArgsConstructor
@Tag(name = "Variações de Produtos", description = "Endpoints para gestão de variações de produtos (estoque, tamanhos, cores)")
public class VariacaoProdutoController {

    private final VariacaoProdutoService variacaoProdutoService;

    @PostMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(summary = "Adicionar variação", description = "Adiciona uma nova variação a um produto existente")
    public ResponseEntity<ApiResponse<VariacaoProdutoResponse>> criar(
            @PathVariable Long produtoId,
            @Valid @RequestBody VariacaoProdutoRequest request) {
        VariacaoProdutoResponse variacao = variacaoProdutoService.criar(produtoId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Variação adicionada com sucesso", variacao));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(summary = "Atualizar variação", description = "Atualiza os dados de uma variação existente")
    public ResponseEntity<ApiResponse<VariacaoProdutoResponse>> atualizar(
            @PathVariable Long produtoId,
            @PathVariable Long id,
            @Valid @RequestBody VariacaoProdutoRequest request) {
        VariacaoProdutoResponse variacao = variacaoProdutoService.atualizar(produtoId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Variação atualizada com sucesso", variacao));
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(summary = "Atualizar stock", description = "Atualiza apenas o valor do stock de uma variação")
    public ResponseEntity<ApiResponse<VariacaoProdutoResponse>> atualizarStock(
            @PathVariable Long produtoId,
            @PathVariable Long id,
            @RequestParam Integer stock) {
        VariacaoProdutoResponse variacao = variacaoProdutoService.atualizarStock(produtoId, id, stock);
        return ResponseEntity.ok(ApiResponse.success("Stock atualizado com sucesso", variacao));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE','COZINHA','CLIENTE')")
    @Operation(summary = "Listar variações", description = "Lista as variações de um produto")
    public ResponseEntity<ApiResponse<List<VariacaoProdutoResponse>>> listar(
            @PathVariable Long produtoId,
            @RequestParam(defaultValue = "true") boolean apenasAtivos) {
        List<VariacaoProdutoResponse> variacoes = variacaoProdutoService.listarPorProduto(produtoId, apenasAtivos);
        return ResponseEntity.ok(ApiResponse.success("Variações listadas com sucesso", variacoes));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(summary = "Desativar variação", description = "Desativa uma variação (soft delete)")
    public ResponseEntity<ApiResponse<Void>> desativar(
            @PathVariable Long produtoId,
            @PathVariable Long id) {
        variacaoProdutoService.desativar(produtoId, id);
        return ResponseEntity.ok(ApiResponse.success("Variação desativada com sucesso", null));
    }
}
