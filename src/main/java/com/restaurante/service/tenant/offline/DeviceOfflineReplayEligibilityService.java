package com.restaurante.service.tenant.offline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.device.offline.entity.DeviceOfflineCommand;
import com.restaurante.device.offline.repository.DeviceOfflineCommandRepository;
import com.restaurante.dto.response.OfflineCommandReplayEligibilityResponse;
import com.restaurante.financeiro.repository.OrdemPagamentoRepository;
import com.restaurante.model.entity.OrdemPagamento;
import com.restaurante.model.enums.DeviceOfflineCommandStatus;
import com.restaurante.model.enums.DeviceOfflineCommandType;
import com.restaurante.model.enums.DeviceOfflineConflictCode;
import com.restaurante.model.enums.DeviceOfflineReplayEligibilityReason;
import com.restaurante.model.enums.DeviceOfflineReplayEligibilityStatus;
import com.restaurante.model.enums.OrdemPagamentoStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceOfflineReplayEligibilityService {

    private final ObjectMapper objectMapper;
    private final DeviceOfflineCommandRepository commandRepository;
    private final OrdemPagamentoRepository ordemPagamentoRepository;

    @Transactional(readOnly = true)
    public OfflineCommandReplayEligibilityResponse evaluate(DeviceOfflineCommand cmd, boolean includeWarnings) {
        OfflineCommandReplayEligibilityResponse r = new OfflineCommandReplayEligibilityResponse();
        r.setCommandId(cmd.getId());
        r.setClientRequestId(cmd.getClientRequestId());
        r.setCommandType(cmd.getCommandType());
        r.setCurrentStatus(cmd.getStatus());
        r.setCreatedEntityType(cmd.getCreatedEntityType());
        r.setCreatedEntityId(cmd.getCreatedEntityId());

        List<String> warnings = new ArrayList<>();

        // nunca reprocessar estes estados
        if (cmd.getStatus() == DeviceOfflineCommandStatus.APPLIED) {
            return notEligible(r, DeviceOfflineReplayEligibilityReason.COMMAND_ALREADY_APPLIED, includeWarnings, warnings);
        }
        if (cmd.getStatus() == DeviceOfflineCommandStatus.DUPLICATE) {
            return notEligible(r, DeviceOfflineReplayEligibilityReason.COMMAND_DUPLICATE, includeWarnings, warnings);
        }

        // só reprocessa FAILED/CONFLICT/REJECTED
        if (!(cmd.getStatus() == DeviceOfflineCommandStatus.FAILED
                || cmd.getStatus() == DeviceOfflineCommandStatus.CONFLICT
                || cmd.getStatus() == DeviceOfflineCommandStatus.REJECTED)) {
            return notEligible(r, DeviceOfflineReplayEligibilityReason.STATUS_NOT_REPLAYABLE, includeWarnings, warnings);
        }

        // não reprocessáveis por conflito/erro
        if (DeviceOfflineConflictCode.IDEMPOTENCY_CONFLICT.name().equals(cmd.getConflictCode())) {
            return notEligible(r, DeviceOfflineReplayEligibilityReason.IDEMPOTENCY_CONFLICT_NOT_REPLAYABLE, includeWarnings, warnings);
        }
        if (DeviceOfflineConflictCode.COMMAND_TYPE_NOT_ALLOWED_OFFLINE.name().equals(cmd.getConflictCode())) {
            return notEligible(r, DeviceOfflineReplayEligibilityReason.COMMAND_TYPE_NOT_ALLOWED, includeWarnings, warnings);
        }
        if (DeviceOfflineConflictCode.PAYLOAD_TOO_LARGE.name().equals(cmd.getConflictCode())
                || DeviceOfflineConflictCode.BATCH_PAYLOAD_TOO_LARGE.name().equals(cmd.getConflictCode())) {
            return notEligible(r, DeviceOfflineReplayEligibilityReason.PAYLOAD_TOO_LARGE_NOT_REPLAYABLE, includeWarnings, warnings);
        }
        if (DeviceOfflineConflictCode.LOCAL_REF_CIRCULAR_DEPENDENCY.name().equals(cmd.getConflictCode())
                || DeviceOfflineConflictCode.LOCAL_REF_FORWARD_REFERENCE_NOT_ALLOWED.name().equals(cmd.getConflictCode())
                || DeviceOfflineConflictCode.DEPENDENCY_DEPTH_EXCEEDED.name().equals(cmd.getConflictCode())) {
            return notEligible(r, DeviceOfflineReplayEligibilityReason.LOCAL_REF_FAILED_DEPENDENCY, includeWarnings, warnings);
        }

        // low value: não vale reprocessar por default
        if (cmd.getCommandType() == DeviceOfflineCommandType.REGISTER_LOCAL_ACTIVITY) {
            r.setEligibilityStatus(DeviceOfflineReplayEligibilityStatus.NOT_ELIGIBLE);
            r.setReason(DeviceOfflineReplayEligibilityReason.LOW_VALUE_NOOP);
            r.setEligible(false);
            r.setRecommendedAction("IGNORAR");
            if (includeWarnings) r.setWarnings(warnings);
            return r;
        }

        // se já criou entidade, não deve reprocessar (evita duplicação)
        if (cmd.getCreatedEntityType() != null && cmd.getCreatedEntityId() != null) {
            return notEligible(r, DeviceOfflineReplayEligibilityReason.COMMAND_ALREADY_APPLIED, includeWarnings, warnings);
        }

        // dependência: se existe dependsOn, só é elegível se a dependência já estiver aplicada
        if (cmd.getDependsOnClientRequestId() != null && !cmd.getDependsOnClientRequestId().isBlank()) {
            DeviceOfflineCommand dep = commandRepository.findByTenantIdAndDispositivoOperacionalIdAndClientRequestId(
                    cmd.getTenant().getId(), cmd.getDispositivoOperacional().getId(), cmd.getDependsOnClientRequestId().trim()
            ).orElse(null);
            if (dep == null) {
                return notEligible(r, DeviceOfflineReplayEligibilityReason.LOCAL_REF_NOT_RESOLVABLE, includeWarnings, warnings);
            }
            if (!(dep.getStatus() == DeviceOfflineCommandStatus.APPLIED || dep.getStatus() == DeviceOfflineCommandStatus.DUPLICATE)) {
                return notEligible(r, DeviceOfflineReplayEligibilityReason.LOCAL_REF_FAILED_DEPENDENCY, includeWarnings, warnings);
            }
            r.setEligibilityStatus(DeviceOfflineReplayEligibilityStatus.ELIGIBLE);
            r.setReason(DeviceOfflineReplayEligibilityReason.ELIGIBLE_AFTER_DEPENDENCY_RESOLVED);
            r.setEligible(true);
            r.setRecommendedAction("REPLAY");
        } else {
            r.setEligibilityStatus(DeviceOfflineReplayEligibilityStatus.ELIGIBLE);
            r.setReason(DeviceOfflineReplayEligibilityReason.ELIGIBLE_RETRYABLE_FAILURE);
            r.setEligible(true);
            r.setRecommendedAction("REPLAY");
        }

        // regra extra: CONFIRM_MANUAL_PAYMENT se ordem já confirmada -> bloqueia
        if (cmd.getCommandType() == DeviceOfflineCommandType.CONFIRM_MANUAL_PAYMENT) {
            Long ordemId = tryExtractOrdemId(cmd);
            if (ordemId != null) {
                OrdemPagamento op = ordemPagamentoRepository.findByIdAndTenantId(ordemId, cmd.getTenant().getId()).orElse(null);
                if (op != null && op.getStatus() == OrdemPagamentoStatus.CONFIRMADA) {
                    return notEligible(r, DeviceOfflineReplayEligibilityReason.ORDER_ALREADY_CONFIRMED_BY_OTHER, includeWarnings, warnings);
                }
            } else {
                warnings.add("Ordem de pagamento não resolvida na elegibilidade; validação ocorrerá no replay.");
                r.setEligibilityStatus(DeviceOfflineReplayEligibilityStatus.REQUIRES_MANUAL_REVIEW);
                r.setReason(DeviceOfflineReplayEligibilityReason.LOCAL_REF_NOT_RESOLVABLE);
                r.setEligible(false);
                r.setRecommendedAction("REVISAR");
            }
        }

        if (includeWarnings) r.setWarnings(warnings);
        return r;
    }

    private OfflineCommandReplayEligibilityResponse notEligible(OfflineCommandReplayEligibilityResponse r,
                                                                DeviceOfflineReplayEligibilityReason reason,
                                                                boolean includeWarnings,
                                                                List<String> warnings) {
        r.setEligibilityStatus(DeviceOfflineReplayEligibilityStatus.NOT_ELIGIBLE);
        r.setReason(reason);
        r.setEligible(false);
        r.setRecommendedAction("NAO_REPLAY");
        if (includeWarnings) r.setWarnings(warnings);
        return r;
    }

    private Long tryExtractOrdemId(DeviceOfflineCommand cmd) {
        if (cmd == null || cmd.getPayloadJson() == null || cmd.getPayloadJson().isBlank()) return null;
        try {
            JsonNode payload = objectMapper.readTree(cmd.getPayloadJson());
            if (payload != null && payload.hasNonNull("ordemPagamentoId")) return payload.get("ordemPagamentoId").asLong();
            // se vier ordemPagamentoClientRequestId, o replay tentará resolver antes de executar
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
