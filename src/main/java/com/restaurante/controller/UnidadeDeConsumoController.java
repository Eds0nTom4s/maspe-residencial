package com.restaurante.controller;

import com.restaurante.dto.request.CriarUnidadeConsumoRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.UnidadeConsumoResponse;
import com.restaurante.model.enums.StatusUnidadeConsumo;
import com.restaurante.model.enums.TipoUnidadeConsumo;
import com.restaurante.service.UnidadeDeConsumoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/unidades-consumo")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Unidades de Consumo", description = "Gerenciamento de unidades de consumo (mesas, quartos, áreas)")
public class UnidadeDeConsumoController {

    private final UnidadeDeConsumoService unidadeDeConsumoService;

    @PostMapping
    @Operation(summary = "Criar nova unidade de consumo")
    public ResponseEntity<ApiResponse<UnidadeConsumoResponse>> criar(
            @Valid @RequestBody CriarUnidadeConsumoRequest request) {
        
        log.info("Requisição para criar unidade de consumo: {}", request.getReferencia());
        UnidadeConsumoResponse response = unidadeDeConsumoService.criar(request);
        
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Unidade de consumo criada com sucesso", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar unidade de consumo por ID")
    public ResponseEntity<ApiResponse<UnidadeConsumoResponse>> buscarPorId(@PathVariable Long id) {
        log.info("Requisição para buscar unidade de consumo ID: {}", id);
        UnidadeConsumoResponse response = unidadeDeConsumoService.buscarPorId(id);
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/qrcode/{qrCode}")
    @Operation(summary = "Buscar unidade de consumo por QR Code")
    public ResponseEntity<ApiResponse<UnidadeConsumoResponse>> buscarPorQrCode(@PathVariable String qrCode) {
        log.info("Requisição para buscar unidade de consumo por QR Code: {}", qrCode);
        UnidadeConsumoResponse response = unidadeDeConsumoService.buscarPorQrCode(qrCode);
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping
    @Operation(summary = "Listar todas as unidades de consumo")
    public ResponseEntity<ApiResponse<List<UnidadeConsumoResponse>>> listarTodas() {
        log.info("Requisição para listar todas as unidades de consumo");
        List<UnidadeConsumoResponse> response = unidadeDeConsumoService.listarTodas();
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Listar unidades de consumo por status")
    public ResponseEntity<ApiResponse<List<UnidadeConsumoResponse>>> listarPorStatus(
            @PathVariable StatusUnidadeConsumo status) {
        
        log.info("Requisição para listar unidades de consumo com status: {}", status);
        List<UnidadeConsumoResponse> response = unidadeDeConsumoService.listarPorStatus(status);
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/disponiveis/{tipo}")
    @Operation(summary = "Listar unidades disponíveis por tipo")
    public ResponseEntity<ApiResponse<List<UnidadeConsumoResponse>>> listarDisponiveisPorTipo(
            @PathVariable TipoUnidadeConsumo tipo) {
        
        log.info("Requisição para listar unidades disponíveis do tipo: {}", tipo);
        List<UnidadeConsumoResponse> response = unidadeDeConsumoService.listarDisponiveisPorTipo(tipo);
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/unidade-atendimento/{unidadeAtendimentoId}")
    @Operation(summary = "Listar unidades de uma Unidade de Atendimento")
    public ResponseEntity<ApiResponse<List<UnidadeConsumoResponse>>> listarPorUnidadeAtendimento(
            @PathVariable Long unidadeAtendimentoId) {
        
        log.info("Requisição para listar unidades da Unidade de Atendimento: {}", unidadeAtendimentoId);
        List<UnidadeConsumoResponse> response = unidadeDeConsumoService.listarPorUnidadeAtendimento(unidadeAtendimentoId);
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @PutMapping("/{id}/fechar")
    @Operation(summary = "Fechar unidade de consumo")
    public ResponseEntity<ApiResponse<UnidadeConsumoResponse>> fechar(@PathVariable Long id) {
        log.info("Requisição para fechar unidade de consumo ID: {}", id);
        UnidadeConsumoResponse response = unidadeDeConsumoService.fechar(id);
        return ResponseEntity.ok(ApiResponse.success("Unidade de consumo fechada com sucesso", response));
    }

    @PutMapping("/{id}/atualizar-status")
    @Operation(summary = "Atualizar status da unidade de consumo")
    public ResponseEntity<ApiResponse<UnidadeConsumoResponse>> atualizarStatus(@PathVariable Long id) {
        log.info("Requisição para atualizar status da unidade de consumo ID: {}", id);
        UnidadeConsumoResponse response = unidadeDeConsumoService.atualizarStatus(id);
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }
}
