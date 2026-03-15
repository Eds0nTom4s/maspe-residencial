package com.restaurante.config;

import com.restaurante.model.entity.ConfiguracaoFinanceiraSistema;
import com.restaurante.repository.ConfiguracaoFinanceiraSistemaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Configuração de inicialização do sistema financeiro
 * 
 * RESPONSABILIDADES:
 * - Carregar valores iniciais do application.properties
 * - Persistir configuração na PRIMEIRA execução
 * - Após primeira execução: BANCO é fonte de verdade
 * 
 * REGRAS:
 * - application.properties = valor bootstrap (apenas primeira execução)
 * - Banco de dados = fonte de verdade após inicialização
 * - Frontend admin pode alterar valores posteriormente
 * 
 * VALORES CONFIGURÁVEIS:
 * - limitePosPago: Limite máximo de pós-pago aberto
 * - valorMinimoOperacao: Valor mínimo para operações financeiras
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InicializacaoFinanceiraConfig {

    private final ConfiguracaoFinanceiraSistemaRepository configuracaoRepository;

    @Value("${financeiro.limite-pos-pago:500.00}")
    private String limitePosPagoPadrao;

    @Value("${financeiro.valor-minimo-operacao:10.00}")
    private String valorMinimoOperacaoPadrao;

    /**
     * Inicializa configuração financeira na primeira execução
     * Executado após contexto Spring estar totalmente carregado
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void inicializarConfiguracaoFinanceira() {
        log.info("=".repeat(80));
        log.info("🔧 INICIALIZANDO CONFIGURAÇÃO FINANCEIRA DO SISTEMA");
        log.info("=".repeat(80));

        // Verifica se já existe configuração
        var configExistente = configuracaoRepository.findAtual();

        if (configExistente.isPresent()) {
            log.info("✅ Configuração financeira JÁ EXISTE no banco de dados");
            log.info("  ┣ Limite Pós-Pago: {}", com.restaurante.util.MoneyFormatter.format(configExistente.get().getLimitePosPago()));
            log.info("  ┣ Valor Mínimo Operação: {}", com.restaurante.util.MoneyFormatter.format(configExistente.get().getValorMinimoOperacao()));
            log.info("  ┗ Pós-Pago Ativo: {}", configExistente.get().getPosPagoAtivo());
            log.info("📌 BANCO DE DADOS é a fonte de verdade. application.properties IGNORADO.");
            log.info("=".repeat(80));
            return;
        }

        // PRIMEIRA EXECUÇÃO: Criar configuração com valores do application.properties
        log.info("⚠️  PRIMEIRA EXECUÇÃO DETECTADA");
        log.info("📥 Carregando valores iniciais do application.properties:");
        log.info("  ┣ Limite Pós-Pago Padrão: {}", com.restaurante.util.MoneyFormatter.format(new BigDecimal(limitePosPagoPadrao)));
        log.info("  ┗ Valor Mínimo Operação: {}", com.restaurante.util.MoneyFormatter.format(new BigDecimal(valorMinimoOperacaoPadrao)));

        ConfiguracaoFinanceiraSistema config = ConfiguracaoFinanceiraSistema.builder()
            .posPagoAtivo(true)
            .limitePosPago(new BigDecimal(limitePosPagoPadrao))
            .valorMinimoOperacao(new BigDecimal(valorMinimoOperacaoPadrao))
            .atualizadoPorNome("Sistema")
            .atualizadoPorRole("ADMIN")
            .build();

        configuracaoRepository.save(config);

        log.info("✅ Configuração financeira CRIADA E PERSISTIDA com sucesso!");
        log.info("  ┣ Limite Pós-Pago: {}", com.restaurante.util.MoneyFormatter.format(config.getLimitePosPago()));
        log.info("  ┣ Valor Mínimo Operação: {}", com.restaurante.util.MoneyFormatter.format(config.getValorMinimoOperacao()));
        log.info("  ┗ Pós-Pago Ativo: {}", config.getPosPagoAtivo());
        log.info("📌 A partir de agora: BANCO DE DADOS é a fonte de verdade.");
        log.info("🔄 Para alterar valores, use o frontend administrativo.");
        log.info("=".repeat(80));
    }
}
