package com.restaurante.controller;

import com.restaurante.dto.request.ProdutoImagemRequest;
import com.restaurante.dto.request.ProdutoRequest;
import com.restaurante.dto.request.ReordenarImagensRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.ProdutoImagemResponse;
import com.restaurante.dto.response.ProdutoResponse;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.ProdutoImagemService;
import com.restaurante.service.ProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/tenant/produtos")
@RequiredArgsConstructor
@Tag(name = "Tenant Produtos", description = "CRUD tenant-aware de produtos do cardápio")
public class TenantProdutoController {

    private final ProdutoService produtoService;
    private final ProdutoImagemService produtoImagemService;
    private final TenantGuard tenantGuard;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<ProdutoResponse>>> listar(
            @PageableDefault(size = 50) Pageable pageable) {
        assertCanRead();
        return ResponseEntity.ok(ApiResponse.success("Produtos listados", produtoService.listarTodosDoTenant(pageable)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProdutoResponse>> criar(@Valid @RequestBody ProdutoRequest request) {
        assertCanWrite();
        ProdutoResponse created = produtoService.criarTenantAware(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Produto criado", created));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProdutoResponse>> buscar(@PathVariable Long id) {
        assertCanRead();
        return ResponseEntity.ok(ApiResponse.success("Produto encontrado", produtoService.buscarPorIdDoTenant(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProdutoResponse>> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProdutoRequest request) {
        assertCanWrite();
        return ResponseEntity.ok(ApiResponse.success("Produto atualizado", produtoService.atualizarTenantAware(id, request)));
    }

    @PatchMapping("/{id}/disponibilidade")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProdutoResponse>> alterarDisponibilidade(
            @PathVariable Long id,
            @RequestParam Boolean disponivel) {
        assertCanWrite();
        return ResponseEntity.ok(ApiResponse.success("Disponibilidade atualizada", produtoService.alterarDisponibilidadeTenantAware(id, disponivel)));
    }

    @PatchMapping("/{id}/ativar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProdutoResponse>> ativar(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") Boolean ativo) {
        assertCanWrite();
        return ResponseEntity.ok(ApiResponse.success("Estado do produto atualizado", produtoService.alterarAtivoTenantAware(id, ativo)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> remover(@PathVariable Long id) {
        assertCanWrite();
        produtoService.desativarTenantAware(id);
        return ResponseEntity.ok(ApiResponse.success("Produto removido logicamente", null));
    }

    @PostMapping(value = "/{id}/imagem", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Upload de imagem do produto", description = "Faz upload da imagem principal do produto para o MinIO")
    public ResponseEntity<ApiResponse<ProdutoResponse>> atualizarImagem(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        assertCanWrite();
        return ResponseEntity.ok(ApiResponse.success("Imagem atualizada", produtoService.atualizarImagemTenantAware(id, file)));
    }

    @GetMapping("/{id}/imagens")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar imagens do produto", description = "Retorna a galeria de imagens do produto ordenada")
    public ResponseEntity<ApiResponse<List<ProdutoImagemResponse>>> listarImagens(@PathVariable Long id) {
        assertCanRead();
        List<ProdutoImagemResponse> imagens = produtoImagemService.listarImagens(id);
        return ResponseEntity.ok(ApiResponse.success("Imagens listadas", imagens));
    }

    @PostMapping(value = "/{id}/imagens", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Adicionar imagem à galeria", description = "Faz upload de uma imagem para o MinIO e adiciona à galeria do produto (máx. 4)")
    public ResponseEntity<ApiResponse<ProdutoImagemResponse>> adicionarImagem(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @Valid @ModelAttribute ProdutoImagemRequest request) {
        assertCanWrite();
        ProdutoImagemResponse imagem = produtoImagemService.adicionarImagem(id, file, request != null ? request.getLegenda() : null);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Imagem adicionada com sucesso", imagem));
    }

    @DeleteMapping("/{id}/imagens/{imagemId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Remover imagem da galeria", description = "Remove uma imagem da galeria do produto e do MinIO")
    public ResponseEntity<ApiResponse<Void>> removerImagem(
            @PathVariable Long id,
            @PathVariable Long imagemId) {
        assertCanWrite();
        produtoImagemService.removerImagem(id, imagemId);
        return ResponseEntity.ok(ApiResponse.success("Imagem removida com sucesso", null));
    }

    @PutMapping("/{id}/imagens/ordem")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Reordenar imagens do produto", description = "Define a ordem de exibição das imagens da galeria")
    public ResponseEntity<ApiResponse<List<ProdutoImagemResponse>>> reordenarImagens(
            @PathVariable Long id,
            @Valid @RequestBody ReordenarImagensRequest request) {
        assertCanWrite();
        List<ProdutoImagemResponse> imagens = produtoImagemService.reordenarImagens(id, request.getImagemIds());
        return ResponseEntity.ok(ApiResponse.success("Imagens reordenadas com sucesso", imagens));
    }

    private void assertCanRead() {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_OPERATOR,
                TenantUserRole.TENANT_CASHIER
        );
    }

    private void assertCanWrite() {
        tenantGuard.assertAnyTenantRole(TenantUserRole.TENANT_OWNER, TenantUserRole.TENANT_ADMIN);
    }
}
