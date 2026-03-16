package com.restaurante.controller;

import com.restaurante.dto.request.AlterarSenhaAdminRequest;
import com.restaurante.dto.request.AtualizarUsuarioRequest;
import com.restaurante.dto.request.CriarUsuarioRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.UserResponse;
import com.restaurante.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuários", description = "Gestão de utilizadores do sistema")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    // ── Leitura ──────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar utilizadores", description = "Retorna página de utilizadores cadastrados")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> listarUsuarios(
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("GET /api/usuarios");
        return ResponseEntity.ok(ApiResponse.success("Utilizadores listados", userService.listarTodos(pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Detalhes do utilizador")
    public ResponseEntity<ApiResponse<UserResponse>> buscarUsuario(@PathVariable Long id) {
        log.info("GET /api/usuarios/{}", id);
        return ResponseEntity.ok(ApiResponse.success("Utilizador encontrado", userService.buscarPorId(id)));
    }

    @GetMapping("/permissoes")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar permissões (roles)", description = "Retorna mapa de roles disponíveis com descrições")
    public ResponseEntity<ApiResponse<Map<String, String>>> listarPermissoes() {
        log.info("GET /api/usuarios/permissoes");
        return ResponseEntity.ok(ApiResponse.success("Permissões listadas", userService.listarPermissoes()));
    }

    @GetMapping("/{id}/logs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Logs de acções do utilizador",
               description = "Histórico de acções do utilizador. Funcionalidade em expansão.")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listarLogs(@PathVariable Long id) {
        log.info("GET /api/usuarios/{}/logs", id);
        return ResponseEntity.ok(ApiResponse.success("Logs listados (histórico em implementação)", userService.listarLogs(id)));
    }

    // ── Criação e Actualização ────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Criar utilizador", description = "Cria um novo utilizador do sistema")
    public ResponseEntity<ApiResponse<UserResponse>> criar(@Valid @RequestBody CriarUsuarioRequest request) {
        log.info("POST /api/usuarios — username: {}", request.getUsername());
        UserResponse user = userService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Utilizador criado com sucesso", user));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar utilizador", description = "Actualiza email, nome, telefone ou roles")
    public ResponseEntity<ApiResponse<UserResponse>> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody AtualizarUsuarioRequest request) {
        log.info("PUT /api/usuarios/{}", id);
        return ResponseEntity.ok(ApiResponse.success("Utilizador actualizado", userService.atualizar(id, request)));
    }

    // ── Activação / Desactivação ──────────────────────────────────────────────

    @PatchMapping("/{id}/ativar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activar utilizador")
    public ResponseEntity<ApiResponse<UserResponse>> ativar(@PathVariable Long id) {
        log.info("PATCH /api/usuarios/{}/ativar", id);
        return ResponseEntity.ok(ApiResponse.success("Utilizador activado", userService.ativar(id)));
    }

    @PatchMapping("/{id}/desativar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactivar utilizador (soft delete)")
    public ResponseEntity<ApiResponse<Void>> desativar(@PathVariable Long id) {
        log.info("PATCH /api/usuarios/{}/desativar", id);
        userService.desativar(id);
        return ResponseEntity.ok(ApiResponse.success("Utilizador desactivado", null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Remover utilizador", description = "Desactivação lógica (equivalente a PATCH /desativar)")
    public ResponseEntity<ApiResponse<Void>> remover(@PathVariable Long id) {
        log.info("DELETE /api/usuarios/{}", id);
        userService.desativar(id);
        return ResponseEntity.ok(ApiResponse.success("Utilizador removido", null));
    }

    // ── Gestão de Senha ───────────────────────────────────────────────────────

    @PatchMapping("/{id}/senha")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Alterar senha (ADMIN)", description = "Admin define nova senha directamente sem verificar a antiga")
    public ResponseEntity<ApiResponse<Void>> alterarSenha(
            @PathVariable Long id,
            @Valid @RequestBody AlterarSenhaAdminRequest request) {
        log.info("PATCH /api/usuarios/{}/senha", id);
        userService.alterarSenha(id, request);
        return ResponseEntity.ok(ApiResponse.success("Senha alterada com sucesso", null));
    }

    /**
     * Reset de senha administrativo — gera uma senha temporária e retorna ao ADMIN.
     * O ADMIN deve comunicar a senha temporária ao utilizador pessoalmente ou por canal seguro.
     */
    @PostMapping("/{id}/reset-senha")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reset de senha (ADMIN)", description = "Gera senha temporária para o utilizador. ADMIN recebe a senha gerada para comunicar ao utilizador.")
    public ResponseEntity<ApiResponse<Map<String, String>>> resetSenha(@PathVariable Long id) {
        log.info("POST /api/usuarios/{}/reset-senha", id);
        Map<String, String> resultado = userService.resetSenha(id);
        return ResponseEntity.ok(ApiResponse.success("Senha redefinida com sucesso", resultado));
    }

    @PostMapping("/reset-senha/confirmar")
    @Operation(summary = "Confirmar reset de senha", description = "Confirma reset usando token enviado por email (não implementado)")
    public ResponseEntity<ApiResponse<Void>> confirmarResetSenha(@RequestBody Map<String, String> body) {
        log.warn("POST /api/usuarios/reset-senha/confirmar — fluxo de email não configurado");
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponse.error("Fluxo de reset por email não está configurado neste ambiente. Use PATCH /usuarios/{id}/senha."));
    }
}

