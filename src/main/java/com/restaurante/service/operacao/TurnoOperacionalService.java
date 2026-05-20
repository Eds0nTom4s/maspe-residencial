package com.restaurante.service.operacao;

import com.restaurante.config.OperacaoProperties;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.CancelarTurnoRequest;
import com.restaurante.dto.request.FecharTurnoRequest;
import com.restaurante.dto.response.ChecklistRunResponse;
import com.restaurante.dto.response.TurnoOperacionalResponse;
import com.restaurante.dto.response.TurnoPreFechoResponse;
import com.restaurante.exception.ConflictException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.caixa.dto.RelatorioCaixaTurnoResponse;
import com.restaurante.financeiro.caixa.dto.ResumoFinanceiroFechoTurnoSnapshot;
import com.restaurante.financeiro.caixa.dto.TotalPorMetodoPagamentoResponse;
import com.restaurante.financeiro.caixa.service.RelatorioCaixaTurnoService;
import com.restaurante.financeiro.snapshot.CanonicalJsonHashService;
import com.restaurante.financeiro.snapshot.SnapshotIntegridadeProperties;
import com.restaurante.financeiro.snapshot.dto.SnapshotIntegridadeResponse;
import com.restaurante.model.entity.ChecklistOperacionalRun;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.ChecklistTipo;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.TurnoOperacionalStatus;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.ChecklistOperacionalRunRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.service.operacional.OperationalEventLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class TurnoOperacionalService {

    private final OperacaoProperties operacaoProperties;
    private final TurnoOperacionalPolicy policy;
    private final TurnoResumoService resumoService;
    private final ChecklistOperacionalService checklistService;
    private final OperationalEventLogService operationalEventLogService;
    private final ChecklistOperacionalRunRepository checklistOperacionalRunRepository;
    private final RelatorioCaixaTurnoService relatorioCaixaTurnoService;
    private final ObjectMapper objectMapper;
    private final CanonicalJsonHashService canonicalJsonHashService;
    private final SnapshotIntegridadeProperties snapshotIntegridadeProperties;

    private final TenantRepository tenantRepository;
    private final InstituicaoRepository instituicaoRepository;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;
    private final UserRepository userRepository;
    private final TurnoOperacionalRepository turnoOperacionalRepository;

    @Transactional
    public TurnoOperacionalResponse abrirTurno(AbrirTurnoRequest request, String ip, String userAgent) {
        TenantContext ctx = TenantContextHolder.require();
        policy.assertCanOpen(ctx);

        Long tenantId = ctx.tenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        Instituicao inst = instituicaoRepository.findById(request.getInstituicaoId())
                .filter(i -> i.getTenant() != null && i.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        UnidadeAtendimento ua = unidadeAtendimentoRepository.findById(request.getUnidadeAtendimentoId())
                .filter(u -> u.getInstituicao() != null
                        && u.getInstituicao().getTenant() != null
                        && u.getInstituicao().getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        if (turnoOperacionalRepository.existsOpenByTenantAndInstituicaoAndUnidade(tenantId, inst.getId(), ua.getId())) {
            throw new ConflictException("Já existe um turno ABERTO/EM_FECHO para esta unidade.");
        }

        User actor = userRepository.findById(ctx.userId()).orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        TurnoOperacional turno = new TurnoOperacional();
        turno.setTenant(tenant);
        turno.setInstituicao(inst);
        turno.setUnidadeAtendimento(ua);
        turno.setAbertoPor(actor);
        turno.setStatus(TurnoOperacionalStatus.ABERTO);
        turno.setTipo(request.getTipo());
        turno.setNome(request.getNome());
        turno.setAbertoEm(LocalDateTime.now());
        turno.setObservacaoAbertura(request.getObservacao());

        turnoOperacionalRepository.save(turno);

        ChecklistOperacionalRun run = checklistService.validarERegistrarChecklist(tenant, turno, ChecklistTipo.ABERTURA, actor, null, request.getChecklist());
        operationalEventLogService.logChecklistEvent(
                OperationalEventType.CHECKLIST_ABERTURA_CONCLUIDO,
                turno,
                run.getId(),
                resolveOrigemFromRoles(ctx),
                "Checklist de abertura concluído",
                null,
                ip,
                userAgent
        );

        operationalEventLogService.logTurnoEvent(
                OperationalEventType.TURNO_ABERTO,
                turno,
                resolveOrigemFromRoles(ctx),
                "Turno aberto",
                null,
                ip,
                userAgent
        );

        return toResponse(turno, List.of(run));
    }

    @Transactional(readOnly = true)
    public TurnoOperacionalResponse getTurnoAtual(Long instituicaoId, Long unidadeAtendimentoId) {
        TenantContext ctx = TenantContextHolder.require();
        policy.assertCanRead(ctx);
        Long tenantId = ctx.tenantId();

        TurnoOperacional turno = turnoOperacionalRepository.findOpenByTenantAndInstituicaoAndUnidade(tenantId, instituicaoId, unidadeAtendimentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        return toResponse(turno, checklistServiceRuns(turno));
    }

    @Transactional(readOnly = true)
    public Page<TurnoOperacionalResponse> listar(Long instituicaoId, Long unidadeAtendimentoId, TurnoOperacionalStatus status,
                                                LocalDateTime de, LocalDateTime ate, Pageable pageable) {
        TenantContext ctx = TenantContextHolder.require();
        policy.assertCanRead(ctx);
        return turnoOperacionalRepository.search(ctx.tenantId(), instituicaoId, unidadeAtendimentoId, status, de, ate, pageable)
                .map(t -> toResponse(t, null));
    }

    @Transactional(readOnly = true)
    public TurnoOperacionalResponse detalhar(Long turnoId) {
        TenantContext ctx = TenantContextHolder.require();
        policy.assertCanRead(ctx);
        TurnoOperacional turno = turnoOperacionalRepository.findByIdAndTenantId(turnoId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        return toResponse(turno, checklistServiceRuns(turno));
    }

    @Transactional
    public TurnoOperacionalResponse iniciarFecho(Long turnoId, String ip, String userAgent) {
        TenantContext ctx = TenantContextHolder.require();
        policy.assertCanClose(ctx);

        TurnoOperacional turno = turnoOperacionalRepository.findByIdAndTenantId(turnoId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        if (turno.getStatus() != TurnoOperacionalStatus.ABERTO) {
            throw new ConflictException("Turno não está ABERTO para iniciar fecho.");
        }
        turno.setStatus(TurnoOperacionalStatus.EM_FECHO);
        turnoOperacionalRepository.save(turno);

        operationalEventLogService.logTurnoEvent(
                OperationalEventType.TURNO_FECHO_INICIADO,
                turno,
                resolveOrigemFromRoles(ctx),
                "Fecho iniciado",
                null,
                ip,
                userAgent
        );

        return toResponse(turno, checklistServiceRuns(turno));
    }

    @Transactional(readOnly = true)
    public TurnoPreFechoResponse preFecho(Long turnoId) {
        TenantContext ctx = TenantContextHolder.require();
        policy.assertCanRead(ctx);
        TurnoOperacional turno = turnoOperacionalRepository.findByIdAndTenantId(turnoId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        return resumoService.calcularPreFecho(turno);
    }

    @Transactional
    public TurnoOperacionalResponse fechar(Long turnoId, FecharTurnoRequest request, String ip, String userAgent) {
        TenantContext ctx = TenantContextHolder.require();
        policy.assertCanClose(ctx);

        TurnoOperacional turno = turnoOperacionalRepository.findByIdAndTenantId(turnoId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        if (turno.getStatus() == TurnoOperacionalStatus.FECHADO || turno.getStatus() == TurnoOperacionalStatus.CANCELADO) {
            throw new ConflictException("Turno já está fechado/cancelado.");
        }

        TurnoPreFechoResponse pre = resumoService.calcularPreFecho(turno);
        boolean forcar = Boolean.TRUE.equals(request.getForcarFecho());
        if (!pre.isPodeFechar() && !forcar) {
            if (pre.getAlertasFinanceiros() != null && pre.getAlertasFinanceiros().isBloqueiaFecho()) {
                operationalEventLogService.logTurnoEvent(
                        OperationalEventType.TURNO_FECHO_BLOQUEADO_ALERTA_FINANCEIRO,
                        turno,
                        resolveOrigemFromRoles(ctx),
                        "Fecho bloqueado por alerta financeiro crítico",
                        new HashMap<>() {{
                            put("totalPendentes", pre.getAlertasFinanceiros().getTotalPagamentosPendentes());
                            put("totalCriticos", pre.getAlertasFinanceiros().getTotalCriticos());
                            put("valorPendente", pre.getAlertasFinanceiros().getValorPendente());
                        }},
                        ip,
                        userAgent
                );
            }
            throw new ConflictException("Existem pendências bloqueantes para fechar o turno.");
        }
        if (forcar) {
            policy.assertCanForceClose(ctx);
            if (request.getObservacao() == null || request.getObservacao().isBlank()) {
                throw new ConflictException("Observação obrigatória para fecho forçado.");
            }
        }

        User actor = userRepository.findById(ctx.userId()).orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        LocalDateTime fechadoEm = LocalDateTime.now();
        turno.setFechadoPor(actor);
        turno.setFechadoEm(fechadoEm);
        turno.setObservacaoFecho(request.getObservacao());
        turno.setStatus(TurnoOperacionalStatus.FECHADO);

        // Snapshot financeiro congelado no fecho (Prompt 37.1)
        RelatorioCaixaTurnoResponse relatorio = relatorioCaixaTurnoService.gerarRelatorio(ctx.tenantId(), turno.getId());
        ResumoFinanceiroFechoTurnoSnapshot snapshot = buildFinanceiroSnapshot(turno, pre, relatorio, fechadoEm, forcar);
        turno.setResumoJson(toResumoJsonComSnapshot(turno.getResumoJson(), pre, snapshot));

        turnoOperacionalRepository.save(turno);

        ChecklistOperacionalRun run = checklistService.validarERegistrarChecklist(turno.getTenant(), turno, ChecklistTipo.FECHO, actor, null, request.getChecklist());
        operationalEventLogService.logChecklistEvent(
                OperationalEventType.CHECKLIST_FECHO_CONCLUIDO,
                turno,
                run.getId(),
                resolveOrigemFromRoles(ctx),
                "Checklist de fecho concluído",
                null,
                ip,
                userAgent
        );

        operationalEventLogService.logTurnoEvent(
                forcar ? OperationalEventType.TURNO_FECHADO_FORCADO : OperationalEventType.TURNO_FECHADO,
                turno,
                resolveOrigemFromRoles(ctx),
                forcar ? "Turno fechado (forçado)" : "Turno fechado",
                new HashMap<>() {{
                    put("bloqueios", pre.getBloqueios());
                    put("avisos", pre.getAvisos());
                    put("financeiroSnapshotPersistido", true);
                    put("snapshotVersion", snapshot.getSnapshotVersion());
                    put("totalGeralConfirmado", relatorio.getTotalGeralConfirmado());
                    put("totalPendente", relatorio.getTotalPendente());
                    if (pre.getAlertasFinanceiros() != null) {
                        put("totalCriticos", pre.getAlertasFinanceiros().getTotalCriticos());
                    }
                    if (snapshot.getIntegridade() != null) {
                        put("snapshotHash", snapshot.getIntegridade().getSnapshotHash());
                        put("hashAlgorithm", snapshot.getIntegridade().getHashAlgorithm());
                        put("canonicalizationVersion", snapshot.getIntegridade().getCanonicalizationVersion());
                    }
                }},
                ip,
                userAgent
        );

        if (pre.getAlertasFinanceiros() != null && pre.getAlertasFinanceiros().getTotalPagamentosPendentes() > 0) {
            operationalEventLogService.logTurnoEvent(
                    OperationalEventType.TURNO_FECHADO_COM_ALERTA_FINANCEIRO,
                    turno,
                    resolveOrigemFromRoles(ctx),
                    "Turno fechado com alertas financeiros pendentes",
                    new HashMap<>() {{
                        put("totalPendentes", pre.getAlertasFinanceiros().getTotalPagamentosPendentes());
                        put("totalCriticos", pre.getAlertasFinanceiros().getTotalCriticos());
                        put("valorPendente", pre.getAlertasFinanceiros().getValorPendente());
                        put("forcarFecho", forcar);
                    }},
                    ip,
                    userAgent
            );
        }

        return toResponse(turno, List.of(run));
    }

    @Transactional
    public TurnoOperacionalResponse cancelar(Long turnoId, CancelarTurnoRequest request, String ip, String userAgent) {
        TenantContext ctx = TenantContextHolder.require();
        policy.assertCanCancel(ctx);

        TurnoOperacional turno = turnoOperacionalRepository.findByIdAndTenantId(turnoId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        if (turno.getStatus() == TurnoOperacionalStatus.FECHADO) {
            throw new ConflictException("Turno já está fechado.");
        }
        turno.setStatus(TurnoOperacionalStatus.CANCELADO);
        turno.setObservacaoFecho(request.getMotivo());
        turnoOperacionalRepository.save(turno);

        operationalEventLogService.logTurnoEvent(
                OperationalEventType.TURNO_CANCELADO,
                turno,
                resolveOrigemFromRoles(ctx),
                "Turno cancelado: " + request.getMotivo(),
                null,
                ip,
                userAgent
        );

        return toResponse(turno, checklistServiceRuns(turno));
    }

    private List<ChecklistOperacionalRun> checklistServiceRuns(TurnoOperacional turno) {
        return checklistOperacionalRunRepository.findByTenantIdAndTurnoIdOrderByIdAsc(
                turno.getTenant().getId(),
                turno.getId()
        );
    }

    private TurnoOperacionalResponse toResponse(TurnoOperacional turno, List<ChecklistOperacionalRun> checklists) {
        TurnoOperacionalResponse r = new TurnoOperacionalResponse();
        r.setId(turno.getId());
        r.setTenantId(turno.getTenant() != null ? turno.getTenant().getId() : null);
        r.setInstituicaoId(turno.getInstituicao() != null ? turno.getInstituicao().getId() : null);
        r.setUnidadeAtendimentoId(turno.getUnidadeAtendimento() != null ? turno.getUnidadeAtendimento().getId() : null);
        r.setStatus(turno.getStatus());
        r.setTipo(turno.getTipo());
        r.setNome(turno.getNome());
        r.setAbertoPorUserId(turno.getAbertoPor() != null ? turno.getAbertoPor().getId() : null);
        r.setFechadoPorUserId(turno.getFechadoPor() != null ? turno.getFechadoPor().getId() : null);
        r.setAbertoEm(turno.getAbertoEm());
        r.setFechadoEm(turno.getFechadoEm());
        r.setObservacaoAbertura(turno.getObservacaoAbertura());
        r.setObservacaoFecho(turno.getObservacaoFecho());
        r.setResumoJson(turno.getResumoJson());

        if (checklists != null) {
            List<ChecklistRunResponse> cr = new ArrayList<>();
            for (ChecklistOperacionalRun run : checklists) {
                cr.add(checklistService.toRunResponse(run));
            }
            r.setChecklists(cr);
        }
        return r;
    }

    private String toResumoJsonComSnapshot(String existingResumoJson,
                                          TurnoPreFechoResponse pre,
                                          ResumoFinanceiroFechoTurnoSnapshot snapshot) {
        try {
            ObjectNode root = readResumoJsonOrCreateObject(existingResumoJson);

            // Preserva compatibilidade: mantém os campos do pré-fecho no root (como antes), mas garante que
            // o root reflita a fotografia no momento do fecho.
            ObjectNode preNode = objectMapper.valueToTree(pre);
            preNode.fields().forEachRemaining(e -> root.set(e.getKey(), e.getValue()));

            // Snapshot financeiro em chave dedicada, sem listas pesadas.
            root.set("financeiro", objectMapper.valueToTree(snapshot));

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            // Não fechar turno sem snapshot financeiro.
            throw new IllegalStateException("Falha ao gerar/persistir resumo_json com snapshot financeiro (Prompt 37.1).", e);
        }
    }

    private ObjectNode readResumoJsonOrCreateObject(String resumoJson) {
        if (resumoJson == null || resumoJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode parsed = objectMapper.readTree(resumoJson);
            if (parsed instanceof ObjectNode on) return on;
            ObjectNode wrapper = objectMapper.createObjectNode();
            wrapper.set("legacy", parsed);
            return wrapper;
        } catch (Exception e) {
            ObjectNode wrapper = objectMapper.createObjectNode();
            wrapper.put("legacy_parse_error", true);
            return wrapper;
        }
    }

    private ResumoFinanceiroFechoTurnoSnapshot buildFinanceiroSnapshot(TurnoOperacional turno,
                                                                       TurnoPreFechoResponse pre,
                                                                       RelatorioCaixaTurnoResponse relatorio,
                                                                       LocalDateTime fechadoEm,
                                                                       boolean forcarFecho) {
        ResumoFinanceiroFechoTurnoSnapshot s = new ResumoFinanceiroFechoTurnoSnapshot();
        s.setSnapshotVersion("37.1");
        s.setGeradoEm(fechadoEm);

        s.setTurnoId(turno.getId());
        s.setTenantId(turno.getTenant() != null ? turno.getTenant().getId() : null);
        s.setInstituicaoId(turno.getInstituicao() != null ? turno.getInstituicao().getId() : null);
        s.setUnidadeAtendimentoId(turno.getUnidadeAtendimento() != null ? turno.getUnidadeAtendimento().getId() : null);
        s.setStatusTurnoNoMomento(TurnoOperacionalStatus.FECHADO.name());
        s.setAbertoEm(turno.getAbertoEm());
        s.setFechadoEm(fechadoEm);

        s.setTotalGeralConfirmado(relatorio.getTotalGeralConfirmado());
        s.setTotalManualConfirmado(relatorio.getTotalManualConfirmado());
        s.setTotalGatewayConfirmado(relatorio.getTotalGatewayConfirmado());
        s.setTotalPendente(relatorio.getTotalPendente());
        s.setTotalFalhado(relatorio.getTotalFalhado());
        s.setTotalDivergente(relatorio.getTotalDivergente());
        s.setTotalCarregamentoFundo(relatorio.getTotalCarregamentoFundoConsumo());
        s.setTotalPagamentoPedidos(relatorio.getTotalPagamentoPedidos());

        s.setQuantidadePagamentosConfirmados(relatorio.getQuantidadePagamentosConfirmados());
        s.setQuantidadePagamentosPendentes(relatorio.getQuantidadePagamentosPendentes());
        s.setQuantidadeOrdensManuaisConfirmadas(relatorio.getQuantidadeOrdensManuaisConfirmadas());

        // Garantir ordem determinística para hashing/export
        if (relatorio.getTotaisPorMetodo() != null) {
            List<com.restaurante.financeiro.caixa.dto.TotalPorMetodoPagamentoResponse> metodo = new ArrayList<>(relatorio.getTotaisPorMetodo());
            metodo.sort(Comparator.comparing(com.restaurante.financeiro.caixa.dto.TotalPorMetodoPagamentoResponse::getMetodoPagamento, Comparator.nullsLast(String::compareTo)));
            s.setTotaisPorMetodo(metodo);
        } else {
            s.setTotaisPorMetodo(List.of());
        }

        if (relatorio.getTotaisPorOrigem() != null) {
            List<com.restaurante.financeiro.caixa.dto.TotalPorOrigemPagamentoResponse> origem = new ArrayList<>(relatorio.getTotaisPorOrigem());
            origem.sort(Comparator.comparing(com.restaurante.financeiro.caixa.dto.TotalPorOrigemPagamentoResponse::getOrigem, Comparator.nullsLast(String::compareTo)));
            s.setTotaisPorOrigem(origem);
        } else {
            s.setTotaisPorOrigem(List.of());
        }
        s.setAlertasFinanceiros(pre.getAlertasFinanceiros() != null ? pre.getAlertasFinanceiros() : relatorio.getAlertasFinanceiros());

        // Totais por método (reduzidos) para CASH/TPA/APPYPAY
        if (relatorio.getTotaisPorMetodo() != null) {
            for (TotalPorMetodoPagamentoResponse t : relatorio.getTotaisPorMetodo()) {
                if ("CASH".equalsIgnoreCase(t.getMetodoPagamento())) s.setTotalCash(t.getTotalConfirmado());
                if ("TPA".equalsIgnoreCase(t.getMetodoPagamento())) s.setTotalTpa(t.getTotalConfirmado());
                if ("APPYPAY".equalsIgnoreCase(t.getMetodoPagamento())) s.setTotalAppyPay(t.getTotalConfirmado());
            }
        }

        if (s.getAlertasFinanceiros() != null) {
            s.setTotalPagamentosPendentes(s.getAlertasFinanceiros().getTotalPagamentosPendentes());
            s.setTotalCriticos(s.getAlertasFinanceiros().getTotalCriticos());
        }

        s.setObservacoes(Map.of(
                "forcarFecho", forcarFecho,
                "temAlertasFinanceiros", pre.getAlertasFinanceiros() != null && pre.getAlertasFinanceiros().getTotalPagamentosPendentes() > 0
        ));

        // Integridade (Prompt 37.2): hash canônico do snapshot financeiro sem integridade
        if (snapshotIntegridadeProperties.isEnabled()) {
            SnapshotIntegridadeResponse integ = new SnapshotIntegridadeResponse();
            integ.setHashAlgorithm(snapshotIntegridadeProperties.getAlgorithm());
            integ.setCanonicalizationVersion(snapshotIntegridadeProperties.getCanonicalizationVersion());
            integ.setHashGeneratedAt(fechadoEm);
            integ.setHashScope("resumo_json.financeiro_sem_integridade");

            // calcula sobre JsonNode do snapshot sem o próprio bloco integridade
            String hash = canonicalJsonHashService.hashHexCanonical(
                    snapshotIntegridadeProperties.getAlgorithm(),
                    objectMapper.valueToTree(s),
                    List.of("integridade")
            );
            integ.setSnapshotHash(hash);
            s.setIntegridade(integ);
        }

        return s;
    }

    private OperationalOrigem resolveOrigemFromRoles(TenantContext ctx) {
        if (ctx == null || ctx.roles() == null) return OperationalOrigem.SYSTEM;
        if (ctx.roles().contains("TENANT_ADMIN") || ctx.roles().contains("TENANT_OWNER")) return OperationalOrigem.TENANT_ADMIN;
        if (ctx.roles().contains("TENANT_OPERATOR")) return OperationalOrigem.TENANT_OPERATOR;
        if (ctx.roles().contains("TENANT_CASHIER")) return OperationalOrigem.TENANT_CASHIER;
        if (ctx.roles().contains("TENANT_KITCHEN")) return OperationalOrigem.TENANT_KITCHEN;
        return OperationalOrigem.TENANT_OPERATOR;
    }
}
