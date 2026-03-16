package com.restaurante.controller;

import com.restaurante.dto.response.ApiResponse;
import com.restaurante.model.entity.ConfiguracaoFinanceiraEventLog;
import com.restaurante.repository.ConfiguracaoFinanceiraEventLogRepository;
import com.restaurante.service.AuditoriaFinanceiraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller de auditoria financeira e operacional.
 */
@RestController
@RequestMapping("/auditoria")
@RequiredArgsConstructor
@Tag(name = "Auditoria", description = "Consulta de logs financeiros e operacionais")
@PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
public class AuditoriaController {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaController.class);

    private final AuditoriaFinanceiraService auditoriaService;
    private final ConfiguracaoFinanceiraEventLogRepository auditRepo;

    /**
     * Lista os eventos de auditoria com paginação e filtros.
     */
    @GetMapping("/acoes")
    @Operation(
        summary = "Listar acções auditadas",
        description = "Retorna página de eventos críticos com suporte a filtros"
    )
    public ResponseEntity<ApiResponse<Page<ConfiguracaoFinanceiraEventLog>>> listarAcoes(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String operador,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim,
            @PageableDefault(size = 50, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("GET /auditoria/acoes | tipo={} operador={} inicio={} fim={} page={}",
                tipo, operador, inicio, fim, pageable.getPageNumber());

        Page<ConfiguracaoFinanceiraEventLog> pagina;

        if (inicio != null && fim != null) {
            pagina = auditoriaService.buscarPorPeriodo(inicio, fim, pageable);
        } else if (tipo != null) {
            pagina = auditoriaService.buscarPorTipo(tipo, pageable);
        } else if (operador != null) {
            pagina = auditoriaService.buscarPorOperador(operador, pageable);
        } else {
            pagina = auditoriaService.listarTodos(pageable);
        }

        return ResponseEntity.ok(ApiResponse.success("Acções auditadas listadas", pagina));
    }


    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/auditoria/estatisticas
    // Consumido pelo dashboard de compliance do frontend
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Retorna contadores agregados para o dashboard de auditoria.
     *
     * Resposta inclui:
     *  - totalEventos         – total histórico
     *  - eventosUltimas24h    – atividade recente
     *  - eventosUltimos7dias  – atividade semanal
     *  - porTipo              – mapa { tipoEvento → contagem }
     */
    @GetMapping("/estatisticas")
    @Operation(
        summary = "Estatísticas de auditoria",
        description = "Contadores agregados: total de eventos, atividade recente e distribuição por tipo"
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> obterEstatisticas() {
        log.info("GET /auditoria/estatisticas");

        LocalDateTime agora        = LocalDateTime.now();
        LocalDateTime ha24h        = agora.minusHours(24);
        LocalDateTime ha7dias      = agora.minusDays(7);

        long totalEventos        = auditRepo.countTotal();
        long eventosUltimas24h   = auditRepo.countDesde(ha24h);
        long eventosUltimos7dias = auditRepo.countDesde(ha7dias);

        // Contagem por tipo: List<Object[]> → Map<String, Long>
        Map<String, Long> porTipo = auditRepo.countByTipoEvento()
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long)   row[1],
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalEventos",        totalEventos);
        stats.put("eventosUltimas24h",   eventosUltimas24h);
        stats.put("eventosUltimos7dias", eventosUltimos7dias);
        stats.put("porTipo",             porTipo);

        return ResponseEntity.ok(ApiResponse.success("Estatísticas de auditoria", stats));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/auditoria/modulos
    // Consumido pelos filtros/selects do painel de auditoria do frontend
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Retorna os tipos de evento distintos registrados no sistema.
     * Usado pelo frontend para popular selects de filtro sem hardcodes.
     *
     * Resposta:
     * {
     *   "modulos": [
     *     { "tipo": "ATIVOU_POS_PAGO",           "label": "Ativou Pós-Pago",           "categoria": "CONFIGURACAO" },
     *     { "tipo": "DESATIVOU_POS_PAGO",         "label": "Desativou Pós-Pago",        "categoria": "CONFIGURACAO" },
     *     ...
     *   ]
     * }
     */
    @GetMapping("/modulos")
    @Operation(
        summary = "Módulos/tipos de evento auditados",
        description = "Lista todos os tipos de evento distintos registrados. Use para popular selects de filtro."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> listarModulos() {
        log.info("GET /auditoria/modulos");

        // Tipos com metadados estáticos (label + categoria)
        List<Map<String, String>> todosModulos = List.of(
            modulo("ATIVOU_POS_PAGO",               "Ativou Pós-Pago",               "CONFIGURACAO"),
            modulo("DESATIVOU_POS_PAGO",             "Desativou Pós-Pago",            "CONFIGURACAO"),
            modulo("ALTEROU_LIMITE_POS_PAGO",        "Alterou Limite Pós-Pago",       "CONFIGURACAO"),
            modulo("ALTEROU_VALOR_MINIMO",            "Alterou Valor Mínimo",          "CONFIGURACAO"),
            modulo("CONFIRMOU_PAGAMENTO_POS_PAGO",   "Confirmou Pagamento Pós-Pago",  "FINANCEIRO"),
            modulo("ESTORNOU_PEDIDO",                 "Estornou Pedido",               "FINANCEIRO"),
            modulo("AUTORIZOU_POS_PAGO",              "Autorizou Pós-Pago",            "FINANCEIRO")
        );

        // Filtra apenas os tipos que realmente têm registros no banco
        List<String> tiposComRegistro = auditRepo.findTiposEventoDistintos();

        List<Map<String, String>> modulosAtivos = todosModulos.stream()
                .filter(m -> tiposComRegistro.contains(m.get("tipo")))
                .collect(Collectors.toList());

        // Inclui também quaisquer tipos novos no banco que não estejam nos metadados
        tiposComRegistro.stream()
                .filter(t -> todosModulos.stream().noneMatch(m -> m.get("tipo").equals(t)))
                .map(t -> modulo(t, t, "OUTRO"))
                .forEach(modulosAtivos::add);

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("modulos",         modulosAtivos);
        resposta.put("totalRegistrado", tiposComRegistro.size());

        return ResponseEntity.ok(ApiResponse.success("Módulos de auditoria", resposta));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private Map<String, String> modulo(String tipo, String label, String categoria) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("tipo",      tipo);
        m.put("label",     label);
        m.put("categoria", categoria);
        return m;
    }
}
