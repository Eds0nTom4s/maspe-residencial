package com.restaurante.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.LimitePosPagoExcedidoException;
import com.restaurante.exception.PosPagoDesabilitadoException;
import com.restaurante.model.entity.ConfiguracaoFinanceiraSistema;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.TipoPagamentoPedido;
import com.restaurante.repository.ConfiguracaoFinanceiraSistemaRepository;
import com.restaurante.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Serviço de configuração financeira global.
 *
 * <p>Princípio de inicialização:
 * <ol>
 *   <li>Na <b>primeira execução</b>, os valores de {@code application.properties}
 *       ({@code financeiro.limite-pos-pago} e {@code financeiro.valor-minimo-operacao})
 *       são usados como semente para criar o registro inicial no banco.</li>
 *   <li>Nas execuções seguintes, o <b>banco é a fonte de verdade</b>.</li>
 *   <li>O frontend administrativo atualiza os valores via API (sem deploy).</li>
 *   <li>Toda alteração gera registro imutável em {@code ConfiguracaoFinanceiraEventLog}.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ConfiguracaoFinanceiraService {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracaoFinanceiraService.class);

    private final ConfiguracaoFinanceiraSistemaRepository configuracaoRepository;
    private final PedidoRepository pedidoRepository;
    private final EventLogService eventLogService;
    private final AuditoriaFinanceiraService auditoriaFinanceiraService;

    // Valores-semente lidos de application.properties
    @Value("${financeiro.limite-pos-pago:500.00}")
    private BigDecimal limitePosPageSemente;

    @Value("${financeiro.valor-minimo-operacao:10.00}")
    private BigDecimal valorMinimoSemente;

    /**
     * Busca configuração atual.
     * Se ainda não existe, cria usando os valores-semente de application.properties.
     * Após criação, o banco é a fonte de verdade.
     */
    @Transactional
    public ConfiguracaoFinanceiraSistema buscarOuCriarConfiguracao() {
        return configuracaoRepository.findAtual()
            .orElseGet(() -> {
                log.info("Criando configuração financeira inicial com valores de bootstrap: " +
                         "limitePosPago={}, valorMinimo={}",
                         com.restaurante.util.MoneyFormatter.format(limitePosPageSemente), com.restaurante.util.MoneyFormatter.format(valorMinimoSemente));
                ConfiguracaoFinanceiraSistema config = new ConfiguracaoFinanceiraSistema();
                config.setPosPagoAtivo(true);
                config.setLimitePosPago(limitePosPageSemente);
                config.setValorMinimoOperacao(valorMinimoSemente);
                config.setAtualizadoPorNome("Sistema");
                config.setAtualizadoPorRole("ADMIN");
                config.setMotivoUltimaAlteracao("Inicialização automática (bootstrap)");
                return configuracaoRepository.save(config);
            });
    }

    /** Verifica se pós-pago está ativo. */
    public boolean isPosPagoAtivo() {
        return buscarOuCriarConfiguracao().getPosPagoAtivo();
    }

    /**
     * Valida se pedido POS-PAGO pode ser criado.
     *
     * <p>Regras:
     * <ul>
     *   <li>posPagoAtivo deve ser true</li>
     *   <li>Usuário deve ter role GERENTE ou ADMIN</li>
     *   <li>Total aberto não pode exceder o limite configurado</li>
     * </ul>
     *
     * IMPORTANTE: REQUIRES_NEW para não contaminar a TX externa quando lança exceção.
     * validarEConfirmarSePermitido() captura LimitePosPagoExcedidoException — se esta
     * TX usasse REQUIRED (padrão), a exceção marcaria a TX pai como rollback-only
     * causando UnexpectedRollbackException no commit.
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void validarCriacaoPosPago(Long sessaoConsumoId, BigDecimal valorNovo, Set<String> roles) {
        log.info("Validando criação de pós-pago para sessão {} - valor {}", sessaoConsumoId, valorNovo);

        if (!isPosPagoAtivo()) {
            throw new PosPagoDesabilitadoException();
        }

        boolean temPermissao = roles.stream()
            .anyMatch(role -> role.equals("GERENTE") || role.equals("ADMIN") || role.equals("CLIENTE"));
        if (!temPermissao) {
            throw new BusinessException("Apenas CLIENTEs identificados, GERENTEs ou ADMINs podem autorizar pós-pago");
        }

        ConfiguracaoFinanceiraSistema config = buscarOuCriarConfiguracao();
        BigDecimal limitePosPago = config.getLimitePosPago();
        BigDecimal totalAberto = calcularTotalPosPagoAberto(sessaoConsumoId);
        BigDecimal novoTotal = totalAberto.add(valorNovo);

        if (novoTotal.compareTo(limitePosPago) > 0) {
            throw new LimitePosPagoExcedidoException(novoTotal, limitePosPago);
        }

        log.info("Validação de pós-pago OK. Total aberto após pedido: {} (limite: {})",
                com.restaurante.util.MoneyFormatter.format(novoTotal), com.restaurante.util.MoneyFormatter.format(limitePosPago));
    }

    /** Calcula total de pós-pago aberto (NAO_PAGO) para sessão. */
    private BigDecimal calcularTotalPosPagoAberto(Long sessaoConsumoId) {
        List<Pedido> pedidosAbertos = pedidoRepository
            .findBySessaoConsumoIdAndTipoPagamentoAndStatusFinanceiro(
                sessaoConsumoId,
                TipoPagamentoPedido.POS_PAGO,
                StatusFinanceiroPedido.NAO_PAGO);
        return pedidosAbertos.stream()
            .map(Pedido::getTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Ativa pós-pago globalmente.
     * Registra auditoria em banco.
     */
    @Transactional
    public void ativarPosPago(String userName, String userRole) {
        ativarPosPago(userName, userRole, null);
    }

    @Transactional
    public void ativarPosPago(String userName, String userRole, String motivo) {
        log.info("Ativando pós-pago globalmente por {} ({})", userName, userRole);

        ConfiguracaoFinanceiraSistema config = buscarOuCriarConfiguracao();
        boolean valorAnterior = config.getPosPagoAtivo();

        if (valorAnterior) {
            log.info("Pós-pago já estava ativo. Nenhuma alteração necessária.");
            return;
        }

        config.setPosPagoAtivo(true);
        config.setAtualizadoPorNome(userName);
        config.setAtualizadoPorRole(userRole);
        config.setMotivoUltimaAlteracao(motivo);
        configuracaoRepository.save(config);

        // ✅ AUDITORIA em banco
        auditoriaFinanceiraService.registrarAlteracaoPosPagoAtivo(
                config.getId(), valorAnterior, true, userName, userRole, motivo);
        log.info("Pós-pago ATIVADO globalmente");
    }

    /**
     * Desativa pós-pago globalmente.
     * Registra auditoria em banco.
     */
    @Transactional
    public void desativarPosPago(String userName, String userRole) {
        desativarPosPago(userName, userRole, null);
    }

    @Transactional
    public void desativarPosPago(String userName, String userRole, String motivo) {
        log.info("Desativando pós-pago globalmente por {} ({})", userName, userRole);

        ConfiguracaoFinanceiraSistema config = buscarOuCriarConfiguracao();
        boolean valorAnterior = config.getPosPagoAtivo();

        if (!valorAnterior) {
            log.info("Pós-pago já estava desativado. Nenhuma alteração necessária.");
            return;
        }

        config.setPosPagoAtivo(false);
        config.setAtualizadoPorNome(userName);
        config.setAtualizadoPorRole(userRole);
        config.setMotivoUltimaAlteracao(motivo);
        configuracaoRepository.save(config);

        // ✅ AUDITORIA em banco
        auditoriaFinanceiraService.registrarAlteracaoPosPagoAtivo(
                config.getId(), valorAnterior, false, userName, userRole, motivo);
        log.info("Pós-pago DESATIVADO globalmente");
    }

    /**
     * Altera limite de pós-pago.
     * Registra auditoria em banco.
     */
    @Transactional
    public void alterarLimitePosPago(BigDecimal novoLimite, String userName, String userRole) {
        alterarLimitePosPago(novoLimite, userName, userRole, null);
    }

    @Transactional
    public void alterarLimitePosPago(BigDecimal novoLimite, String userName, String userRole, String motivo) {
        log.info("Alterando limite de pós-pago para {} por {} ({})", com.restaurante.util.MoneyFormatter.format(novoLimite), userName, userRole);

        if (novoLimite == null || novoLimite.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Limite de pós-pago deve ser maior que zero");
        }

        ConfiguracaoFinanceiraSistema config = buscarOuCriarConfiguracao();
        BigDecimal limiteAnterior = config.getLimitePosPago();

        if (limiteAnterior.compareTo(novoLimite) == 0) {
            log.info("Limite já é {}. Nenhuma alteração necessária.", com.restaurante.util.MoneyFormatter.format(novoLimite));
            return;
        }

        config.setLimitePosPago(novoLimite);
        config.setAtualizadoPorNome(userName);
        config.setAtualizadoPorRole(userRole);
        config.setMotivoUltimaAlteracao(motivo);
        configuracaoRepository.save(config);

        // ✅ AUDITORIA em banco
        auditoriaFinanceiraService.registrarAlteracaoLimitePosPago(
                config.getId(), limiteAnterior, novoLimite, userName, userRole, motivo);
        log.info("Limite de pós-pago alterado de {} para {}", com.restaurante.util.MoneyFormatter.format(limiteAnterior), com.restaurante.util.MoneyFormatter.format(novoLimite));
    }

    /**
     * Altera valor mínimo de operação.
     * Registra auditoria em banco.
     */
    @Transactional
    public void alterarValorMinimo(BigDecimal novoValor, String userName, String userRole, String motivo) {
        log.info("Alterando valor mínimo de operação para {} por {} ({})", com.restaurante.util.MoneyFormatter.format(novoValor), userName, userRole);

        if (novoValor == null || novoValor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Valor mínimo de operação deve ser maior que zero");
        }

        ConfiguracaoFinanceiraSistema config = buscarOuCriarConfiguracao();
        BigDecimal valorAnterior = config.getValorMinimoOperacao();

        if (valorAnterior.compareTo(novoValor) == 0) {
            log.info("Valor mínimo já é {}. Nenhuma alteração necessária.", com.restaurante.util.MoneyFormatter.format(novoValor));
            return;
        }

        config.setValorMinimoOperacao(novoValor);
        config.setAtualizadoPorNome(userName);
        config.setAtualizadoPorRole(userRole);
        config.setMotivoUltimaAlteracao(motivo);
        configuracaoRepository.save(config);

        // ✅ AUDITORIA em banco
        auditoriaFinanceiraService.registrarAlteracaoValorMinimo(
                config.getId(), valorAnterior, novoValor, userName, userRole, motivo);
        log.info("Valor mínimo alterado de {} para {}", com.restaurante.util.MoneyFormatter.format(valorAnterior), com.restaurante.util.MoneyFormatter.format(novoValor));
    }

    /** Altera estado do pós-pago (método genérico). */
    @Transactional
    public void alterarPosPagoAtivo(boolean ativo, String userName, String userRole) {
        if (ativo) {
            ativarPosPago(userName, userRole);
        } else {
            desativarPosPago(userName, userRole);
        }
    }
}