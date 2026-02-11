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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Service para configuração financeira global
 * 
 * INTERRUPTOR GLOBAL DE POS-PAGO:
 * - ADMIN pode ativar/desativar pós-pago em tempo real
 * - Valida limites de risco por cliente/unidade
 * - Registra auditoria de todas alterações
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfiguracaoFinanceiraService {

    private final ConfiguracaoFinanceiraSistemaRepository configuracaoRepository;
    private final PedidoRepository pedidoRepository;
    private final EventLogService eventLogService;

    // Limite padrão de pós-pago por unidade (pode ser configurável)
    private static final BigDecimal LIMITE_POS_PAGO_PADRAO = new BigDecimal("500.00");

    /**
     * Busca configuração atual (cria se não existir)
     */
    @Transactional
    public ConfiguracaoFinanceiraSistema buscarOuCriarConfiguracao() {
        return configuracaoRepository.findAtual()
            .orElseGet(() -> {
                log.info("Criando configuração financeira inicial");
                ConfiguracaoFinanceiraSistema config = ConfiguracaoFinanceiraSistema.builder()
                    .posPagoAtivo(true)
                    .atualizadoPorNome("Sistema")
                    .atualizadoPorRole("ADMIN")
                    .build();
                return configuracaoRepository.save(config);
            });
    }

    /**
     * Verifica se pós-pago está ativo
     */
    public boolean isPosPagoAtivo() {
        return buscarOuCriarConfiguracao().getPosPagoAtivo();
    }

    /**
     * Valida se pedido POS-PAGO pode ser criado
     * 
     * REGRAS:
     * - posPagoAtivo deve ser true
     * - Usuário deve ter role GERENTE ou ADMIN
     * - Total aberto não pode exceder limite
     */
    @Transactional(readOnly = true)
    public void validarCriacaoPosPago(Long unidadeConsumoId, BigDecimal valorNovo, Set<String> roles) {
        log.info("Validando criação de pós-pago para unidade {} - valor {}", unidadeConsumoId, valorNovo);

        // 1. Verifica se pós-pago está ativo globalmente
        if (!isPosPagoAtivo()) {
            throw new PosPagoDesabilitadoException();
        }

        // 2. Verifica permissão (já validado em PedidoFinanceiroService, mas reforça)
        boolean temPermissao = roles.stream()
            .anyMatch(role -> role.equals("GERENTE") || role.equals("ADMIN"));
        if (!temPermissao) {
            throw new BusinessException("Apenas GERENTE ou ADMIN podem autorizar pós-pago");
        }

        // 3. Verifica limite de risco
        BigDecimal totalAberto = calcularTotalPosPagoAberto(unidadeConsumoId);
        BigDecimal novoTotal = totalAberto.add(valorNovo);
        
        if (novoTotal.compareTo(LIMITE_POS_PAGO_PADRAO) > 0) {
            throw new LimitePosPagoExcedidoException(novoTotal, LIMITE_POS_PAGO_PADRAO);
        }

        log.info("Validação de pós-pago OK. Total aberto após pedido: R$ {}", novoTotal);
    }

    /**
     * Calcula total de pós-pago aberto (NAO_PAGO) para unidade
     */
    private BigDecimal calcularTotalPosPagoAberto(Long unidadeConsumoId) {
        List<Pedido> pedidosAbertos = pedidoRepository.findByUnidadeConsumoIdAndTipoPagamentoAndStatusFinanceiro(
            unidadeConsumoId, 
            TipoPagamentoPedido.POS_PAGO, 
            StatusFinanceiroPedido.NAO_PAGO
        );

        return pedidosAbertos.stream()
            .map(Pedido::getTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Ativa pós-pago globalmente
     */
    @Transactional
    public void ativarPosPago(String userName, String userRole) {
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
        configuracaoRepository.save(config);

        // TODO: Gerar EventLog ALTERACAO_POLITICA_POS_PAGO
        log.info("Pós-pago ATIVADO globalmente");
    }

    /**
     * Desativa pós-pago globalmente
     */
    @Transactional
    public void desativarPosPago(String userName, String userRole) {
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
        configuracaoRepository.save(config);

        // TODO: Gerar EventLog ALTERACAO_POLITICA_POS_PAGO
        log.info("Pós-pago DESATIVADO globalmente");
    }
}
