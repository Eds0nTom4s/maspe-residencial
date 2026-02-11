package com.restaurante.controller;

import com.restaurante.dto.request.CriarUnidadeAtendimentoRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.CozinhaResponse;
import com.restaurante.dto.response.UnidadeAtendimentoResponse;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.enums.TipoUnidadeAtendimento;
import com.restaurante.service.UnidadeAtendimentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/unidades-atendimento")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Unidades de Atendimento", description = "Gerenciamento de unidades de atendimento")
public class UnidadeAtendimentoController {

    private final UnidadeAtendimentoService unidadeAtendimentoService;

    @PostMapping
    @Operation(summary = "Criar nova unidade de atendimento")
    public ResponseEntity<ApiResponse<UnidadeAtendimentoResponse>> criar(@Valid @RequestBody CriarUnidadeAtendimentoRequest request) {
        log.info("Requisição para criar unidade de atendimento: {}", request.getNome());
        
        UnidadeAtendimento unidade = unidadeAtendimentoService.criar(
            request.getNome(), 
            request.getTipo(), 
            request.getDescricao()
        );
        
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Unidade criada com sucesso", converterParaResponse(unidade)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar unidade por ID")
    public ResponseEntity<ApiResponse<UnidadeAtendimentoResponse>> buscarPorId(@PathVariable Long id) {
        log.info("Requisição para buscar unidade de atendimento ID: {}", id);
        
        UnidadeAtendimento unidade = unidadeAtendimentoService.buscarPorId(id);
        return ResponseEntity.ok(ApiResponse.success("Sucesso", converterParaResponse(unidade)));
    }

    @GetMapping
    @Operation(summary = "Listar todas as unidades")
    public ResponseEntity<ApiResponse<List<UnidadeAtendimentoResponse>>> listarTodas() {
        log.info("Requisição para listar todas as unidades de atendimento");
        
        List<UnidadeAtendimentoResponse> response = unidadeAtendimentoService.listarTodas().stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/ativas")
    @Operation(summary = "Listar unidades ativas")
    public ResponseEntity<ApiResponse<List<UnidadeAtendimentoResponse>>> listarAtivas() {
        log.info("Requisição para listar unidades ativas");
        
        List<UnidadeAtendimentoResponse> response = unidadeAtendimentoService.listarAtivas().stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/operacionais")
    @Operation(summary = "Listar unidades operacionais (ativas com cozinhas)")
    public ResponseEntity<ApiResponse<List<UnidadeAtendimentoResponse>>> listarOperacionais() {
        log.info("Requisição para listar unidades operacionais");
        
        List<UnidadeAtendimentoResponse> response = unidadeAtendimentoService.listarOperacionais().stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/tipo/{tipo}")
    @Operation(summary = "Listar unidades por tipo")
    public ResponseEntity<ApiResponse<List<UnidadeAtendimentoResponse>>> listarPorTipo(@PathVariable TipoUnidadeAtendimento tipo) {
        log.info("Requisição para listar unidades do tipo: {}", tipo);
        
        List<UnidadeAtendimentoResponse> response = unidadeAtendimentoService.listarPorTipo(tipo).stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @PutMapping("/{id}/ativar")
    @Operation(summary = "Ativar unidade")
    public ResponseEntity<ApiResponse<UnidadeAtendimentoResponse>> ativar(@PathVariable Long id) {
        log.info("Requisição para ativar unidade ID: {}", id);
        
        UnidadeAtendimento unidade = unidadeAtendimentoService.ativar(id);
        return ResponseEntity.ok(ApiResponse.success("Unidade ativada", converterParaResponse(unidade)));
    }

    @PutMapping("/{id}/desativar")
    @Operation(summary = "Desativar unidade")
    public ResponseEntity<ApiResponse<UnidadeAtendimentoResponse>> desativar(@PathVariable Long id) {
        log.info("Requisição para desativar unidade ID: {}", id);
        
        UnidadeAtendimento unidade = unidadeAtendimentoService.desativar(id);
        return ResponseEntity.ok(ApiResponse.success("Unidade desativada", converterParaResponse(unidade)));
    }

    @PostMapping("/{unidadeId}/cozinhas/{cozinhaId}")
    @Operation(summary = "Adicionar cozinha à unidade")
    public ResponseEntity<ApiResponse<Void>> adicionarCozinha(
            @PathVariable Long unidadeId,
            @PathVariable Long cozinhaId) {
        
        log.info("Requisição para adicionar cozinha {} à unidade {}", cozinhaId, unidadeId);
        
        unidadeAtendimentoService.adicionarCozinha(unidadeId, cozinhaId);
        return ResponseEntity.ok(ApiResponse.success("Cozinha adicionada", null));
    }

    @DeleteMapping("/{unidadeId}/cozinhas/{cozinhaId}")
    @Operation(summary = "Remover cozinha da unidade")
    public ResponseEntity<ApiResponse<Void>> removerCozinha(
            @PathVariable Long unidadeId,
            @PathVariable Long cozinhaId) {
        
        log.info("Requisição para remover cozinha {} da unidade {}", cozinhaId, unidadeId);
        
        unidadeAtendimentoService.removerCozinha(unidadeId, cozinhaId);
        return ResponseEntity.ok(ApiResponse.success("Cozinha removida", null));
    }

    private UnidadeAtendimentoResponse converterParaResponse(UnidadeAtendimento unidade) {
        List<CozinhaResponse> cozinhasResponse = unidade.getCozinhas().stream()
                .map(cozinha -> CozinhaResponse.builder()
                        .id(cozinha.getId())
                        .nome(cozinha.getNome())
                        .tipo(cozinha.getTipo())
                        .ativa(cozinha.getAtiva())
                        .build())
                .collect(Collectors.toList());

        return UnidadeAtendimentoResponse.builder()
                .id(unidade.getId())
                .nome(unidade.getNome())
                .tipo(unidade.getTipo())
                .descricao(unidade.getDescricao())
                .ativa(unidade.getAtiva())
                .operacional(unidade.isOperacional())
                .cozinhas(cozinhasResponse)
                .unidadesConsumoAtivas(unidadeAtendimentoService.contarUnidadesConsumoAtivas(unidade.getId()))
                .build();
    }
}