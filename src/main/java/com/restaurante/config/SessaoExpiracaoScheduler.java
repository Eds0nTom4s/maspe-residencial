package com.restaurante.config;

import com.restaurante.model.enums.ExpiracaoSessaoResultado;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.service.SessaoConsumoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduler responsável por expirar sessões de consumo inactivas.
 *
 * <p><strong>Sprint 1 — Blindagem da Expiração</strong>:
 * O scheduler passou a:
 * <ol>
 *   <li>Usar {@code ultimaAtividadeEm} em vez de {@code abertaEm} como critério temporal.</li>
 *   <li>Delegar cada decisão de expiração ao {@link SessaoConsumoService#expirarComSeguranca},
 *       que executa em transacção própria com validações completas.</li>
 *   <li>Nunca alterar entidades directamente.</li>
 *   <li>Processar cada sessão de forma isolada: falha numa não impede as restantes.</li>
 *   <li>Registar métricas de resultado (expiradas, bloqueadas, erros).</li>
 * </ol>
 *
 * <p>Configuração:
 * <pre>
 *   sessao.expiracao.horas=12
 *   # Agora interpretado como horas desde ultimaAtividadeEm (inatividade real),
 *   # não apenas desde abertaEm.
 * </pre>
 */
@Component
public class SessaoExpiracaoScheduler {

    private static final Logger log = LoggerFactory.getLogger(SessaoExpiracaoScheduler.class);

    /**
     * Horas de inactividade (desde ultimaAtividadeEm) para considerar sessão expirada.
     * Anteriormente media a idade desde abertaEm; agora mede inactividade real.
     */
    @Value("${sessao.expiracao.horas:12}")
    private int horasDeInatividade;

    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final SessaoConsumoService sessaoConsumoService;

    public SessaoExpiracaoScheduler(SessaoConsumoRepository sessaoConsumoRepository,
                                     SessaoConsumoService sessaoConsumoService) {
        this.sessaoConsumoRepository = sessaoConsumoRepository;
        this.sessaoConsumoService = sessaoConsumoService;
    }

    /**
     * Varre sessões inactivas a cada hora e delega a decisão ao service.
     *
     * <p>A validação efectiva (saldo, pedidos, pagamentos) é feita em
     * {@link SessaoConsumoService#expirarComSeguranca}, garantindo atomicidade e auditoria.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void expirarSessoesAbandonadas() {
        LocalDateTime limiteInatividade = LocalDateTime.now().minusHours(horasDeInatividade);

        List<Long> candidatasIds = sessaoConsumoRepository
                .findCandidatasParaExpiracao(limiteInatividade)
                .stream()
                .map(s -> s.getId())
                .toList();

        if (candidatasIds.isEmpty()) {
            log.debug("Nenhuma sessão candidata à expiração (inactividade > {} h)", horasDeInatividade);
            return;
        }

        log.info("Iniciando ciclo de expiração: {} candidata(s) com inatividade > {} h",
                candidatasIds.size(), horasDeInatividade);

        // Contadores por resultado
        Map<ExpiracaoSessaoResultado, Integer> contadores = new EnumMap<>(ExpiracaoSessaoResultado.class);
        for (ExpiracaoSessaoResultado r : ExpiracaoSessaoResultado.values()) {
            contadores.put(r, 0);
        }

        for (Long sessaoId : candidatasIds) {
            ExpiracaoSessaoResultado resultado;
            try {
                resultado = sessaoConsumoService.expirarComSeguranca(sessaoId, limiteInatividade);
            } catch (Exception e) {
                resultado = ExpiracaoSessaoResultado.ERRO;
                log.error("Erro ao processar sessão ID={} no scheduler: {}", sessaoId, e.getMessage(), e);
            }
            contadores.merge(resultado, 1, Integer::sum);
        }

        // Relatório final do ciclo
        log.info("Ciclo de expiração concluído: total={}, expiradas={}, ignoradas_status={}, " +
                 "bloqueadas_atividade={}, bloqueadas_saldo={}, bloqueadas_pedido={}, " +
                 "bloqueadas_pagamento={}, erros={}",
                candidatasIds.size(),
                contadores.get(ExpiracaoSessaoResultado.EXPIRADA),
                contadores.get(ExpiracaoSessaoResultado.IGNORADA_STATUS_NAO_ABERTA),
                contadores.get(ExpiracaoSessaoResultado.BLOQUEADA_ATIVIDADE_RECENTE),
                contadores.get(ExpiracaoSessaoResultado.BLOQUEADA_SALDO_POSITIVO),
                contadores.get(ExpiracaoSessaoResultado.BLOQUEADA_PEDIDO_PENDENTE),
                contadores.get(ExpiracaoSessaoResultado.BLOQUEADA_PAGAMENTO_PENDENTE),
                contadores.get(ExpiracaoSessaoResultado.ERRO));
    }
}
