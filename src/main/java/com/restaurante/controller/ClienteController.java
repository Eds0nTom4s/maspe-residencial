package com.restaurante.controller;

import com.restaurante.dto.request.AtualizarClienteRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.ClienteResponse;
import com.restaurante.model.entity.Cliente;
import com.restaurante.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST para gestão de clientes.
 *
 * <p>Uso principal: painel administrativo (GERENTE/ADMIN).
 * A autenticação do cliente final é feita via OTP em {@code /api/auth}.
 *
 * <h2>Base URL</h2>
 * {@code /api/clientes}
 */
@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Clientes", description = "Gestão de clientes — uso administrativo")
@PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
public class ClienteController {

    private final ClienteService clienteService;

    /**
     * Lista todos os clientes com paginação.
     * GET /api/clientes?page=0&size=20&sort=createdAt,desc
     */
    @GetMapping
    @Operation(
        summary = "Listar clientes",
        description = "Retorna lista paginada de todos os clientes. Ordenação padrão: mais recentes primeiro."
    )
    public ResponseEntity<ApiResponse<Page<ClienteResponse>>> listar(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        log.info("GET /api/clientes — page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        Page<ClienteResponse> pagina = clienteService.listarTodos(pageable);
        return ResponseEntity.ok(ApiResponse.success(
                "Clientes listados: " + pagina.getTotalElements() + " no total", pagina));
    }

    /**
     * Busca cliente por ID.
     * GET /api/clientes/{id}
     */
    @GetMapping("/{id}")
    @Operation(summary = "Buscar cliente por ID")
    public ResponseEntity<ApiResponse<ClienteResponse>> buscarPorId(@PathVariable Long id) {
        log.info("GET /api/clientes/{}", id);
        Cliente cliente = clienteService.buscarPorId(id);
        return ResponseEntity.ok(ApiResponse.success("Cliente encontrado", toResponse(cliente)));
    }

    /**
     * Busca cliente pelo número de telefone.
     * GET /api/clientes/telefone/{telefone}
     * Exemplo: /api/clientes/telefone/+244923000000
     */
    @GetMapping("/telefone/{telefone}")
    @Operation(
        summary = "Buscar cliente por telefone",
        description = "Útil para verificar se um número já está cadastrado antes de criar um fundo."
    )
    public ResponseEntity<ApiResponse<ClienteResponse>> buscarPorTelefone(
            @PathVariable String telefone) {

        log.info("GET /api/clientes/telefone/{}", telefone);
        Cliente cliente = clienteService.buscarPorTelefone(telefone);
        return ResponseEntity.ok(ApiResponse.success("Cliente encontrado", toResponse(cliente)));
    }

    /**
     * Actualiza dados do cliente (nome).
     * PUT /api/clientes/{id}
     * Body: { "nome": "João Silva" }
     */
    @PutMapping("/{id}")
    @Operation(
        summary = "Actualizar cliente",
        description = "Actualiza o nome do cliente. Outros campos (telefone) não são alteráveis via este endpoint."
    )
    public ResponseEntity<ApiResponse<ClienteResponse>> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody AtualizarClienteRequest request) {

        log.info("PUT /api/clientes/{} — nome={}", id, request.getNome());
        ClienteResponse resposta = clienteService.atualizar(id, request.getNome());
        return ResponseEntity.ok(ApiResponse.success("Cliente actualizado", resposta));
    }

    /**
     * Desactiva cliente (soft delete — não apaga os dados).
     * DELETE /api/clientes/{id}
     * Apenas ADMIN.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Desactivar cliente",
        description = "Marca o cliente como inativo. Não apaga os dados nem os fundos/pedidos associados."
    )
    public ResponseEntity<ApiResponse<Void>> desativar(@PathVariable Long id) {
        log.warn("DELETE /api/clientes/{} — desactivando cliente", id);
        clienteService.desativar(id);
        return ResponseEntity.ok(ApiResponse.success("Cliente desactivado", null));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mapper interno
    // ─────────────────────────────────────────────────────────────────────────

    private ClienteResponse toResponse(Cliente c) {
        return ClienteResponse.builder()
                .id(c.getId())
                .telefone(c.getTelefone())
                .nome(c.getNome())
                .telefoneVerificado(c.getTelefoneVerificado())
                .ativo(c.getAtivo())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
