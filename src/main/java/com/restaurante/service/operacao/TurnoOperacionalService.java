package com.restaurante.service.operacao;

import com.restaurante.config.OperacaoProperties;
import com.restaurante.dto.request.AbrirTurnoRequest;
import com.restaurante.dto.request.CancelarTurnoRequest;
import com.restaurante.dto.request.FecharTurnoRequest;
import com.restaurante.dto.response.ChecklistRunResponse;
import com.restaurante.dto.response.OperacaoConfigResponse;
import com.restaurante.dto.response.TurnoOperacionalResponse;
import com.restaurante.dto.response.TurnoPreFechoResponse;
import com.restaurante.exception.ConflictException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.caixa.dto.RelatorioCaixaTurnoResponse;
import com.restaurante.financeiro.caixa.dto.ResumoFinanceiroFechoTurnoSnapshot;
import com.restaurante.financeiro.caixa.dto.TotalPorMetodoPagamentoResponse;
import com.restaurante.financeiro.caixa.service.RelatorioCaixaTurnoService;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.financeiro.snapshot.CanonicalJsonHashService;
import com.restaurante.financeiro.snapshot.SnapshotIntegridadeProperties;
import com.restaurante.financeiro.snapshot.SnapshotSignatureService;
import com.restaurante.financeiro.snapshot.dto.SnapshotIntegridadeResponse;
import com.restaurante.financeiro.snapshot.dto.SnapshotSignatureResult;
import com.restaurante.model.entity.ChecklistOperacionalRun;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.OrdemPagamento;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.ChecklistTipo;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.model.enums.TurnoOperacionalStatus;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.ChecklistOperacionalRunRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.service.SessaoConsumoAutoClosureService;
import com.restaurante.service.operacional.OperationalEventLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TurnoOperacionalService {

    private static final String EXTRATO_LIMPEZA_POLICY_VERSION = "TURNO_EXTRATO_LIMPEZA_OPERACIONAL_001";
    private static final String FINANCEIRO_SNAPSHOT_VERSION = "37.1";
    private static final int MOTIVO_FECHO_FORCADO_MIN_LENGTH = 10;
    private static final int MOTIVO_FECHO_FORCADO_MAX_LENGTH = 500;
    private static final Set<StatusSessaoConsumo> SESSOES_OPERACIONALMENTE_ABERTAS =
            Set.of(StatusSessaoConsumo.ABERTA, StatusSessaoConsumo.AGUARDANDO_PAGAMENTO);

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
    private final SnapshotSignatureService snapshotSignatureService;
    private final OrdemPagamentoRepository ordemPagamentoRepository;
    private final PagamentoGatewayRepository pagamentoGatewayRepository;
    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final SessaoConsumoAutoClosureService sessaoConsumoAutoClosureService;

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
    public OperacaoConfigResponse getConfig() {
        TenantContext ctx = TenantContextHolder.require();
        policy.assertCanRead(ctx);
        return OperacaoConfigResponse.builder()
                .turnoObrigatorio(operacaoProperties.isTurnoObrigatorio())
                .pedidosEscopo(operacaoProperties.getPedidosEscopo())
                .extratoTurnoEnabled(operacaoProperties.isExtratoTurnoEnabled())
                .build();
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
        String motivoFechoForcado = null;
        ForceAutoClosureResult autoClosureResult = ForceAutoClosureResult.empty();
        Map<String, Object> fechoForcadoPolicyMetadata = null;

        if (forcar) {
            policy.assertCanForceClose(ctx);
            motivoFechoForcado = validarMotivoFechoForcado(request);
            autoClosureResult = tentarAutoFechoSessoesPontoElegiveis(turno);
            if (autoClosureResult.tentadas() > 0) {
                pre = resumoService.calcularPreFecho(turno);
            }

            List<String> bloqueiosNaoIgnoraveis = bloqueiosNaoIgnoraveis(pre);
            if (!bloqueiosNaoIgnoraveis.isEmpty()) {
                logFechoForcadoBloqueado(turno, ctx, motivoFechoForcado, pre, bloqueiosNaoIgnoraveis,
                        autoClosureResult, ip, userAgent);
                throw new ConflictException("Fecho forçado bloqueado por pendência não ignorável.");
            }

            fechoForcadoPolicyMetadata = buildFechoForcadoPolicyMetadata(ctx, turno, pre, motivoFechoForcado,
                    bloqueiosNaoIgnoraveis, autoClosureResult);
        } else if (!pre.isPodeFechar()) {
            if (pre.getAlertasFinanceiros() != null && pre.getAlertasFinanceiros().isBloqueiaFecho()) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("totalPendentes", pre.getAlertasFinanceiros().getTotalPagamentosPendentes());
                metadata.put("totalCriticos", pre.getAlertasFinanceiros().getTotalCriticos());
                metadata.put("valorPendente", pre.getAlertasFinanceiros().getValorPendente());
                operationalEventLogService.logTurnoEvent(
                        OperationalEventType.TURNO_FECHO_BLOQUEADO_ALERTA_FINANCEIRO,
                        turno,
                        resolveOrigemFromRoles(ctx),
                        "Fecho bloqueado por alerta financeiro crítico",
                        metadata,
                        ip,
                        userAgent
                );
            }
            throw new ConflictException("Existem pendências bloqueantes para fechar o turno.");
        }

        User actor = userRepository.findById(ctx.userId()).orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        LocalDateTime fechadoEm = LocalDateTime.now();
        turno.setFechadoPor(actor);
        turno.setFechadoEm(fechadoEm);
        turno.setObservacaoFecho(forcar ? motivoFechoForcado : request.getObservacao());
        turno.setStatus(TurnoOperacionalStatus.FECHADO);

        // Snapshot financeiro congelado no fecho (Prompt 37.1)
        RelatorioCaixaTurnoResponse relatorio = relatorioCaixaTurnoService.gerarRelatorio(ctx.tenantId(), turno.getId());
        ResumoFinanceiroFechoTurnoSnapshot snapshot = buildFinanceiroSnapshot(turno, pre, relatorio, fechadoEm, forcar);
        turno.setResumoJson(toResumoJsonComSnapshot(
                turno,
                ctx,
                pre,
                relatorio,
                snapshot,
                fechoForcadoPolicyMetadata,
                fechadoEm,
                forcar,
                motivoFechoForcado
        ));

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
                buildTurnoFechadoMetadata(pre, relatorio, snapshot, fechoForcadoPolicyMetadata),
                ip,
                userAgent
        );

        if (pre.getAlertasFinanceiros() != null && pre.getAlertasFinanceiros().getTotalPagamentosPendentes() > 0) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("totalPendentes", pre.getAlertasFinanceiros().getTotalPagamentosPendentes());
            metadata.put("totalCriticos", pre.getAlertasFinanceiros().getTotalCriticos());
            metadata.put("valorPendente", pre.getAlertasFinanceiros().getValorPendente());
            metadata.put("forcarFecho", forcar);
            operationalEventLogService.logTurnoEvent(
                    OperationalEventType.TURNO_FECHADO_COM_ALERTA_FINANCEIRO,
                    turno,
                    resolveOrigemFromRoles(ctx),
                    "Turno fechado com alertas financeiros pendentes",
                    metadata,
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

    private String toResumoJsonComSnapshot(TurnoOperacional turno,
                                          TenantContext ctx,
                                          TurnoPreFechoResponse pre,
                                          RelatorioCaixaTurnoResponse relatorio,
                                          ResumoFinanceiroFechoTurnoSnapshot snapshot,
                                          Map<String, Object> fechoForcadoPolicyMetadata,
                                          LocalDateTime fechadoEm,
                                          boolean forcar,
                                          String motivoFechoForcado) {
        try {
            ObjectNode root = readResumoJsonOrCreateObject(turno.getResumoJson());

            // Preserva compatibilidade: mantém os campos do pré-fecho no root (como antes), mas garante que
            // o root reflita a fotografia no momento do fecho.
            ObjectNode preNode = objectMapper.valueToTree(pre);
            preNode.fields().forEachRemaining(e -> root.set(e.getKey(), e.getValue()));

            // Snapshot financeiro em chave dedicada, sem listas pesadas.
            root.set("financeiro", objectMapper.valueToTree(snapshot));
            Map<String, Object> operacional = buildOperacionalSnapshot(
                    turno, ctx, pre, relatorio, snapshot, fechadoEm, forcar, motivoFechoForcado
            );
            root.set("operacional", objectMapper.valueToTree(operacional));
            root.set("limpezaOperacional", objectMapper.valueToTree(buildLimpezaOperacionalSnapshot(pre, operacional, forcar)));
            root.put("snapshotGeradoEm", fechadoEm != null ? fechadoEm.toString() : null);
            root.put("policyVersion", EXTRATO_LIMPEZA_POLICY_VERSION);
            if (fechoForcadoPolicyMetadata != null && !fechoForcadoPolicyMetadata.isEmpty()) {
                root.set("fechoForcadoPolicy", objectMapper.valueToTree(fechoForcadoPolicyMetadata));
            }

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            // Não fechar turno sem snapshot financeiro.
            throw new IllegalStateException("Falha ao gerar/persistir resumo_json com snapshot financeiro (Prompt 37.1).", e);
        }
    }

    private Map<String, Object> buildOperacionalSnapshot(TurnoOperacional turno,
                                                        TenantContext ctx,
                                                        TurnoPreFechoResponse pre,
                                                        RelatorioCaixaTurnoResponse relatorio,
                                                        ResumoFinanceiroFechoTurnoSnapshot financeiro,
                                                        LocalDateTime fechadoEm,
                                                        boolean forcar,
                                                        String motivoFechoForcado) {
        Long tenantId = turno.getTenant() != null ? turno.getTenant().getId() : null;
        Long turnoId = turno.getId();
        Long unidadeId = turno.getUnidadeAtendimento() != null ? turno.getUnidadeAtendimento().getId() : null;

        List<OrdemPagamento> ordens = tenantId != null && turnoId != null
                ? ordemPagamentoRepository.findAllByTenantIdAndTurnoOperacionalId(tenantId, turnoId)
                : List.of();
        Map<String, Long> ordensPorStatus = countOrdensPorStatus(ordens);
        List<SessaoConsumo> sessoesHerdadas = tenantId != null && unidadeId != null
                ? sessaoConsumoRepository.findByTenantIdAndUnidadeAtendimentoIdAndStatusIn(
                tenantId,
                unidadeId,
                SESSOES_OPERACIONALMENTE_ABERTAS
        )
                : List.of();

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("snapshotVersion", "1.0");
        root.put("policyVersion", EXTRATO_LIMPEZA_POLICY_VERSION);
        root.put("snapshotGeradoEm", fechadoEm);

        Map<String, Object> identificacao = new LinkedHashMap<>();
        identificacao.put("turnoId", turno.getId());
        identificacao.put("tenantId", tenantId);
        identificacao.put("instituicaoId", turno.getInstituicao() != null ? turno.getInstituicao().getId() : null);
        identificacao.put("unidadeAtendimentoId", unidadeId);
        identificacao.put("abertoEm", turno.getAbertoEm());
        identificacao.put("fechadoEm", fechadoEm);
        identificacao.put("fechadoPor", turno.getFechadoPor() != null ? turno.getFechadoPor().getId() : null);
        identificacao.put("fechadoPorRole", ctx.roles() != null ? new ArrayList<>(ctx.roles()) : List.of());
        identificacao.put("tipoFecho", forcar ? "FORCADO" : "NORMAL");
        identificacao.put("motivoFechoForcado", forcar ? motivoFechoForcado : null);
        root.put("identificacao", identificacao);

        Map<String, Object> pedidos = new LinkedHashMap<>();
        pedidos.put("pedidosMapeados", sumMapValues(pre.getPedidosPorStatus()));
        pedidos.put("pedidosPorStatus", pre.getPedidosPorStatus());
        pedidos.put("pedidosCriados", getCount(pre.getPedidosPorStatus(), "CRIADO"));
        pedidos.put("pedidosEmAndamento", getCount(pre.getPedidosPorStatus(), "EM_ANDAMENTO"));
        pedidos.put("pedidosFinalizados", getCount(pre.getPedidosPorStatus(), "FINALIZADO"));
        pedidos.put("pedidosCancelados", getCount(pre.getPedidosPorStatus(), "CANCELADO"));
        root.put("pedidos", pedidos);

        Map<String, Object> subPedidos = new LinkedHashMap<>();
        subPedidos.put("subPedidosPorStatus", pre.getSubPedidosPorStatus());
        subPedidos.put("subpedidosNaoTerminais", countNonTerminal(pre.getSubPedidosPorStatus(), Set.of("ENTREGUE", "CANCELADO")));
        root.put("subPedidos", subPedidos);

        Map<String, Object> sessoes = new LinkedHashMap<>();
        sessoes.put("sessoesAbertas", pre.getSessoesAbertas());
        sessoes.put("sessoesEncerradas", countSessoesComPedidoNoTurno(tenantId, turnoId, StatusSessaoConsumo.ENCERRADA));
        sessoes.put("sessoesExpiradas", countSessoesComPedidoNoTurno(tenantId, turnoId, StatusSessaoConsumo.EXPIRADA));
        sessoes.put("sessoesHerdadas", sessoesHerdadas.size());
        sessoes.put("sessoesHerdadasDetalhes", mapSessoesHerdadas(sessoesHerdadas));
        root.put("sessoes", sessoes);

        Map<String, Object> pagamentos = new LinkedHashMap<>();
        pagamentos.put("pagamentosMapeados", sumMapValues(pre.getPagamentosPorStatus()));
        pagamentos.put("pagamentosPorStatus", pre.getPagamentosPorStatus());
        pagamentos.put("pagamentosConfirmados", getCount(pre.getPagamentosPorStatus(), "CONFIRMADO"));
        pagamentos.put("pagamentosPendentes", getCount(pre.getPagamentosPorStatus(), "PENDENTE"));
        pagamentos.put("pagamentosFalhados", getCount(pre.getPagamentosPorStatus(), "FALHOU"));
        pagamentos.put("pagamentosEstornados", getCount(pre.getPagamentosPorStatus(), "ESTORNADO"));
        pagamentos.put("totalConfirmado", relatorio.getTotalGeralConfirmado());
        pagamentos.put("totalPendente", relatorio.getTotalPendente());
        pagamentos.put("totalFalhado", relatorio.getTotalFalhado());
        pagamentos.put("totalCancelado", sumOrdensByStatus(ordens, OrdemPagamentoStatus.CANCELADA));
        pagamentos.put("totalEstornado", sumPagamentoGatewayByStatus(tenantId, turnoId, StatusPagamentoGateway.ESTORNADO));
        pagamentos.put("totalPorMetodo", financeiro.getTotaisPorMetodo());
        pagamentos.put("totalPorOrigem", financeiro.getTotaisPorOrigem());
        root.put("pagamentos", pagamentos);

        Map<String, Object> ordensPagamento = new LinkedHashMap<>();
        ordensPagamento.put("ordensPagamentoPorStatus", ordensPorStatus);
        ordensPagamento.put("ordensPagamentoAtivas", ordensPorStatus.getOrDefault(OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO.name(), 0L));
        ordensPagamento.put("ordensPagamentoExpiradas", ordensPorStatus.getOrDefault(OrdemPagamentoStatus.EXPIRADA.name(), 0L));
        ordensPagamento.put("ordensPagamentoConfirmadas", ordensPorStatus.getOrDefault(OrdemPagamentoStatus.CONFIRMADA.name(), 0L));
        ordensPagamento.put("ordensPagamentoCanceladas", ordensPorStatus.getOrDefault(OrdemPagamentoStatus.CANCELADA.name(), 0L));
        ordensPagamento.put("ordensPagamentoVencidasAindaAtivas", countOrdensVencidasAindaAtivas(ordens, fechadoEm));
        root.put("ordensPagamento", ordensPagamento);

        Map<String, Object> dispositivos = new LinkedHashMap<>();
        dispositivos.put("dispositivosOffline", pre.getDispositivosOffline());
        dispositivos.put("impactoFinanceiro", "NAO_APLICAVEL");
        root.put("dispositivos", dispositivos);

        Map<String, Object> preFecho = new LinkedHashMap<>();
        preFecho.put("podeFecharNoMomento", pre.isPodeFechar());
        preFecho.put("bloqueiosNoMomento", pre.getBloqueios());
        preFecho.put("alertasNoMomento", pre.getAvisos());
        preFecho.put("alertasFinanceiros", pre.getAlertasFinanceiros());
        root.put("preFecho", preFecho);

        Map<String, Object> auditoria = new LinkedHashMap<>();
        auditoria.put("snapshotFinanceiroHash", financeiro.getIntegridade() != null ? financeiro.getIntegridade().getSnapshotHash() : null);
        auditoria.put("snapshotFinanceiroAssinado", financeiro.getIntegridade() != null
                && financeiro.getIntegridade().getSnapshotSignature() != null
                && !financeiro.getIntegridade().getSnapshotSignature().isBlank());
        auditoria.put("efeitoEmPedidosPagamentosOrdens", "NAO_ALTERA_ESTADOS");
        auditoria.put("estadoVivoNaoRecalculadoNoExtrato", true);
        root.put("auditoria", auditoria);

        return root;
    }

    private Map<String, Object> buildLimpezaOperacionalSnapshot(TurnoPreFechoResponse pre,
                                                               Map<String, Object> operacional,
                                                               boolean forcar) {
        Map<String, Object> limpeza = new LinkedHashMap<>();
        limpeza.put("policyVersion", EXTRATO_LIMPEZA_POLICY_VERSION);
        limpeza.put("tipo", "CLASSIFICACAO_SEM_MUTACAO");
        limpeza.put("apagaSessoes", false);
        limpeza.put("alteraPedidos", false);
        limpeza.put("alteraPagamentos", false);
        limpeza.put("alteraOrdensPagamento", false);
        limpeza.put("alteraRestKds", false);
        limpeza.put("turnoFechadoComPendencias", hasPendenciasOperacionais(pre, operacional));
        limpeza.put("pendenciasHerdadas", buildPendenciasHerdadasSnapshot(pre, operacional, forcar));
        return limpeza;
    }

    private Map<String, Long> countOrdensPorStatus(List<OrdemPagamento> ordens) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (OrdemPagamentoStatus status : OrdemPagamentoStatus.values()) {
            out.put(status.name(), 0L);
        }
        for (OrdemPagamento ordem : ordens != null ? ordens : List.<OrdemPagamento>of()) {
            if (ordem.getStatus() != null) {
                out.put(ordem.getStatus().name(), out.getOrDefault(ordem.getStatus().name(), 0L) + 1);
            }
        }
        return out;
    }

    private long sumMapValues(Map<String, Long> values) {
        if (values == null || values.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (Long value : values.values()) {
            total += value != null ? value : 0L;
        }
        return total;
    }

    private long getCount(Map<String, Long> values, String key) {
        return values != null ? values.getOrDefault(key, 0L) : 0L;
    }

    private long countSessoesComPedidoNoTurno(Long tenantId, Long turnoId, StatusSessaoConsumo status) {
        if (tenantId == null || turnoId == null || status == null) {
            return 0L;
        }
        return sessaoConsumoRepository.countDistinctByTenantIdAndPedidoTurnoOperacionalIdAndStatus(
                tenantId,
                turnoId,
                status
        );
    }

    private List<Map<String, Object>> mapSessoesHerdadas(List<SessaoConsumo> sessoes) {
        if (sessoes == null || sessoes.isEmpty()) {
            return List.of();
        }
        return sessoes.stream()
                .sorted(Comparator.comparing(SessaoConsumo::getId, Comparator.nullsLast(Long::compareTo)))
                .map(sessao -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("sessaoId", sessao.getId());
                    item.put("status", sessao.getStatus() != null ? sessao.getStatus().name() : null);
                    item.put("abertaEm", sessao.getAbertaEm());
                    item.put("ultimaAtividadeEm", sessao.getUltimaAtividadeEm());
                    item.put("motivoNaoLimpeza", "STATUS_OPERACIONALMENTE_ABERTO");
                    return item;
                })
                .collect(Collectors.toList());
    }

    private BigDecimal sumOrdensByStatus(List<OrdemPagamento> ordens, OrdemPagamentoStatus status) {
        if (ordens == null || ordens.isEmpty() || status == null) {
            return BigDecimal.ZERO;
        }
        return ordens.stream()
                .filter(o -> o.getStatus() == status)
                .map(OrdemPagamento::getValor)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumPagamentoGatewayByStatus(Long tenantId, Long turnoId, StatusPagamentoGateway status) {
        if (tenantId == null || turnoId == null || status == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = pagamentoGatewayRepository.sumByTenantIdAndTurnoOperacionalIdAndStatus(tenantId, turnoId, status);
        return total != null ? total : BigDecimal.ZERO;
    }

    private long countOrdensVencidasAindaAtivas(List<OrdemPagamento> ordens, LocalDateTime fechadoEm) {
        if (ordens == null || ordens.isEmpty()) {
            return 0L;
        }
        LocalDateTime ref = fechadoEm != null ? fechadoEm : LocalDateTime.now();
        return ordens.stream()
                .filter(o -> o.getStatus() == OrdemPagamentoStatus.AGUARDANDO_CONFIRMACAO)
                .filter(o -> o.isExpirada(ref))
                .count();
    }

    @SuppressWarnings("unchecked")
    private boolean hasPendenciasOperacionais(TurnoPreFechoResponse pre, Map<String, Object> operacional) {
        if (pre.getBloqueios() != null && !pre.getBloqueios().isEmpty()) {
            return true;
        }
        if (pre.getAvisos() != null && !pre.getAvisos().isEmpty()) {
            return true;
        }
        if (pre.getAlertasFinanceiros() != null && pre.getAlertasFinanceiros().getTotalPagamentosPendentes() > 0) {
            return true;
        }
        Map<String, Object> ordens = (Map<String, Object>) operacional.get("ordensPagamento");
        Number ordensAtivas = ordens != null ? (Number) ordens.get("ordensPagamentoAtivas") : 0L;
        return ordensAtivas != null && ordensAtivas.longValue() > 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildPendenciasHerdadasSnapshot(TurnoPreFechoResponse pre,
                                                               Map<String, Object> operacional,
                                                               boolean forcar) {
        Map<String, Object> pendencias = new LinkedHashMap<>(pendenciasHerdadas(pre));
        Map<String, Object> ordens = (Map<String, Object>) operacional.get("ordensPagamento");
        if (ordens != null) {
            pendencias.put("ordensPagamentoAtivas", ordens.getOrDefault("ordensPagamentoAtivas", 0L));
            pendencias.put("ordensPagamentoExpiradas", ordens.getOrDefault("ordensPagamentoExpiradas", 0L));
            pendencias.put("ordensPagamentoVencidasAindaAtivas", ordens.getOrDefault("ordensPagamentoVencidasAindaAtivas", 0L));
        }
        Map<String, Object> sessoes = (Map<String, Object>) operacional.get("sessoes");
        if (sessoes != null) {
            pendencias.put("sessoesHerdadas", sessoes.getOrDefault("sessoesHerdadas", 0L));
            pendencias.put("sessoesHerdadasDetalhes", sessoes.getOrDefault("sessoesHerdadasDetalhes", List.of()));
        }
        pendencias.put("fechoForcado", forcar);
        pendencias.put("classificacao", hasPendenciasOperacionais(pre, operacional) ? "COM_PENDENCIAS" : "SEM_PENDENCIAS");
        return pendencias;
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
        s.setSnapshotVersion(FINANCEIRO_SNAPSHOT_VERSION);
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

            if (snapshotIntegridadeProperties.isSignatureEnabled()) {
                integ.setSignatureScope("snapshotHash");
                SnapshotSignatureResult sig = snapshotSignatureService.sign(hash);
                integ.setSignatureAlgorithm(sig.getAlgorithm());
                integ.setSignatureKeyId(sig.getKeyId());
                integ.setSignatureGeneratedAt(sig.getGeneratedAt() != null ? sig.getGeneratedAt() : fechadoEm);
                integ.setSnapshotSignature(sig.getSignature());
            }

            s.setIntegridade(integ);
        }

        return s;
    }

    private String validarMotivoFechoForcado(FecharTurnoRequest request) {
        String motivo = request.getMotivoFechoForcado();
        if (motivo == null || motivo.isBlank()) {
            motivo = request.getObservacao();
        }
        if (motivo == null || motivo.isBlank()) {
            throw new ConflictException("Motivo obrigatório para fecho forçado.");
        }
        String normalized = motivo.trim();
        if (normalized.length() < MOTIVO_FECHO_FORCADO_MIN_LENGTH) {
            throw new ConflictException("Motivo do fecho forçado deve ter pelo menos 10 caracteres.");
        }
        if (normalized.length() > MOTIVO_FECHO_FORCADO_MAX_LENGTH) {
            throw new ConflictException("Motivo do fecho forçado deve ter no máximo 500 caracteres.");
        }
        return normalized;
    }

    private ForceAutoClosureResult tentarAutoFechoSessoesPontoElegiveis(TurnoOperacional turno) {
        if (turno.getTenant() == null || turno.getUnidadeAtendimento() == null) {
            return ForceAutoClosureResult.empty();
        }

        List<SessaoConsumo> sessoes = sessaoConsumoRepository.findByTenantIdAndUnidadeAtendimentoIdAndStatusIn(
                turno.getTenant().getId(),
                turno.getUnidadeAtendimento().getId(),
                SESSOES_OPERACIONALMENTE_ABERTAS
        );
        if (sessoes.isEmpty()) {
            return ForceAutoClosureResult.empty();
        }

        List<Long> encerradas = new ArrayList<>();
        for (SessaoConsumo sessao : sessoes) {
            StatusSessaoConsumo statusAntes = sessao.getStatus();
            sessaoConsumoAutoClosureService.tryAutoCloseSessaoConsumo(sessao.getId());
            SessaoConsumo atualizada = sessaoConsumoRepository.findByIdAndTenantId(
                    sessao.getId(),
                    turno.getTenant().getId()
            ).orElse(null);
            if (atualizada != null
                    && atualizada.getStatus() == StatusSessaoConsumo.ENCERRADA
                    && statusAntes != StatusSessaoConsumo.ENCERRADA) {
                encerradas.add(atualizada.getId());
            }
        }
        return new ForceAutoClosureResult(sessoes.size(), encerradas.size(), encerradas);
    }

    private List<String> bloqueiosNaoIgnoraveis(TurnoPreFechoResponse pre) {
        List<String> bloqueios = new ArrayList<>();
        if (pre.getAlertasFinanceiros() != null && pre.getAlertasFinanceiros().isBloqueiaFecho()) {
            bloqueios.add("ALERTA_FINANCEIRO_CRITICO_BLOQUEANTE");
        }
        return bloqueios;
    }

    private void logFechoForcadoBloqueado(TurnoOperacional turno,
                                          TenantContext ctx,
                                          String motivoFechoForcado,
                                          TurnoPreFechoResponse pre,
                                          List<String> bloqueiosNaoIgnoraveis,
                                          ForceAutoClosureResult autoClosureResult,
                                          String ip,
                                          String userAgent) {
        Map<String, Object> metadata = buildFechoForcadoPolicyMetadata(ctx, turno, pre, motivoFechoForcado,
                bloqueiosNaoIgnoraveis, autoClosureResult);
        metadata.put("forcarFecho", true);
        metadata.put("resultado", "BLOQUEADO");
        if (pre.getAlertasFinanceiros() != null) {
            metadata.put("totalPendentes", pre.getAlertasFinanceiros().getTotalPagamentosPendentes());
            metadata.put("totalCriticos", pre.getAlertasFinanceiros().getTotalCriticos());
            metadata.put("valorPendente", pre.getAlertasFinanceiros().getValorPendente());
        }
        operationalEventLogService.logTurnoEvent(
                OperationalEventType.TURNO_FECHO_BLOQUEADO_ALERTA_FINANCEIRO,
                turno,
                resolveOrigemFromRoles(ctx),
                "Fecho forçado bloqueado por pendência não ignorável",
                metadata,
                ip,
                userAgent
        );
    }

    private Map<String, Object> buildFechoForcadoPolicyMetadata(TenantContext ctx,
                                                                TurnoOperacional turno,
                                                                TurnoPreFechoResponse pre,
                                                                String motivoFechoForcado,
                                                                List<String> bloqueiosNaoIgnoraveis,
                                                                ForceAutoClosureResult autoClosureResult) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("policy", "TURNO_FECHO_FORCADO_POLICY_001");
        metadata.put("forcarFecho", true);
        metadata.put("motivoFechoForcado", motivoFechoForcado);
        metadata.put("actorUserId", ctx.userId());
        metadata.put("actorRoles", ctx.roles());
        metadata.put("tenantId", turno.getTenant() != null ? turno.getTenant().getId() : null);
        metadata.put("turnoId", turno.getId());
        metadata.put("unidadeAtendimentoId", turno.getUnidadeAtendimento() != null ? turno.getUnidadeAtendimento().getId() : null);
        metadata.put("bloqueiosPreFecho", pre.getBloqueios());
        metadata.put("bloqueiosIgnorados", bloqueiosIgnorados(pre, bloqueiosNaoIgnoraveis));
        metadata.put("bloqueiosNaoIgnoraveis", bloqueiosNaoIgnoraveis);
        metadata.put("avisosFlexiveis", pre.getAvisos());
        metadata.put("sessoesAutoFechoTentadas", autoClosureResult.tentadas());
        metadata.put("sessoesAutoFechadas", autoClosureResult.encerradas());
        metadata.put("sessoesAutoFechadasIds", autoClosureResult.encerradasIds());
        metadata.put("pendenciasHerdadas", pendenciasHerdadas(pre));
        metadata.put("efeitoEmPedidosPagamentosOrdens", "NAO_ALTERA_ESTADOS");
        metadata.put("preFechoRecalculadoAposAutoFecho", autoClosureResult.tentadas() > 0);
        return metadata;
    }

    private List<String> bloqueiosIgnorados(TurnoPreFechoResponse pre, List<String> bloqueiosNaoIgnoraveis) {
        if (pre.getBloqueios() == null || pre.getBloqueios().isEmpty()) {
            return List.of();
        }
        if (bloqueiosNaoIgnoraveis == null || bloqueiosNaoIgnoraveis.isEmpty()) {
            return new ArrayList<>(pre.getBloqueios());
        }
        List<String> ignorados = new ArrayList<>();
        for (String bloqueio : pre.getBloqueios()) {
            if (bloqueio == null || !bloqueio.toLowerCase().contains("pagamentos críticos")) {
                ignorados.add(bloqueio);
            }
        }
        return ignorados;
    }

    private Map<String, Object> pendenciasHerdadas(TurnoPreFechoResponse pre) {
        Map<String, Object> pendencias = new HashMap<>();
        pendencias.put("sessoesAbertas", pre.getSessoesAbertas());
        pendencias.put("pedidosNaoTerminais", countNonTerminal(pre.getPedidosPorStatus(), Set.of("FINALIZADO", "CANCELADO")));
        pendencias.put("subPedidosNaoTerminais", countNonTerminal(pre.getSubPedidosPorStatus(), Set.of("ENTREGUE", "CANCELADO")));
        pendencias.put("pagamentosPendentes", pre.getPagamentosPorStatus() != null
                ? pre.getPagamentosPorStatus().getOrDefault("PENDENTE", 0L)
                : 0L);
        pendencias.put("dispositivosOffline", pre.getDispositivosOffline());
        if (pre.getAlertasFinanceiros() != null) {
            pendencias.put("alertasFinanceirosPendentes", pre.getAlertasFinanceiros().getTotalPagamentosPendentes());
            pendencias.put("alertasFinanceirosCriticos", pre.getAlertasFinanceiros().getTotalCriticos());
            pendencias.put("alertaFinanceiroBloqueiaFecho", pre.getAlertasFinanceiros().isBloqueiaFecho());
        }
        return pendencias;
    }

    private long countNonTerminal(Map<String, Long> porStatus, Set<String> terminais) {
        if (porStatus == null || porStatus.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (Map.Entry<String, Long> entry : porStatus.entrySet()) {
            if (!terminais.contains(entry.getKey())) {
                total += entry.getValue() != null ? entry.getValue() : 0L;
            }
        }
        return total;
    }

    private Map<String, Object> buildTurnoFechadoMetadata(TurnoPreFechoResponse pre,
                                                          RelatorioCaixaTurnoResponse relatorio,
                                                          ResumoFinanceiroFechoTurnoSnapshot snapshot,
                                                          Map<String, Object> fechoForcadoPolicyMetadata) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("bloqueios", pre.getBloqueios());
        metadata.put("avisos", pre.getAvisos());
        metadata.put("financeiroSnapshotPersistido", true);
        metadata.put("snapshotVersion", snapshot.getSnapshotVersion());
        metadata.put("totalGeralConfirmado", relatorio.getTotalGeralConfirmado());
        metadata.put("totalPendente", relatorio.getTotalPendente());
        if (pre.getAlertasFinanceiros() != null) {
            metadata.put("totalCriticos", pre.getAlertasFinanceiros().getTotalCriticos());
        }
        if (snapshot.getIntegridade() != null) {
            metadata.put("snapshotHash", snapshot.getIntegridade().getSnapshotHash());
            metadata.put("hashAlgorithm", snapshot.getIntegridade().getHashAlgorithm());
            metadata.put("canonicalizationVersion", snapshot.getIntegridade().getCanonicalizationVersion());
        }
        if (fechoForcadoPolicyMetadata != null && !fechoForcadoPolicyMetadata.isEmpty()) {
            metadata.putAll(fechoForcadoPolicyMetadata);
            metadata.put("resultado", "FECHADO_FORCADO");
        }
        return metadata;
    }

    private OperationalOrigem resolveOrigemFromRoles(TenantContext ctx) {
        if (ctx == null || ctx.roles() == null) return OperationalOrigem.SYSTEM;
        if (ctx.roles().contains("TENANT_ADMIN") || ctx.roles().contains("TENANT_OWNER")) return OperationalOrigem.TENANT_ADMIN;
        if (ctx.roles().contains("TENANT_OPERATOR")) return OperationalOrigem.TENANT_OPERATOR;
        if (ctx.roles().contains("TENANT_CASHIER")) return OperationalOrigem.TENANT_CASHIER;
        if (ctx.roles().contains("TENANT_KITCHEN")) return OperationalOrigem.TENANT_KITCHEN;
        return OperationalOrigem.TENANT_OPERATOR;
    }

    private record ForceAutoClosureResult(int tentadas, int encerradas, List<Long> encerradasIds) {
        static ForceAutoClosureResult empty() {
            return new ForceAutoClosureResult(0, 0, List.of());
        }
    }
}
