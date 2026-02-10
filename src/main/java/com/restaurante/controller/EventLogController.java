package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.dto.response.PedidoEventLogResponse;
import com.restaurante.dto.response.SubPedidoEventLogResponse;
import com.restaurante.model.entity.PedidoEventLog;
import com.restaurante.model.entity.SubPedidoEventLog;
import com.restaurante.model.enums.StatusPedido;
import com.restaurante.model.enums.StatusSubPedido;
import com.restaurante.service.EventLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/event-logs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Event Logs", description = "Auditoria e histórico de eventos")
public class EventLogController {

    private final EventLogService eventLogService;

    // ========== PEDIDO EVENT LOGS ==========

    @GetMapping("/pedidos/{pedidoId}")
    @Operation(summary = "Buscar histórico completo de um pedido")
    public ResponseEntity<ApiResponse<List<PedidoEventLogResponse>>> buscarHistoricoPedido(
            @PathVariable Long pedidoId) {
        
        log.info("Requisição para histórico do pedido ID: {}", pedidoId);
        
        List<PedidoEventLogResponse> response = eventLogService.buscarHistoricoPedido(pedidoId)
                .stream()
                .map(this::converterPedidoEventLog)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/pedidos/usuario/{usuario}")
    @Operation(summary = "Buscar eventos de pedidos por usuário")
    public ResponseEntity<ApiResponse<List<PedidoEventLogResponse>>> buscarEventosPedidoPorUsuario(
            @PathVariable String usuario) {
        
        log.info("Requisição para eventos de pedidos do usuário: {}", usuario);
        
        List<PedidoEventLogResponse> response = eventLogService.buscarEventosPedidoPorUsuario(usuario)
                .stream()
                .map(this::converterPedidoEventLog)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/pedidos/periodo")
    @Operation(summary = "Buscar eventos de pedidos por período")
    public ResponseEntity<ApiResponse<List<PedidoEventLogResponse>>> buscarEventosPedidoPorPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim) {
        
        log.info("Requisição para eventos de pedidos entre {} e {}", inicio, fim);
        
        List<PedidoEventLogResponse> response = eventLogService.buscarEventosPedidoPorPeriodo(inicio, fim)
                .stream()
                .map(this::converterPedidoEventLog)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/pedidos/recentes")
    @Operation(summary = "Buscar eventos recentes de pedidos")
    public ResponseEntity<ApiResponse<List<PedidoEventLogResponse>>> buscarEventosPedidoRecentes(
            @RequestParam(defaultValue = "24") int horas) {
        
        log.info("Requisição para eventos de pedidos das últimas {} horas", horas);
        
        List<PedidoEventLogResponse> response = eventLogService.buscarEventosPedidoRecentes(horas)
                .stream()
                .map(this::converterPedidoEventLog)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/pedidos/status/{status}")
    @Operation(summary = "Buscar eventos de mudança para status específico")
    public ResponseEntity<ApiResponse<List<PedidoEventLogResponse>>> buscarEventosPedidoPorStatus(
            @PathVariable StatusPedido status) {
        
        log.info("Requisição para eventos de pedidos com status: {}", status);
        
        List<PedidoEventLogResponse> response = eventLogService.buscarEventosPedidoPorStatus(status)
                .stream()
                .map(this::converterPedidoEventLog)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    // ========== SUBPEDIDO EVENT LOGS ==========

    @GetMapping("/subpedidos/{subPedidoId}")
    @Operation(summary = "Buscar histórico completo de um subpedido")
    public ResponseEntity<ApiResponse<List<SubPedidoEventLogResponse>>> buscarHistoricoSubPedido(
            @PathVariable Long subPedidoId) {
        
        log.info("Requisição para histórico do subpedido ID: {}", subPedidoId);
        
        List<SubPedidoEventLogResponse> response = eventLogService.buscarHistoricoSubPedido(subPedidoId)
                .stream()
                .map(this::converterSubPedidoEventLog)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/subpedidos/pedido/{pedidoId}")
    @Operation(summary = "Buscar todos eventos de subpedidos de um pedido")
    public ResponseEntity<ApiResponse<List<SubPedidoEventLogResponse>>> buscarHistoricoSubPedidosPorPedido(
            @PathVariable Long pedidoId) {
        
        log.info("Requisição para histórico de subpedidos do pedido ID: {}", pedidoId);
        
        List<SubPedidoEventLogResponse> response = eventLogService.buscarHistoricoSubPedidosPorPedido(pedidoId)
                .stream()
                .map(this::converterSubPedidoEventLog)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/subpedidos/cozinha/{cozinhaId}")
    @Operation(summary = "Buscar eventos de uma cozinha específica")
    public ResponseEntity<ApiResponse<List<SubPedidoEventLogResponse>>> buscarEventosSubPedidoPorCozinha(
            @PathVariable Long cozinhaId) {
        
        log.info("Requisição para eventos de subpedidos da cozinha ID: {}", cozinhaId);
        
        List<SubPedidoEventLogResponse> response = eventLogService.buscarEventosSubPedidoPorCozinha(cozinhaId)
                .stream()
                .map(this::converterSubPedidoEventLog)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/subpedidos/cozinha/{cozinhaId}/periodo")
    @Operation(summary = "Buscar eventos de uma cozinha em período")
    public ResponseEntity<ApiResponse<List<SubPedidoEventLogResponse>>> buscarEventosSubPedidoPorCozinhaEPeriodo(
            @PathVariable Long cozinhaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim) {
        
        log.info("Requisição para eventos da cozinha {} entre {} e {}", cozinhaId, inicio, fim);
        
        List<SubPedidoEventLogResponse> response = eventLogService.buscarEventosSubPedidoPorCozinhaEPeriodo(
                cozinhaId, inicio, fim)
                .stream()
                .map(this::converterSubPedidoEventLog)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/subpedidos/usuario/{usuario}")
    @Operation(summary = "Buscar eventos de subpedidos por usuário")
    public ResponseEntity<ApiResponse<List<SubPedidoEventLogResponse>>> buscarEventosSubPedidoPorUsuario(
            @PathVariable String usuario) {
        
        log.info("Requisição para eventos de subpedidos do usuário: {}", usuario);
        
        List<SubPedidoEventLogResponse> response = eventLogService.buscarEventosSubPedidoPorUsuario(usuario)
                .stream()
                .map(this::converterSubPedidoEventLog)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/subpedidos/periodo")
    @Operation(summary = "Buscar eventos de subpedidos por período")
    public ResponseEntity<ApiResponse<List<SubPedidoEventLogResponse>>> buscarEventosSubPedidoPorPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim) {
        
        log.info("Requisição para eventos de subpedidos entre {} e {}", inicio, fim);
        
        List<SubPedidoEventLogResponse> response = eventLogService.buscarEventosSubPedidoPorPeriodo(inicio, fim)
                .stream()
                .map(this::converterSubPedidoEventLog)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/subpedidos/criticos/recentes")
    @Operation(summary = "Buscar eventos críticos recentes (PRONTO, ENTREGUE)")
    public ResponseEntity<ApiResponse<List<SubPedidoEventLogResponse>>> buscarEventosCriticosRecentes(
            @RequestParam(defaultValue = "24") int horas) {
        
        log.info("Requisição para eventos críticos das últimas {} horas", horas);
        
        List<SubPedidoEventLogResponse> response = eventLogService.buscarEventosCriticosRecentes(horas)
                .stream()
                .map(this::converterSubPedidoEventLog)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    @GetMapping("/subpedidos/status/{status}")
    @Operation(summary = "Buscar eventos de mudança para status específico")
    public ResponseEntity<ApiResponse<List<SubPedidoEventLogResponse>>> buscarEventosSubPedidoPorStatus(
            @PathVariable StatusSubPedido status) {
        
        log.info("Requisição para eventos de subpedidos com status: {}", status);
        
        List<SubPedidoEventLogResponse> response = eventLogService.buscarEventosSubPedidoPorStatus(status)
                .stream()
                .map(this::converterSubPedidoEventLog)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Sucesso", response));
    }

    // ========== ESTATÍSTICAS ==========

    @GetMapping("/pedidos/{pedidoId}/timeline")
    @Operation(summary = "Buscar timeline completa de um pedido")
    public ResponseEntity<ApiResponse<List<String>>> buscarTimelineCompletaPedido(
            @PathVariable Long pedidoId) {
        
        log.info("Requisição para timeline do pedido ID: {}", pedidoId);
        
        List<String> timeline = eventLogService.buscarTimelineCompletaPedido(pedidoId);
        return ResponseEntity.ok(ApiResponse.success("Sucesso", timeline));
    }

    @GetMapping("/pedidos/{pedidoId}/contagem")
    @Operation(summary = "Contar total de eventos de um pedido")
    public ResponseEntity<ApiResponse<Long>> contarEventosPedido(
            @PathVariable Long pedidoId) {
        
        Long total = eventLogService.contarEventosPedido(pedidoId);
        return ResponseEntity.ok(ApiResponse.success("Sucesso", total));
    }

    @GetMapping("/cozinhas/{cozinhaId}/tempo-medio")
    @Operation(summary = "Calcular tempo médio de transação de uma cozinha")
    public ResponseEntity<ApiResponse<Double>> calcularTempoMedioTransacao(
            @PathVariable Long cozinhaId) {
        
        Double tempoMedio = eventLogService.calcularTempoMedioTransacao(cozinhaId);
        return ResponseEntity.ok(ApiResponse.success("Sucesso", tempoMedio));
    }

    // ========== CONVERSORES ==========

    private PedidoEventLogResponse converterPedidoEventLog(PedidoEventLog evento) {
        return PedidoEventLogResponse.builder()
                .id(evento.getId())
                .pedidoId(evento.getPedido().getId())
                .numeroPedido(evento.getPedido().getNumero())
                .statusAnterior(evento.getStatusAnterior())
                .statusNovo(evento.getStatusNovo())
                .usuario(evento.getUsuario())
                .timestamp(evento.getTimestamp())
                .observacoes(evento.getObservacoes())
                .descricao(evento.getDescricao())
                .build();
    }

    private SubPedidoEventLogResponse converterSubPedidoEventLog(SubPedidoEventLog evento) {
        return SubPedidoEventLogResponse.builder()
                .id(evento.getId())
                .subPedidoId(evento.getSubPedido().getId())
                .pedidoId(evento.getSubPedido().getPedido().getId())
                .numeroPedido(evento.getSubPedido().getPedido().getNumero())
                .cozinhaId(evento.getCozinha().getId())
                .nomeCozinha(evento.getCozinha().getNome())
                .statusAnterior(evento.getStatusAnterior())
                .statusNovo(evento.getStatusNovo())
                .usuario(evento.getUsuario())
                .timestamp(evento.getTimestamp())
                .observacoes(evento.getObservacoes())
                .tempoTransacaoMs(evento.getTempoTransacaoMs())
                .descricao(evento.getDescricao())
                .transicaoCritica(evento.isTransicaoCritica())
                .build();
    }
}
