package com.restaurante.consumo.participante.service;

import com.restaurante.config.SessaoParticipanteLifecycleProperties;
import com.restaurante.consumo.participante.entity.SessaoConsumoParticipante;
import com.restaurante.consumo.participante.repository.SessaoConsumoParticipanteRepository;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.SessaoParticipanteStatus;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SessaoParticipanteExpirationService {

    private final SessaoConsumoParticipanteRepository participanteRepository;
    private final OperationalEventLogService operationalEventLogService;
    private final SessaoParticipanteLifecycleProperties props;

    @Transactional
    public ExpirationRunResult expireOnce(String cleanupBatchId) {
        if (!props.isExpirationJobEnabled()) return new ExpirationRunResult(cleanupBatchId, 0, 0);

        Instant now = Instant.now();
        List<SessaoConsumoParticipante> list = participanteRepository.findExpiredCandidatesForUpdate(now, PageRequest.of(0, props.getExpirationBatchSize()));
        int expired = 0;
        for (SessaoConsumoParticipante p : list) {
            if (p.getStatus() == SessaoParticipanteStatus.ACTIVE) continue;
            if (p.getStatus() == SessaoParticipanteStatus.EXPIRED || p.getStatus() == SessaoParticipanteStatus.CANCELLED) continue;
            if (p.getExpiresAt() == null || !p.getExpiresAt().isBefore(now)) continue;

            SessaoParticipanteStatus old = p.getStatus();
            p.setStatus(SessaoParticipanteStatus.EXPIRED);
            p.setExpiredAt(now);
            p.setExpirationReason(expirationReason(old));
            p.setCleanupBatchId(cleanupBatchId);
            participanteRepository.save(p);
            expired++;

            operationalEventLogService.logPublicEvent(
                    p.getTenant(),
                    p.getSessaoConsumo() != null ? p.getSessaoConsumo().getInstituicao() : null,
                    p.getSessaoConsumo() != null ? p.getSessaoConsumo().getUnidadeAtendimento() : null,
                    p.getSessaoConsumo() != null ? p.getSessaoConsumo().getMesa() : null,
                    null,
                    OperationalEventType.SESSAO_PARTICIPANTE_EXPIRED_BY_JOB,
                    OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                    p.getId(),
                    OperationalOrigem.SYSTEM,
                    "Participante expirado por job",
                    Map.of(
                            "tenantId", p.getTenant() != null ? p.getTenant().getId() : null,
                            "sessaoConsumoId", p.getSessaoConsumo() != null ? p.getSessaoConsumo().getId() : null,
                            "participanteId", p.getId(),
                            "oldStatus", old.name(),
                            "newStatus", p.getStatus().name(),
                            "expiresAt", p.getExpiresAt(),
                            "expiredAt", p.getExpiredAt(),
                            "expirationReason", p.getExpirationReason(),
                            "cleanupBatchId", cleanupBatchId
                    ),
                    null,
                    null
            );
        }

        return new ExpirationRunResult(cleanupBatchId, list.size(), expired);
    }

    private String expirationReason(SessaoParticipanteStatus old) {
        if (old == SessaoParticipanteStatus.PENDING_APPROVAL) return "PENDING_APPROVAL_TTL_EXPIRED";
        if (old == SessaoParticipanteStatus.PENDING_OTP) return "PENDING_OTP_TTL_EXPIRED";
        return "INVITE_TTL_EXPIRED";
    }

    public record ExpirationRunResult(String cleanupBatchId, int candidates, int expired) {}
}
