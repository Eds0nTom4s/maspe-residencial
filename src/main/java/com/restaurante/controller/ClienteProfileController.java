package com.restaurante.controller;

import com.restaurante.dto.request.CompletarPerfilRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.ClienteResponse;
import com.restaurante.model.entity.Cliente;
import com.restaurante.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cliente")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Perfil Cliente", description = "Gestão do perfil do próprio cliente autenticado")
@PreAuthorize("hasRole('CLIENTE')")
public class ClienteProfileController {

    private final ClienteService clienteService;

    @PostMapping("/completar-perfil")
    @Operation(summary = "Completar perfil", description = "Adiciona ou atualiza o nome do próprio cliente autenticado.")
    public ResponseEntity<ApiResponse<ClienteResponse>> completarPerfil(@Valid @RequestBody CompletarPerfilRequest request) {
        log.info("POST /api/cliente/completar-perfil");
        
        // Obter telefone da sessão JWT (o subject do token emitido no login)
        String telefone = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("Completando perfil para o telefone: {}", telefone);
        
        Cliente cliente = clienteService.buscarPorTelefone(telefone);
        
        // Atualiza o nome através do serviço
        ClienteResponse response = clienteService.atualizar(cliente.getId(), request.getNome());
        
        return ResponseEntity.ok(ApiResponse.success("Perfil atualizado com sucesso", response));
    }
}
