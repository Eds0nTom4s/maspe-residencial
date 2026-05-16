package com.restaurante.controller;

import com.restaurante.dto.request.ProdutoRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.ProdutoResponse;
import com.restaurante.service.ProdutoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/tenant/produtos")
@RequiredArgsConstructor
@Tag(name = "Tenant Produtos", description = "Endpoints tenant-aware para produtos do catálogo")
public class TenantProdutoController {

    private final ProdutoService produtoService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<ProdutoResponse>>> listarDisponiveis(
            @RequestParam(required = false) Long categoriaProdutoId,
            @PageableDefault(size = 50) Pageable pageable
    ) {
        Page<ProdutoResponse> page = (categoriaProdutoId == null)
                ? produtoService.listarDisponiveisDoTenant(pageable)
                : produtoService.listarDisponiveisDoTenantPorCategoriaProduto(categoriaProdutoId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Produtos listados", page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProdutoResponse>> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Produto", produtoService.buscarPorIdDoTenant(id)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProdutoResponse>> criar(@Valid @RequestBody ProdutoRequest request) {
        ProdutoResponse created = produtoService.criarTenantAware(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Produto criado", created));
    }
}
