package com.restaurante.financeiro.snapshot.evidence.service;

import com.restaurante.financeiro.snapshot.dto.SnapshotFinanceiroExportResponse;
import com.restaurante.financeiro.snapshot.evidence.dto.EvidenceBundleEventoDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.EvidenceBundleExportMetadataDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.EvidenceBundleGeneratedByDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.EvidenceBundleInstituicaoDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.EvidenceBundlePagamentosResumoDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.EvidenceBundleTenantDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.EvidenceBundleTurnoDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.EvidenceBundleUnidadeDTO;
import com.restaurante.financeiro.snapshot.evidence.dto.SnapshotFinanceiroEvidenceBundleResponse;
import com.restaurante.financeiro.snapshot.evidence.EvidenceBundleProperties;
import com.restaurante.model.entity.OperationalEventLog;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.OperationalEventLogRepository;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.security.tenant.TenantGuard;
import com.restaurante.service.operacional.OperationalEventLogService;
import com.restaurante.financeiro.snapshot.service.SnapshotFinanceiroExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SnapshotFinanceiroEvidenceBundleService {

    private final TenantGuard tenantGuard;
    private final TurnoOperacionalRepository turnoOperacionalRepository;
    private final SnapshotFinanceiroExportService exportService;
    private final OperationalEventLogRepository operationalEventLogRepository;
    private final OperationalEventLogService operationalEventLogService;
    private final EvidenceBundleProperties evidenceBundleProperties;

    private static final Set<OperationalEventType> EVENT_TYPES = Set.of(
            OperationalEventType.TURNO_ABERTO,
            OperationalEventType.CHECKLIST_ABERTURA_CONCLUIDO,
            OperationalEventType.PEDIDO_CRIADO_DEVICE,
            OperationalEventType.PAGAMENTO_INICIADO_DEVICE,
            OperationalEventType.PAGAMENTO_CONFIRMADO_POR_POLLING,
            OperationalEventType.PAGAMENTO_POLLING_MANUAL_EXECUTADO,
            OperationalEventType.SNAPSHOT_FINANCEIRO_HASH_GERADO,
            OperationalEventType.SNAPSHOT_FINANCEIRO_ASSINATURA_GERADA,
            OperationalEventType.SNAPSHOT_FINANCEIRO_EXPORTADO,
            OperationalEventType.SNAPSHOT_FINANCEIRO_INTEGRIDADE_INVALIDA,
            OperationalEventType.SNAPSHOT_FINANCEIRO_ASSINATURA_INVALIDA,
            OperationalEventType.TURNO_FECHADO,
            OperationalEventType.TURNO_FECHADO_FORCADO
    );

    @Transactional
    public SnapshotFinanceiroEvidenceBundleResponse gerar(Long turnoId, String ip, String userAgent) {
        tenantGuard.assertAnyTenantRole(
                TenantUserRole.TENANT_OWNER,
                TenantUserRole.TENANT_ADMIN,
                TenantUserRole.TENANT_FINANCE,
                TenantUserRole.TENANT_CASHIER
        );
        TenantContext ctx = TenantContextHolder.require();

        TurnoOperacional turno = turnoOperacionalRepository.findByIdAndTenantId(turnoId, ctx.tenantId())
                .orElseThrow(() -> new com.restaurante.exception.ResourceNotFoundException("Recurso não encontrado."));

        SnapshotFinanceiroExportResponse export = exportService.exportar(turnoId, ip, userAgent);

        int maxEvents = Math.max(1, evidenceBundleProperties.getMaxEvents());
        List<OperationalEventLog> eventos = operationalEventLogRepository.findTopByTenantAndTurnoAndEventTypes(
                ctx.tenantId(),
                turnoId,
                EVENT_TYPES,
                PageRequest.of(0, maxEvents)
        );

        SnapshotFinanceiroEvidenceBundleResponse out = new SnapshotFinanceiroEvidenceBundleResponse();
        out.setBundleVersion("v1");
        out.setGeneratedAt(LocalDateTime.now());

        EvidenceBundleGeneratedByDTO genBy = new EvidenceBundleGeneratedByDTO();
        genBy.setUserId(ctx.userId());
        genBy.setActorType("USER");
        out.setGeneratedBy(genBy);

        EvidenceBundleTenantDTO tenant = new EvidenceBundleTenantDTO();
        tenant.setTenantId(turno.getTenant() != null ? turno.getTenant().getId() : null);
        tenant.setTenantNome(turno.getTenant() != null ? turno.getTenant().getNome() : null);
        tenant.setTenantCode(turno.getTenant() != null ? turno.getTenant().getTenantCode() : null);
        out.setTenant(tenant);

        EvidenceBundleInstituicaoDTO inst = new EvidenceBundleInstituicaoDTO();
        inst.setInstituicaoId(turno.getInstituicao() != null ? turno.getInstituicao().getId() : null);
        inst.setNome(turno.getInstituicao() != null ? turno.getInstituicao().getNome() : null);
        out.setInstituicao(inst);

        EvidenceBundleUnidadeDTO ua = new EvidenceBundleUnidadeDTO();
        ua.setUnidadeAtendimentoId(turno.getUnidadeAtendimento() != null ? turno.getUnidadeAtendimento().getId() : null);
        ua.setNome(turno.getUnidadeAtendimento() != null ? turno.getUnidadeAtendimento().getNome() : null);
        out.setUnidadeAtendimento(ua);

        EvidenceBundleTurnoDTO t = new EvidenceBundleTurnoDTO();
        t.setTurnoId(turno.getId());
        t.setStatus(turno.getStatus().name());
        t.setAbertoEm(turno.getAbertoEm());
        t.setFechadoEm(turno.getFechadoEm());
        t.setAbertoPorUserId(turno.getAbertoPor() != null ? turno.getAbertoPor().getId() : null);
        t.setFechadoPorUserId(turno.getFechadoPor() != null ? turno.getFechadoPor().getId() : null);
        t.setObservacaoFecho(turno.getObservacaoFecho());
        t.setFechamentoForcado(eventos.stream().anyMatch(e -> e.getEventType() == OperationalEventType.TURNO_FECHADO_FORCADO));
        out.setTurno(t);

        out.setSnapshotExport(export);

        out.setEventosOperacionais(eventos.stream().map(this::toEventoDTO).toList());
        out.setPagamentosResumo(toPagamentosResumo(export));

        EvidenceBundleExportMetadataDTO meta = new EvidenceBundleExportMetadataDTO();
        meta.setFormato("JSON_LOGICO");
        meta.setExportadoEm(out.getGeneratedAt());
        out.setExportMetadata(meta);

        operationalEventLogService.logTurnoEvent(
                OperationalEventType.SNAPSHOT_FINANCEIRO_EVIDENCE_BUNDLE_EXPORTADO,
                turno,
                resolveOrigemFromRoles(ctx),
                "Evidence bundle exportado",
                java.util.Map.of(
                        "bundleVersion", out.getBundleVersion(),
                        "valido", export.getVerificacao() != null && export.getVerificacao().isValido(),
                        "signatureKeyId", export.getIntegridade() != null ? export.getIntegridade().getSignatureKeyId() : null
                ),
                ip,
                userAgent
        );

        return out;
    }

    private EvidenceBundleEventoDTO toEventoDTO(OperationalEventLog e) {
        EvidenceBundleEventoDTO dto = new EvidenceBundleEventoDTO();
        dto.setEventId(e.getId());
        dto.setEventType(e.getEventType() != null ? e.getEventType().name() : null);
        dto.setEntityType(e.getEntityType() != null ? e.getEntityType().name() : null);
        dto.setEntityId(e.getEntityId());
        dto.setActorType(e.getActorType() != null ? e.getActorType().name() : null);
        dto.setActorUserId(e.getActorUser() != null ? e.getActorUser().getId() : null);
        dto.setDeviceId(e.getDispositivo() != null ? e.getDispositivo().getId() : null);
        dto.setOrigem(e.getOrigem() != null ? e.getOrigem().name() : null);
        dto.setCreatedAt(e.getCreatedAt());
        dto.setMotivo(e.getMotivo());
        return dto;
    }

    private EvidenceBundlePagamentosResumoDTO toPagamentosResumo(SnapshotFinanceiroExportResponse export) {
        EvidenceBundlePagamentosResumoDTO r = new EvidenceBundlePagamentosResumoDTO();
        if (export == null || export.getSnapshotFinanceiro() == null) return r;
        var fin = export.getSnapshotFinanceiro();
        r.setQuantidadePagamentosConfirmados(fin.path("quantidadePagamentosConfirmados").isMissingNode() ? null : fin.path("quantidadePagamentosConfirmados").asInt());
        r.setQuantidadePagamentosPendentes(fin.path("quantidadePagamentosPendentes").isMissingNode() ? null : fin.path("quantidadePagamentosPendentes").asInt());
        r.setQuantidadeOrdensManuaisConfirmadas(fin.path("quantidadeOrdensManuaisConfirmadas").isMissingNode() ? null : fin.path("quantidadeOrdensManuaisConfirmadas").asInt());
        r.setTotalGeralConfirmado(parseBig(fin.get("totalGeralConfirmado")));
        r.setTotalManualConfirmado(parseBig(fin.get("totalManualConfirmado")));
        r.setTotalGatewayConfirmado(parseBig(fin.get("totalGatewayConfirmado")));
        r.setTotalPendente(parseBig(fin.get("totalPendente")));
        r.setTotalFalhado(parseBig(fin.get("totalFalhado")));
        r.setTotalDivergente(parseBig(fin.get("totalDivergente")));
        return r;
    }

    private java.math.BigDecimal parseBig(com.fasterxml.jackson.databind.JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) return null;
        try {
            if (n.isNumber()) return n.decimalValue();
            String t = n.asText();
            if (t == null || t.isBlank()) return null;
            return new java.math.BigDecimal(t);
        } catch (Exception e) {
            return null;
        }
    }

    private OperationalOrigem resolveOrigemFromRoles(TenantContext ctx) {
        if (ctx == null || ctx.roles() == null) return OperationalOrigem.SYSTEM;
        if (ctx.roles().contains("TENANT_ADMIN") || ctx.roles().contains("TENANT_OWNER")) return OperationalOrigem.TENANT_ADMIN;
        if (ctx.roles().contains("TENANT_FINANCE")) return OperationalOrigem.TENANT_FINANCE;
        if (ctx.roles().contains("TENANT_CASHIER")) return OperationalOrigem.TENANT_CASHIER;
        return OperationalOrigem.TENANT_OPERATOR;
    }
}
