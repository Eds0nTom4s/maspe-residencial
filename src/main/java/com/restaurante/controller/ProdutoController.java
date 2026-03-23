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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(summary = "Criar produto", description = "Adiciona um novo item ao cardápio")
    public ResponseEntity<ApiResponse<ProdutoResponse>> criar(@Valid @RequestBody ProdutoRequest request) {
        ProdutoResponse produto = produtoService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Produto criou com sucesso", produto));
    }

    /**
     * Atualiza um produto existente
     * PUT /api/produtos/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(summary = "Atualizar produto", description = "Atualiza os dados de um produto existente")
    public ResponseEntity<ApiResponse<ProdutoResponse>> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProdutoRequest request) {
        ProdutoResponse produto = produtoService.atualizar(id, request);
        return ResponseEntity.ok(ApiResponse.success("Produto atualizado com sucesso", produto));
    }

    /**
     * Lista todos os produtos disponíveis com paginação
     * GET /api/produtos
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE','COZINHA','CLIENTE')")
    @Operation(summary = "Listar produtos disponíveis", description = "Retorna página de produtos ativos e disponíveis")
    public ResponseEntity<ApiResponse<Page<ProdutoResponse>>> listarDisponiveis(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ProdutoResponse> produtos = produtoService.listarDisponiveis(pageable);
        return ResponseEntity.ok(ApiResponse.success("Produtos listados com sucesso", produtos));
    }

    /**
     * Lista produtos por categoria com paginação
     * GET /api/produtos/categoria/{categoria}
     */
    @GetMapping("/categoria/{categoria}")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE','COZINHA','CLIENTE')")
    @Operation(summary = "Listar produtos por categoria", description = "Filtra produtos por categoria específica com paginação")
    public ResponseEntity<ApiResponse<Page<ProdutoResponse>>> listarPorCategoria(
            @PathVariable CategoriaProduto categoria,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ProdutoResponse> produtos = produtoService.listarPorCategoria(categoria, pageable);
        return ResponseEntity.ok(ApiResponse.success("Produtos listados com sucesso", produtos));
    }

    /**
     * Busca produtos por nome com paginação
     * GET /api/produtos/buscar?nome=
     */
    @GetMapping("/buscar")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE','COZINHA','CLIENTE')")
    @Operation(summary = "Buscar produtos por nome", description = "Busca produtos que contenham o nome informado com paginação")
    public ResponseEntity<ApiResponse<Page<ProdutoResponse>>> buscarPorNome(
            @RequestParam String nome,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ProdutoResponse> produtos = produtoService.buscarPorNome(nome, pageable);
        return ResponseEntity.ok(ApiResponse.success("Produtos encontrados", produtos));
    }

    /**
     * Altera disponibilidade do produto
     * PATCH /api/produtos/{id}/disponibilidade
     */
    @PatchMapping("/{id}/disponibilidade")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
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
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(summary = "Desativar produto", description = "Remove produto do cardápio (soft delete)")
    public ResponseEntity<ApiResponse<Void>> desativar(@PathVariable Long id) {
        produtoService.desativar(id);
        return ResponseEntity.ok(ApiResponse.success("Produto desativado com sucesso", null));
    }

    /**
     * [Admin] Lista todos os produtos incluindo inativos e indisponíveis com paginação.
     * GET /api/produtos/admin
     */
    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(summary = "[Admin] Listar todos os produtos", description = "Inclui produtos inativos e indisponíveis com paginação")
    public ResponseEntity<ApiResponse<Page<ProdutoResponse>>> listarAdmin(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ProdutoResponse> produtos = produtoService.listarTodos(pageable);
        return ResponseEntity.ok(ApiResponse.success("Produtos listados", produtos));
    }


    /**
     * Busca produto por ID (detalhe completo).
     * GET /api/produtos/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE','COZINHA','CLIENTE')")
    @Operation(summary = "Buscar produto por ID", description = "Retorna os detalhes completos de um produto")
    public ResponseEntity<ApiResponse<ProdutoResponse>> buscarPorId(@PathVariable Long id) {
        ProdutoResponse produto = produtoService.buscarPorIdResponse(id);
        return ResponseEntity.ok(ApiResponse.success("Sucesso", produto));
    }

    /**
     * Upload de imagem para o produto.
     * POST /api/produtos/{id}/imagem
     */
    @PostMapping(value = "/{id}/imagem", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    @Operation(summary = "Upload de imagem do produto", description = "Faz o upload de uma imagem para o MinIO e associa ao produto")
    public ResponseEntity<ApiResponse<ProdutoResponse>> uploadImagem(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        ProdutoResponse produto = produtoService.atualizarImagem(id, file);
        return ResponseEntity.ok(ApiResponse.success("Imagem enviada com sucesso", produto));
    }
}
