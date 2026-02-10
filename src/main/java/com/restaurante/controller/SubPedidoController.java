package com.restaurante.controller;

import com.restaurante.dto.request.AtualizarStatusSubPedidoRequest;
import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.ItemPedidoResponse;
import com.restaurante.dto.response.SubPedidoResponse;
import com.restaurante.model.entity.SubPedido;
import com.restaurante.service.SubPedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/subpedidos")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "SubPedidos", description = "Gerenciamento de SubPedidos (unidades operacionais)")
public class SubPedidoController {

    private final SubPedidoService subPedidoService;

    @GetMapping("/{id}")
    @Operation(summary = "Buscar SubPedido por ID")
    public ResponseEntity<ApiResponse<SubPedidoResponse>> buscarPorId(@PathVariable Long id) {
        log.info("Requisição para buscar SubPedido ID: {}", id);
        
        SubPedido subPedido = subPedidoService.buscarPorId(id);
        return ResponseEntity.ok(ApiResponse.success("Sucesso", converterParaResponse(subPedido)));
    }

    @GetMapping("/pedido/{pedidoId}")
    @Operation(summary = "Buscar SubPedidos de um Pedido")
    public ResponseEntity<ApiResponse<List<SubPedidoResponse>>> buscarPorPedido(@PathVariable Long pedidoId) {
        log.info("Requisição para buscar SubPedidos do Pedido ID: {}", pedidoId);
        
        List<SubPedidoResponse> response = subPedidoService.buscarPorPedido(pedidoId).stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/cozinha/{cozinhaId}/ativos")
    @Operation(summary = "Buscar SubPedidos ativos de uma Cozinha")
    public ResponseEntity<ApiResponse<List<SubPedidoResponse>>> buscarAtivosPorCozinha(@PathVariable Long cozinhaId) {
        log.info("Requisição para buscar SubPedidos ativos da Cozinha ID: {}", cozinhaId);
        
        List<SubPedidoResponse> response = subPedidoService.buscarAtivosPorCozinha(cozinhaId).stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/cozinha/{cozinhaId}/prontos")
    @Operation(summary = "Buscar SubPedidos prontos de uma Cozinha (para impressão)")
    public ResponseEntity<ApiResponse<List<SubPedidoResponse>>> buscarProntosPorCozinha(@PathVariable Long cozinhaId) {
        log.info("Requisição para buscar SubPedidos prontos da Cozinha ID: {}", cozinhaId);
        
        List<SubPedidoResponse> response = subPedidoService.buscarProntosPorCozinha(cozinhaId).stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/atrasados")
    @Operation(summary = "Buscar SubPedidos com atraso")
    public ResponseEntity<ApiResponse<List<SubPedidoResponse>>> buscarComAtraso(
            @RequestParam(defaultValue = "30") int minutosAtraso) {
        
        log.info("Requisição para buscar SubPedidos com atraso de {} minutos", minutosAtraso);
        
        List<SubPedidoResponse> response = subPedidoService.buscarComAtraso(minutosAtraso).stream()
                .map(this::converterParaResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @PutMapping("/{id}/avancar-status")
    @Operation(summary = "Avançar status do SubPedido")
    public ResponseEntity<ApiResponse<SubPedidoResponse>> avancarStatus(@PathVariable Long id) {
        log.info("Requisição para avançar status do SubPedido ID: {}", id);
        
        SubPedido subPedido = subPedidoService.avancarStatus(id);
        return ResponseEntity.ok(ApiResponse.success("Status atualizado com sucesso", converterParaResponse(subPedido)));
    }

    @PutMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar SubPedido")
    public ResponseEntity<ApiResponse<SubPedidoResponse>> cancelar(
            @PathVariable Long id, 
            @RequestParam String motivo) {
        
        log.info("Requisição para cancelar SubPedido ID: {}", id);
        
        SubPedido subPedido = subPedidoService.cancelar(id, motivo);
        return ResponseEntity.ok(ApiResponse.success("SubPedido cancelado", converterParaResponse(subPedido)));
    }

    @GetMapping("/kpi/tempo-medio")
    @Operation(summary = "KPI: Tempo médio de preparação por cozinha")
    public ResponseEntity<ApiResponse<Map<String, Double>>> calcularTempoMedioPorCozinha() {
        log.info("Requisição para KPI de tempo médio por cozinha");
        
        Map<String, Double> kpi = subPedidoService.calcularTempoMedioPorCozinha();
        return ResponseEntity.ok(ApiResponse.success("Sucesso", kpi));
    }

    private SubPedidoResponse converterParaResponse(SubPedido subPedido) {
        List<ItemPedidoResponse> itensResponse = subPedido.getItens().stream()
                .map(item -> ItemPedidoResponse.builder()
                        .id(item.getId())
                        .produtoId(item.getProduto().getId())
                        .produtoNome(item.getProduto().getNome())
                        .produtoCodigo(item.getProduto().getCodigo())
                        .quantidade(item.getQuantidade())
                        .precoUnitario(item.getPrecoUnitario())
                        .subtotal(item.getSubtotal())
                        .observacoes(item.getObservacoes())
                        .build())
                .collect(Collectors.toList());

        return SubPedidoResponse.builder()
                .id(subPedido.getId())
                .pedidoId(subPedido.getPedido().getId())
                .numeroPedido(subPedido.getPedido().getNumero())
                .cozinhaId(subPedido.getCozinha().getId())
                .nomeCozinha(subPedido.getCozinha().getNome())
                .unidadeAtendimentoId(subPedido.getUnidadeAtendimento().getId())
                .nomeUnidadeAtendimento(subPedido.getUnidadeAtendimento().getNome())
                .status(subPedido.getStatus())
                .itens(itensResponse)
                .observacoes(subPedido.getObservacoes())
                .recebidoEm(subPedido.getRecebidoEm())
                .iniciadoEm(subPedido.getIniciadoEm())
                .prontoEm(subPedido.getProntoEm())
                .entregueEm(subPedido.getEntregueEm())
                .tempoPreparacaoMinutos(subPedido.calcularTempoPreparacao())
                .build();
    }
}
