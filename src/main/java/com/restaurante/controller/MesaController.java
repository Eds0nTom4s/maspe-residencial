package com.restaurante.controller;

import com.restaurante.dto.request.CriarMesaRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.MesaResponse;
import com.restaurante.service.MesaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller para gerenciamento de Mesas físicas.
 *
 * <p>Mesa é um recurso permanente — criada pelo ADMIN, nunca finalizada.
 *
 * <p>Roles:
 * <ul>
 *   <li>POST   /mesas          → ADMIN</li>
 *   <li>PUT    /mesas/{id}/ativar|desativar → ADMIN</li>
 *   <li>GET    /mesas/**       → autenticado</li>
 * </ul>
 */
@RestController
@RequestMapping("/mesas")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mesas", description = "Gerenciamento de mesas físicas (recurso permanente — status DERIVADO)")
public class MesaController {

    private final MesaService mesaService;

    // ──────────────────────────────────────────────────────────────────────────
    // Operações administrativas
    // ──────────────────────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Criar nova mesa física (ADMIN)")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<MesaResponse>> criar(@Valid @RequestBody CriarMesaRequest request) {
        log.info("POST /mesas — referencia='{}'", request.getReferencia());
        MesaResponse response = mesaService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Mesa criada com sucesso", response));
    }

    @PutMapping("/{id}/ativar")
    @Operation(summary = "Ativar mesa (ADMIN)")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<MesaResponse>> ativar(@PathVariable Long id) {
        log.info("PUT /mesas/{}/ativar", id);
        return ResponseEntity.ok(ApiResponse.success("Mesa ativada", mesaService.ativar(id)));
    }

    @PutMapping("/{id}/desativar")
    @Operation(summary = "Desativar mesa — proibido com sessão aberta (ADMIN)")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<MesaResponse>> desativar(@PathVariable Long id) {
        log.info("PUT /mesas/{}/desativar", id);
        return ResponseEntity.ok(ApiResponse.success("Mesa desativada", mesaService.desativar(id)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Consultas — retornam status DERIVADO (DISPONIVEL/OCUPADA) em tempo real
    // ──────────────────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Listar todas as mesas com status DERIVADO (DISPONIVEL/OCUPADA)")
    public ResponseEntity<ApiResponse<List<MesaResponse>>> listarTodas() {
        return ResponseEntity.ok(ApiResponse.success("Sucesso", mesaService.listarTodas()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar mesa por ID com status derivado")
    public ResponseEntity<ApiResponse<MesaResponse>> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Sucesso", mesaService.buscarPorId(id)));
    }

    @GetMapping("/qrcode/{qrCode}")
    @Operation(summary = "Buscar mesa pelo QR Code fixo")
    public ResponseEntity<ApiResponse<MesaResponse>> buscarPorQrCode(@PathVariable String qrCode) {
        return ResponseEntity.ok(ApiResponse.success("Sucesso", mesaService.buscarPorQrCode(qrCode)));
    }

    @GetMapping("/ativas")
    @Operation(summary = "Listar mesas ativas")
    public ResponseEntity<ApiResponse<List<MesaResponse>>> listarAtivas() {
        return ResponseEntity.ok(ApiResponse.success("Sucesso", mesaService.listarAtivas()));
    }

    @GetMapping("/disponiveis")
    @Operation(summary = "Listar mesas DISPONÍVEIS (sem sessão aberta — status derivado)")
    public ResponseEntity<ApiResponse<List<MesaResponse>>> listarDisponiveis() {
        return ResponseEntity.ok(ApiResponse.success("Sucesso", mesaService.listarDisponiveis()));
    }

    @GetMapping("/ocupadas")
    @Operation(summary = "Listar mesas OCUPADAS (com sessão aberta — status derivado)")
    public ResponseEntity<ApiResponse<List<MesaResponse>>> listarOcupadas() {
        return ResponseEntity.ok(ApiResponse.success("Sucesso", mesaService.listarOcupadas()));
    }

    @GetMapping("/unidade-atendimento/{unidadeAtendimentoId}")
    @Operation(summary = "Listar mesas de uma Unidade de Atendimento")
    public ResponseEntity<ApiResponse<List<MesaResponse>>> listarPorUnidadeAtendimento(
            @PathVariable Long unidadeAtendimentoId) {
        return ResponseEntity.ok(ApiResponse.success("Sucesso",
                mesaService.listarPorUnidadeAtendimento(unidadeAtendimentoId)));
    }
}
