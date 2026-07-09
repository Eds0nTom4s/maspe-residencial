package com.restaurante.consumo.participante.job;

import com.restaurante.config.SessaoOwnerActionTokenProperties;
import com.restaurante.consumo.participante.repository.SessaoOwnerActionTokenRepository;
import com.restaurante.model.enums.OperationalEntityType;
import com.restaurante.model.enums.OperationalEventType;
import com.restaurante.model.enums.OperationalOrigem;
import com.restaurante.model.enums.SessaoOwnerActionTokenStatus;
import com.restaurante.service.operacional.OperationalEventLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Prompt 41.5 — Job de limpeza física de tokens Owner antigos e finalizados.
 * <p>
 * Critério: tokens com status EXPIRED / CONSUMED / REVOKED criados há mais
 * de {@code consuma.sessao.owner-action-token.cleanup.retention-days} dias.
 * <p>
 * A raw token nunca foi armazenada, portanto DELETE físico é seguro;
 * a auditoria de eventos já preserva a rastreabilidade necessária.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessaoOwnerActionTokenCleanupJob {

    public static final String JOB_NAME = "SessaoOwnerActionTokenCleanupJob";

    private static final List<SessaoOwnerActionTokenStatus> FINALIZADO = List.of(
            SessaoOwnerActionTokenStatus.EXPIRED,
            SessaoOwnerActionTokenStatus.CONSUMED,
            SessaoOwnerActionTokenStatus.REVOKED
    );

    private final SessaoOwnerActionTokenRepository tokenRepository;
    private final SessaoOwnerActionTokenProperties tokenProps;
    private final OperationalEventLogService eventLogService;

    @Scheduled(cron = "${consuma.sessao.owner-action-token.cleanup.cron:0 20 3 * * *}")
    @Transactional
    public void run() {
        if (!tokenProps.getCleanup().isEnabled()) {
            log.debug("[OwnerTokenCleanup] Job desactivado, ignorando execução.");
            return;
        }

        long startMs = System.currentTimeMillis();
        int retentionDays = tokenProps.getCleanup().getRetentionDays();
        int batchSize     = tokenProps.getCleanup().getBatchSize();
        Instant cutoff    = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        log.info("[OwnerTokenCleanup] Iniciando — cutoff={}, batchSize={}, retentionDays={}",
                cutoff, batchSize, retentionDays);

        int totalDeleted = 0;
        int scanned = 0;

        try {
            // Busca IDs paginados (cross-tenant, pois job é global)
            List<Long> ids = tokenRepository.findFinalizedIdsCrossTenantsBeforeForCleanup(
                    FINALIZADO, cutoff, PageRequest.of(0, batchSize));
            scanned = ids.size();

            if (!ids.isEmpty()) {
                totalDeleted = tokenRepository.deleteByIds(ids);
                log.info("[OwnerTokenCleanup] {} token(s) removido(s).", totalDeleted);
            } else {
                log.debug("[OwnerTokenCleanup] Nenhum token elegível para limpeza.");
            }

            long durationMs = System.currentTimeMillis() - startMs;
            auditCleanup(scanned, totalDeleted, cutoff, durationMs, null);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startMs;
            log.error("[OwnerTokenCleanup] Erro durante limpeza: {}", e.getMessage(), e);
            auditCleanup(scanned, totalDeleted, cutoff, durationMs, e.getMessage());
        }
    }

    private void auditCleanup(int scanned, int deleted, Instant cutoff, long durationMs, String error) {
        try {
            // Sem tenant específico (job cross-tenant), sem entidade concreta
            eventLogService.logPublicEvent(
                    null, null, null, null, null,
                    OperationalEventType.SESSAO_OWNER_ACTION_TOKEN_CLEANUP_RUN,
                    OperationalEntityType.SESSAO_CONSUMO_PARTICIPANTE,
                    0L,
                    OperationalOrigem.SYSTEM,
                    "Cleanup de tokens Owner finalizado",
                    Map.of(
                            "jobName", JOB_NAME,
                            "scannedCount", scanned,
                            "deletedCount", deleted,
                            "cutoff", cutoff.toString(),
                            "statuses", "EXPIRED,CONSUMED,REVOKED",
                            "durationMs", durationMs,
                            "error", error != null ? error.substring(0, Math.min(error.length(), 500)) : "none"
                    ),
                    null, null
            );
        } catch (Exception ex) {
            log.warn("[OwnerTokenCleanup] Falha ao auditar cleanup: {}", ex.getMessage());
        }
    }
}
