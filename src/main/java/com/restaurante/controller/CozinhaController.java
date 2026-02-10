package com.restaurante.controller;

import com.restaurante.dto.request.CriarCozinhaRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.CozinhaResponse;
import com.restaurante.model.entity.Cozinha;
import com.restaurante.model.enums.TipoCozinha;
import com.restaurante.service.CozinhaService;
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
@RequestMapping("/api/cozinhas")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Cozinhas", description = "Gerenciamento de cozinhas (recursos de preparação)")
public class CozinhaController {

    private final CozinhaService cozinhaService;

    @PostMapping
    @Operation(summary = "Criar nova cozinha")
    public ResponseEntity<ApiResponse<CozinhaResponse>> criar(@Valid @RequestBody CriarCozinhaRequest request) {
        log.info("Requisição para criar cozinha: {}", request.getNome());
        
        Cozinha cozinha = cozinhaService.criar(request.getNome(), request.getTipo(), request.getIdImpressora());
        CozinhaResponse response = converterParaResponse(cozinha);
        
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Cozinha criada com sucesso", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar cozinha por ID")
    public ResponseEntity<ApiResponse<CozinhaResponse>> buscarPorId(@PathVariable Long id) {
        log.info("Requisição para buscar cozinha ID: {}", id);
        
        Cozinha cozinha = cozinhaService.buscarPorId(id);
        return ResponseEntity.ok(ApiResponse.success("Sucesso", converterParaResponse(cozinha)));
    }

    @GetMapping
    @Operation(summary = "Listar todas as cozinhas")
    public ResponseEntity<ApiResponse<List<CozinhaResponse>>> listarTodas() {
        log.info("Requisição para listar todas as cozinhas");
        
        List<CozinhaResponse> response = cozinhaService.listarTodas().stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/ativas")
    @Operation(summary = "Listar cozinhas ativas")
    public ResponseEntity<ApiResponse<List<CozinhaResponse>>> listarAtivas() {
        log.info("Requisição para listar cozinhas ativas");
        
        List<CozinhaResponse> response = cozinhaService.listarAtivas().stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/tipo/{tipo}")
    @Operation(summary = "Listar cozinhas por tipo")
    public ResponseEntity<ApiResponse<List<CozinhaResponse>>> listarPorTipo(@PathVariable TipoCozinha tipo) {
        log.info("Requisição para listar cozinhas do tipo: {}", tipo);
        
        List<CozinhaResponse> response = cozinhaService.listarPorTipo(tipo).stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @PutMapping("/{id}/ativar")
    @Operation(summary = "Ativar cozinha")
    public ResponseEntity<ApiResponse<CozinhaResponse>> ativar(@PathVariable Long id) {
        log.info("Requisição para ativar cozinha ID: {}", id);
        
        Cozinha cozinha = cozinhaService.ativar(id);
        return ResponseEntity.ok(ApiResponse.success("Cozinha ativada com sucesso", converterParaResponse(cozinha)));
    }

    @PutMapping("/{id}/desativar")
    @Operation(summary = "Desativar cozinha")
    public ResponseEntity<ApiResponse<CozinhaResponse>> desativar(@PathVariable Long id) {
        log.info("Requisição para desativar cozinha ID: {}", id);
        
        Cozinha cozinha = cozinhaService.desativar(id);
        return ResponseEntity.ok(ApiResponse.success("Cozinha desativada com sucesso", converterParaResponse(cozinha)));
    }

    @PutMapping("/{id}/impressora")
    @Operation(summary = "Atualizar ID da impressora")
    public ResponseEntity<ApiResponse<CozinhaResponse>> atualizarImpressora(
            @PathVariable Long id, 
            @RequestParam String idImpressora) {
        
        log.info("Requisição para atualizar impressora da cozinha ID: {}", id);
        
        Cozinha cozinha = cozinhaService.atualizarImpressora(id, idImpressora);
        return ResponseEntity.ok(ApiResponse.success("Impressora atualizada", converterParaResponse(cozinha)));
    }

    @PostMapping("/{cozinhaId}/vincular/{unidadeAtendimentoId}")
    @Operation(summary = "Vincular cozinha a unidade de atendimento")
    public ResponseEntity<ApiResponse<Void>> vincularUnidadeAtendimento(
            @PathVariable Long cozinhaId,
            @PathVariable Long unidadeAtendimentoId) {
        
        log.info("Requisição para vincular cozinha {} à unidade {}", cozinhaId, unidadeAtendimentoId);
        
        cozinhaService.vincularUnidadeAtendimento(cozinhaId, unidadeAtendimentoId);
        return ResponseEntity.ok(ApiResponse.success("Vínculo criado com sucesso", null));
    }

    @DeleteMapping("/{cozinhaId}/desvincular/{unidadeAtendimentoId}")
    @Operation(summary = "Desvincular cozinha de unidade de atendimento")
    public ResponseEntity<ApiResponse<Void>> desvincularUnidadeAtendimento(
            @PathVariable Long cozinhaId,
            @PathVariable Long unidadeAtendimentoId) {
        
        log.info("Requisição para desvincular cozinha {} da unidade {}", cozinhaId, unidadeAtendimentoId);
        
        cozinhaService.desvincularUnidadeAtendimento(cozinhaId, unidadeAtendimentoId);
        return ResponseEntity.ok(ApiResponse.success("Vínculo removido com sucesso", null));
    }

    private CozinhaResponse converterParaResponse(Cozinha cozinha) {
        return CozinhaResponse.builder()
                .id(cozinha.getId())
                .nome(cozinha.getNome())
                .tipo(cozinha.getTipo())
                .impressoraId(cozinha.getImpressoraId())
                .ativa(cozinha.getAtiva())
                .subPedidosAtivos(cozinhaService.contarSubPedidosAtivos(cozinha.getId()))
                .build();
    }
}
