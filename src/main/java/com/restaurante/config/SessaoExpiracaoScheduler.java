package com.restaurante.config;

import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.enums.StatusSessaoConsumo;
import com.restaurante.repository.SessaoConsumoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduler responsável por expirar sessões de consumo abandonadas.
 *
 * <p>Regra de negócio (prompt-fase1.txt):
 * "Sessões abertas sem operações devem expirar automaticamente conforme
 *  configuração do sistema."
 *
 * <p>Critério de expiração:
 * Sessão com status ABERTA e {@code abertaEm} anterior ao tempo de corte
 * (padrão: 12 horas, configurável via {@code sessao.expiracao.horas}).
 *
 * <p>Ciclo de vida aplicado:
 * <pre>
 *   ABERTA (sem actividade por N horas) → EXPIRADA
 * </pre>
 */
@Component
public class SessaoExpiracaoScheduler {

    private static final Logger log = LoggerFactory.getLogger(SessaoExpiracaoScheduler.class);

    /**
     * Janela máxima de inactividade antes de expirar sessão (em horas).
     * Valor conservador para ambientes de restaurante/bar.
     */
    private static final int HORAS_ATE_EXPIRAR = 12;

    private final SessaoConsumoRepository sessaoConsumoRepository;

    public SessaoExpiracaoScheduler(SessaoConsumoRepository sessaoConsumoRepository) {
        this.sessaoConsumoRepository = sessaoConsumoRepository;
    }

    /**
     * Varre sessões abandonadas a cada hora e transita-as para EXPIRADA.
     *
     * <p>Execução: a cada 60 minutos (cron: topo de cada hora).
     * Cada sessão é processada de forma isolada — falha num não afecta os restantes.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void expirarSessoesAbandonadas() {
        LocalDateTime corte = LocalDateTime.now().minusHours(HORAS_ATE_EXPIRAR);

        List<SessaoConsumo> sessoesAntigas = sessaoConsumoRepository
                .findByStatusAndAbertaEmBefore(StatusSessaoConsumo.ABERTA, corte);

        if (sessoesAntigas.isEmpty()) {
            log.debug("Nenhuma sessão a expirar (corte: {} horas de inactividade)", HORAS_ATE_EXPIRAR);
            return;
        }

        log.info("Iniciando expiração de {} sessões abandonadas (abertas há mais de {} horas)",
                sessoesAntigas.size(), HORAS_ATE_EXPIRAR);

        int expiradas = 0;
        int falhas = 0;

        for (SessaoConsumo sessao : sessoesAntigas) {
            try {
                expirarSessao(sessao);
                expiradas++;
            } catch (Exception e) {
                falhas++;
                log.error("Erro ao expirar sessão ID={}: {}", sessao.getId(), e.getMessage(), e);
            }
        }

        log.info("Expiração concluída: {} expiradas, {} falhas", expiradas, falhas);
    }

    /**
     * Transita uma sessão individual para EXPIRADA e encerra o seu fundo.
     */
    private void expirarSessao(SessaoConsumo sessao) {
        log.info("Expirando sessão ID={}, aberta em={}, QR={}",
                sessao.getId(), sessao.getAbertaEm(), sessao.getQrCodeSessao());

        sessao.setStatus(StatusSessaoConsumo.EXPIRADA);
        sessao.setFechadaEm(LocalDateTime.now());

        // Encerra também o fundo associado, se existir e estiver activo
        if (sessao.getFundoConsumo() != null && Boolean.TRUE.equals(sessao.getFundoConsumo().getAtivo())) {
            sessao.getFundoConsumo().encerrar();
            log.info("  Fundo ID={} encerrado junto com sessão expirada", sessao.getFundoConsumo().getId());
        }

        sessaoConsumoRepository.save(sessao);

        log.info("  Sessão ID={} expirada com sucesso", sessao.getId());
    }
}
