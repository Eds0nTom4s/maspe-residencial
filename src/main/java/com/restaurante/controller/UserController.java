package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.UserResponse;
import com.restaurante.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Usuários", description = "Endpoints para gerenciamento de usuários do sistema")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar usuários", description = "Retorna uma página de usuários cadastrados")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> listarUsuarios(
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("GET /api/usuarios");
        Page<UserResponse> usuarios = userService.listarTodos(pageable);
        return ResponseEntity.ok(ApiResponse.success("Usuários listados com sucesso", usuarios));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Detalhes do usuário", description = "Retorna os detalhes de um usuário específico")
    public ResponseEntity<ApiResponse<UserResponse>> buscarUsuario(@PathVariable Long id) {
        log.info("GET /api/usuarios/{}", id);
        UserResponse usuario = userService.buscarPorId(id);
        return ResponseEntity.ok(ApiResponse.success("Usuário encontrado", usuario));
    }
}
