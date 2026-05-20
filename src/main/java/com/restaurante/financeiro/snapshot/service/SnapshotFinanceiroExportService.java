package com.restaurante.financeiro.snapshot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.restaurante.exception.ConflictException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.snapshot.CanonicalJsonHashService;
import com.restaurante.financeiro.snapshot.SnapshotIntegridadeProperties;
import com.restaurante.financeiro.snapshot.dto.SnapshotFinanceiroExportResponse;
import com.restaurante.financeiro.snapshot.dto.SnapshotIntegridadeResponse;
import com.restaurante.financeiro.snapshot.dto.SnapshotVerificacaoIntegridadeResponse;
import com.restaurante.model.entity.TurnoOperacional;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.TurnoOperacionalStatus;
import com.restaurante.repository.TurnoOperacionalRepository;
import com.restaurante.security.tenant.TenantContext;
import com.restaurante.security.tenant.TenantContextHolder;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SnapshotFinanceiroExportService {

    private final TurnoOperacionalRepository turnoOperacionalRepository;
    private final ObjectMapper objectMapper;
    private final CanonicalJsonHashService canonicalJsonHashService;
    private final SnapshotIntegridadeProperties props;
    private final OperationalEventLogService operationalEventLogService;

    @Transactional
    public SnapshotFinanceiroExportResponse exportar(Long turnoId, String ip, String userAgent) {
        TenantContext ctx = TenantContextHolder.require();
        TurnoOperacional turno = turnoOperacionalRepository.findByIdAndTenantId(turnoId, ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        if (turno.getStatus() != TurnoOperacionalStatus.FECHADO) {
            throw new ConflictException("Turno não está FECHADO para exportar snapshot.");
        }
        if (turno.getResumoJson() == null || turno.getResumoJson().isBlank()) {
            throw new ConflictException("Turno fechado não possui resumo_json.");
        }

        ObjectNode root = readJsonObjectOrFail(turno.getResumoJson());
        JsonNode financeiro = root.get("financeiro");
        if (financeiro == null || !financeiro.isObject()) {
            throw new ConflictException("Turno fechado não possui snapshot financeiro congelado.");
        }

        // Compatibilidade: gerar integridade on-demand para snapshots antigos sem hash.
        ObjectNode finObj = (ObjectNode) financeiro;
        ObjectNode integridadeNode = finObj.has("integridade") && finObj.get("integridade").isObject()
                ? (ObjectNode) finObj.get("integridade")
                : null;

        if (props.isEnabled() && (integridadeNode == null || !integridadeNode.hasNonNull("snapshotHash"))) {
            SnapshotIntegridadeResponse integ = buildIntegridade(finObj);
            finObj.set("integridade", objectMapper.valueToTree(integ));
            root.set("financeiro", finObj);
            turno.setResumoJson(writeJson(root));
            turnoOperacionalRepository.save(turno);

            operationalEventLogService.logTurnoEvent(
                    OperationalEventType.SNAPSHOT_FINANCEIRO_HASH_GERADO,
                    turno,
                    resolveOrigemFromRoles(ctx),
                    "Hash de integridade do snapshot financeiro gerado (on-demand)",
                    new HashMap<>() {{
                        put("hashAlgorithm", integ.getHashAlgorithm());
                        put("canonicalizationVersion", integ.getCanonicalizationVersion());
                        put("snapshotHash", integ.getSnapshotHash());
                    }},
                    ip,
                    userAgent
            );
        }

        // Recarrega do root já com integridade (se gerada)
        finObj = (ObjectNode) root.get("financeiro");
        SnapshotIntegridadeResponse integ = finObj.has("integridade")
                ? objectMapper.convertValue(finObj.get("integridade"), SnapshotIntegridadeResponse.class)
                : null;

        SnapshotVerificacaoIntegridadeResponse ver = verificar(finObj, integ);
        if (props.isAuditExport()) {
            operationalEventLogService.logTurnoEvent(
                    OperationalEventType.SNAPSHOT_FINANCEIRO_EXPORTADO,
                    turno,
                    resolveOrigemFromRoles(ctx),
                    "Snapshot financeiro exportado",
                    new HashMap<>() {{
                        put("hashAlgorithm", integ != null ? integ.getHashAlgorithm() : null);
                        put("canonicalizationVersion", integ != null ? integ.getCanonicalizationVersion() : null);
                        put("snapshotHash", integ != null ? integ.getSnapshotHash() : null);
                        put("valido", ver.isValido());
                    }},
                    ip,
                    userAgent
            );
        }
        if (!ver.isValido()) {
            operationalEventLogService.logTurnoEvent(
                    OperationalEventType.SNAPSHOT_FINANCEIRO_INTEGRIDADE_INVALIDA,
                    turno,
                    resolveOrigemFromRoles(ctx),
                    "Snapshot financeiro com integridade inválida",
                    new HashMap<>() {{
                        put("hashPersistido", ver.getHashPersistido());
                        put("hashRecalculado", ver.getHashRecalculado());
                        put("motivo", ver.getMotivo());
                    }},
                    ip,
                    userAgent
            );
        }

        SnapshotFinanceiroExportResponse resp = new SnapshotFinanceiroExportResponse();
        resp.setExportVersion("37.2");
        resp.setExportadoEm(LocalDateTime.now());
        resp.setTurnoId(turno.getId());
        resp.setTenantId(turno.getTenant() != null ? turno.getTenant().getId() : null);
        resp.setInstituicaoId(turno.getInstituicao() != null ? turno.getInstituicao().getId() : null);
        resp.setUnidadeAtendimentoId(turno.getUnidadeAtendimento() != null ? turno.getUnidadeAtendimento().getId() : null);
        resp.setStatusTurno(turno.getStatus().name());
        resp.setFechadoEm(turno.getFechadoEm());
        resp.setSnapshotVersion(finObj.path("snapshotVersion").asText(null));
        resp.setSnapshotFinanceiro(finObj);
        resp.setIntegridade(integ);
        resp.setVerificacao(ver);
        resp.setObservacoes(Map.of("hashOnDemandGenerated", integ != null && (integ.getHashGeneratedAt() != null)));
        return resp;
    }

    private ObjectNode readJsonObjectOrFail(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node instanceof ObjectNode on) return on;
            throw new ConflictException("resumo_json inválido (não é objeto).");
        } catch (Exception e) {
            throw new ConflictException("resumo_json inválido (parse falhou).");
        }
    }

    private String writeJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao persistir resumo_json.", e);
        }
    }

    private SnapshotIntegridadeResponse buildIntegridade(ObjectNode financeiro) {
        SnapshotIntegridadeResponse integ = new SnapshotIntegridadeResponse();
        integ.setHashAlgorithm(props.getAlgorithm());
        integ.setCanonicalizationVersion(props.getCanonicalizationVersion());
        integ.setHashGeneratedAt(LocalDateTime.now());
        integ.setHashScope("resumo_json.financeiro_sem_integridade");

        String hash = canonicalJsonHashService.hashHexCanonical(
                props.getAlgorithm(),
                financeiro,
                List.of("integridade")
        );
        integ.setSnapshotHash(hash);
        return integ;
    }

    private SnapshotVerificacaoIntegridadeResponse verificar(ObjectNode financeiro, SnapshotIntegridadeResponse integ) {
        SnapshotVerificacaoIntegridadeResponse ver = new SnapshotVerificacaoIntegridadeResponse();
        ver.setVerificadoEm(LocalDateTime.now());

        if (!props.isEnabled()) {
            ver.setValido(true);
            ver.setMotivo("Integridade desativada por configuração.");
            return ver;
        }
        if (integ == null || integ.getSnapshotHash() == null || integ.getSnapshotHash().isBlank()) {
            ver.setValido(false);
            ver.setMotivo("Hash persistido ausente.");
            return ver;
        }

        String recalculado = canonicalJsonHashService.hashHexCanonical(
                integ.getHashAlgorithm() != null ? integ.getHashAlgorithm() : props.getAlgorithm(),
                financeiro,
                List.of("integridade")
        );
        ver.setHashPersistido(integ.getSnapshotHash());
        ver.setHashRecalculado(recalculado);
        ver.setValido(integ.getSnapshotHash().equals(recalculado));
        if (!ver.isValido()) {
            ver.setMotivo("Hash não confere (snapshot possivelmente adulterado).");
        } else {
            ver.setMotivo("OK");
        }
        return ver;
    }

    private OperationalOrigem resolveOrigemFromRoles(TenantContext ctx) {
        if (ctx == null || ctx.roles() == null) return OperationalOrigem.SYSTEM;
        if (ctx.roles().contains("TENANT_ADMIN") || ctx.roles().contains("TENANT_OWNER")) return OperationalOrigem.TENANT_ADMIN;
        if (ctx.roles().contains("TENANT_FINANCE")) return OperationalOrigem.TENANT_FINANCE;
        if (ctx.roles().contains("TENANT_CASHIER")) return OperationalOrigem.TENANT_CASHIER;
        return OperationalOrigem.TENANT_OPERATOR;
    }
}

