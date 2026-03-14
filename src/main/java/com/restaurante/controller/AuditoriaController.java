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

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller de auditoria financeira e operacional.
 *
 * Serve os logs imutáveis gerados pelo AuditoriaFinanceiraService.
 * Todos os endpoints exigem no mínimo role GERENTE.
 *
 * Endpoints consumidos pelo frontend:
 *   GET /api/auditoria/acoes       – lista de eventos recentes
 *   GET /api/auditoria/estatisticas – contadores por tipo de evento
 *   GET /api/auditoria/modulos      – tipos de evento disponíveis no sistema
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

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/auditoria/acoes
    // Consumido pelo painel de compliance do frontend
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Lista os últimos eventos de auditoria, com filtros opcionais.
     *
     * @param limite   quantos registros retornar (default: 50, máx: 200)
     * @param tipo     filtrar por tipo de evento (ex: ATIVOU_POS_PAGO)
     * @param operador filtrar por nome/login do operador
     * @param inicio   filtrar a partir desta data (ISO 8601)
     * @param fim      filtrar até esta data (ISO 8601)
     */
    @GetMapping("/acoes")
    @Operation(
        summary = "Listar ações auditadas",
        description = "Retorna o histórico de eventos críticos (configuração financeira, pedidos pós-pago, estornos)"
    )
    public ResponseEntity<ApiResponse<List<ConfiguracaoFinanceiraEventLog>>> listarAcoes(
            @RequestParam(defaultValue = "50") int limite,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String operador,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim) {

        log.info("GET /auditoria/acoes | limite={} tipo={} operador={} inicio={} fim={}",
                limite, tipo, operador, inicio, fim);

        // Limita o máximo para evitar payloads excessivos
        int limiteEfetivo = Math.min(limite, 200);

        List<ConfiguracaoFinanceiraEventLog> eventos;

        if (inicio != null && fim != null) {
            // Filtro por período
            eventos = auditRepo.findByPeriodo(inicio, fim);
            if (tipo != null) {
                eventos = eventos.stream()
                        .filter(e -> e.getTipoEvento().equalsIgnoreCase(tipo))
                        .collect(Collectors.toList());
            }
            if (operador != null) {
                eventos = eventos.stream()
                        .filter(e -> e.getUsuarioNome().equalsIgnoreCase(operador))
                        .collect(Collectors.toList());
            }
            // Aplica limite após filtros
            eventos = eventos.stream().limit(limiteEfetivo).collect(Collectors.toList());

        } else if (tipo != null) {
            eventos = auditoriaService.buscarPorTipo(tipo).stream()
                    .limit(limiteEfetivo).collect(Collectors.toList());

        } else if (operador != null) {
            eventos = auditoriaService.buscarPorOperador(operador).stream()
                    .limit(limiteEfetivo).collect(Collectors.toList());

        } else {
            eventos = auditRepo.findUltimosEventos(limiteEfetivo);
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Últimas " + eventos.size() + " ações auditadas", eventos));
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
