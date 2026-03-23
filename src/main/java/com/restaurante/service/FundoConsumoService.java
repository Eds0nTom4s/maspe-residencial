package com.restaurante.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.exception.SaldoInsuficienteException;
import com.restaurante.model.entity.Cliente;
import com.restaurante.model.entity.FundoConsumo;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.SessaoConsumo;
import com.restaurante.model.entity.TransacaoFundo;
import com.restaurante.model.enums.TipoTransacaoFundo;
import com.restaurante.repository.ClienteRepository;
import com.restaurante.repository.FundoConsumoRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.SessaoConsumoRepository;
import com.restaurante.repository.TransacaoFundoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service para gerenciar Fundo de Consumo (pré-pago).
 *
 * MODELO ARQUITECTURAL:
 * - Cada SessaoConsumo possui exactamente UM FundoConsumo (criado automaticamente).
 * - O acesso externo ao fundo é feito via qrCodeSessao da sessão (o "token" público).
 * - Toda operação financeira é auditada em TransacaoFundo.
 *
 * GARANTIAS:
 * - Concorrência protegida com @Version
 * - Isolation SERIALIZABLE para operações financeiras
 * - Idempotência: mesma operação não executa duas vezes
 */
@Service
public class FundoConsumoService {

    private static final Logger log = LoggerFactory.getLogger(FundoConsumoService.class);

    private final FundoConsumoRepository fundoConsumoRepository;
    private final TransacaoFundoRepository transacaoFundoRepository;
    private final PedidoRepository pedidoRepository;
    private final SessaoConsumoRepository sessaoConsumoRepository;
    private final ClienteRepository clienteRepository;
    private final ConfiguracaoFinanceiraService configuracaoFinanceiraService;

    public FundoConsumoService(FundoConsumoRepository fundoConsumoRepository,
                              TransacaoFundoRepository transacaoFundoRepository,
                              PedidoRepository pedidoRepository,
                              SessaoConsumoRepository sessaoConsumoRepository,
                              ClienteRepository clienteRepository,
                              ConfiguracaoFinanceiraService configuracaoFinanceiraService) {
        this.fundoConsumoRepository = fundoConsumoRepository;
        this.transacaoFundoRepository = transacaoFundoRepository;
        this.pedidoRepository = pedidoRepository;
        this.sessaoConsumoRepository = sessaoConsumoRepository;
        this.clienteRepository = clienteRepository;
        this.configuracaoFinanceiraService = configuracaoFinanceiraService;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LEITURA — Administrativo
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Lista todos os fundos de consumo (administrativo).
     */
    @Transactional(readOnly = true)
    public Page<FundoConsumo> listarTodos(Pageable pageable) {
        return fundoConsumoRepository.findAllWithSessao(pageable);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CRIAÇÃO — invocado pelo SessaoConsumoService ao abrir sessão
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Cria o FundoConsumo para uma sessão recém-criada.
     * Chamado automaticamente pelo SessaoConsumoService — não expõe endpoint directo.
     *
     * @param sessao sessão de consumo já persistida
     */
    @Transactional
    public FundoConsumo criarFundoParaSessao(com.restaurante.model.entity.SessaoConsumo sessao) {
        log.info("Criando FundoConsumo para sessão ID={}", sessao.getId());
        
        // Verifica se já existe para evitar duplicidade
        return fundoConsumoRepository.findBySessaoConsumoId(sessao.getId())
                .orElseGet(() -> {
                    FundoConsumo fundo = FundoConsumo.builder()
                            .sessaoConsumo(sessao)
                            .saldoAtual(BigDecimal.ZERO)
                            .ativo(true)
                            .build();
                    return fundoConsumoRepository.save(fundo);
                });
    }

    /**
     * Criação manual (Admin) de um fundo de consumo.
     * Pode ser associado a uma sessão existente ou a um cliente.
     */
    @Transactional
    public FundoConsumo criarFundoManual(Long sessaoId, Long clienteId, BigDecimal saldoInicial, String obs) {
        log.info("Iniciando criação manual de fundo: sessaoId={}, clienteId={}, saldo={}", sessaoId, clienteId, saldoInicial);

        FundoConsumo.FundoConsumoBuilder builder = FundoConsumo.builder()
                .saldoAtual(saldoInicial)
                .ativo(true);

        if (sessaoId != null) {
            com.restaurante.model.entity.SessaoConsumo sessao = sessaoConsumoRepository.findById(sessaoId)
                    .orElseThrow(() -> new BusinessException("Sessão não encontrada: " + sessaoId));
            
            // Verifica se a sessão já tem fundo
            fundoConsumoRepository.findBySessaoConsumoId(sessaoId).ifPresent(f -> {
                throw new BusinessException("Esta sessão já possui o fundo ID: " + f.getId());
            });
            builder.sessaoConsumo(sessao);
        } else if (clienteId != null) {
            // Se informou clienteId, buscamos a sessão aberta dele
            com.restaurante.model.entity.SessaoConsumo sessao = sessaoConsumoRepository.findSessaoAbertaByCliente(clienteId)
                    .orElseThrow(() -> new BusinessException("O cliente ID=" + clienteId + " não possui uma sessão aberta para vincular o fundo."));
            
            // Verifica se a sessão já tem fundo
            fundoConsumoRepository.findBySessaoConsumoId(sessao.getId()).ifPresent(f -> {
                throw new BusinessException("A sessão ativa do cliente (" + sessao.getId() + ") já possui o fundo ID: " + f.getId());
            });
            builder.sessaoConsumo(sessao);
        } else {
            throw new BusinessException("É necessário informar uma Sessão ou um Cliente para criar o fundo.");
        }

        FundoConsumo fundo = builder.build();
        FundoConsumo salvo = fundoConsumoRepository.save(fundo);

        // Se houver saldo inicial, registra como transação de crédito
        if (saldoInicial.compareTo(BigDecimal.ZERO) > 0) {
            TransacaoFundo t = TransacaoFundo.builder()
                    .fundoConsumo(salvo)
                    .tipo(com.restaurante.model.enums.TipoTransacaoFundo.CREDITO)
                    .valor(saldoInicial)
                    .saldoAnterior(BigDecimal.ZERO)
                    .saldoNovo(saldoInicial)
                    .observacoes(obs != null ? obs : "Saldo inicial administrativo")
                    .build();
            transacaoFundoRepository.save(t);
        }

        return salvo;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LOOKUP — por sessaoId ou por qrCodeSessao (token público)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Busca fundo ativo pelo ID da sessão.
     */
    @Transactional(readOnly = true)
    public FundoConsumo buscarPorSessaoId(Long sessaoId) {
        return fundoConsumoRepository.findBySessaoConsumoIdAndAtivoTrue(sessaoId)
            .orElseThrow(() -> new ResourceNotFoundException("Fundo de consumo não encontrado para sessão: " + sessaoId));
    }

    /**
     * Busca fundo ativo pelo QR Code da sessão (token público de acesso).
     * Usado pelo controller e operações de bar/balcão.
     */
    @Transactional(readOnly = true)
    public FundoConsumo buscarPorToken(String qrCodeSessao) {
        return fundoConsumoRepository.findByQrCodeSessaoAndAtivoTrue(qrCodeSessao)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Fundo de consumo não encontrado para o QR Code: " + qrCodeSessao));
    }

    /**
     * Consulta saldo do fundo pelo QR Code da sessão.
     *
     * II-2: Saldo calculado SEMPRE da soma real do ledger de transacções,
     * nunca do campo cache. Garante consistência mesmo após falhas parciais.
     */
    @Transactional(readOnly = true)
    public BigDecimal consultarSaldoPorToken(String qrCodeSessao) {
        FundoConsumo fundo = buscarPorToken(qrCodeSessao);
        return transacaoFundoRepository.calcularSaldoAgregado(fundo.getId());
    }

    /**
     * Consulta saldo do fundo pelo ID da sessão.
     *
     * II-2: Saldo calculado da soma real do ledger (fonte de verdade).
     */
    @Transactional(readOnly = true)
    public BigDecimal consultarSaldo(Long sessaoId) {
        FundoConsumo fundo = buscarPorSessaoId(sessaoId);
        return transacaoFundoRepository.calcularSaldoAgregado(fundo.getId());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DÉBITO (DEBITO)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Recarrega o saldo para o cliente logado, buscando a sua sessão ativa pelo número de telefone (principal).
     * Esta simplificação permite que o frontend não precise de passar o token na URL 
     * da recarga na área logada.
     */
    @Retryable(
        retryFor = { ObjectOptimisticLockingFailureException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 500)
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransacaoFundo recarregarCliente(String telefoneCliente, BigDecimal valor, String observacoes) {
        log.info("Cliente telefone={} solicitou recarga de {}", telefoneCliente, com.restaurante.util.MoneyFormatter.format(valor));
        validarValorPositivo(valor);
        validarValorMinimo(valor);
        
        FundoConsumo fundo = fundoConsumoRepository.findBySessaoConsumoClienteTelefoneAndSessaoConsumoStatusAndAtivoTrue(
                telefoneCliente, com.restaurante.model.enums.StatusSessaoConsumo.ABERTA)
            .orElseThrow(() -> new BusinessException("Nenhuma sessão de consumo ativa encontrada para o cliente: " + telefoneCliente));

        return executarCredito(fundo, valor, observacoes != null ? observacoes : "Recarga efetuada pelo cliente");
    }

    /**
     * Recarrega saldo pelo QR Code da sessão (operação principal de balcão).
     *
     * @param qrCodeSessao QR Code único da sessão
     * @param valor        valor a creditar (> mínimo configurado)
     * @param observacoes  motivo da recarga
     */
    @Retryable(
        retryFor = { ObjectOptimisticLockingFailureException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 500)
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransacaoFundo recarregarPorToken(String qrCodeSessao, BigDecimal valor, String observacoes) {
        log.info("Recarregando {} no fundo da sessão QR={}", com.restaurante.util.MoneyFormatter.format(valor), qrCodeSessao);
        validarValorPositivo(valor);
        validarValorMinimo(valor);
        FundoConsumo fundo = buscarPorToken(qrCodeSessao);
        return executarCredito(fundo, valor, observacoes != null ? observacoes : "Recarga via QR Code");
    }

    /**
     * Recarrega saldo pelo ID da sessão.
     */
    @Retryable(
        retryFor = { ObjectOptimisticLockingFailureException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 500)
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransacaoFundo recarregar(Long sessaoId, BigDecimal valor, String observacoes) {
        log.info("Recarregando {} no fundo da sessão ID={}", com.restaurante.util.MoneyFormatter.format(valor), sessaoId);
        validarValorPositivo(valor);
        validarValorMinimo(valor);
        FundoConsumo fundo = buscarPorSessaoId(sessaoId);
        return executarCredito(fundo, valor, observacoes);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DÉBITO (DEBITO)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Debita valor de um pedido directamente do fundo (idempotente).
     * Invocado pelo motor financeiro após criação do pedido.
     *
     * @param fundo    fundo da sessão (obtido via sessao.getFundoConsumo())
     * @param pedidoId ID do pedido
     * @param valor    valor a debitar
     */
    @Retryable(
        retryFor = { ObjectOptimisticLockingFailureException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 500)
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransacaoFundo debitarDireto(FundoConsumo fundo, Long pedidoId, BigDecimal valor) {
        log.info("Debitando {} do fundo ID={} para pedido {}", com.restaurante.util.MoneyFormatter.format(valor), fundo.getId(), pedidoId);

        // IDEMPOTÊNCIA
        var existente = transacaoFundoRepository.findByPedidoIdAndTipo(pedidoId, TipoTransacaoFundo.DEBITO);
        if (existente.isPresent()) {
            log.info("Pedido {} já debitado. Retornando transação existente.", pedidoId);
            return existente.get();
        }

        validarValorPositivo(valor);

        if (!fundo.getAtivo()) {
            throw new BusinessException("Fundo de consumo encerrado. Não é possível debitar.");
        }
        
        // II-3: Carregar sessaoConsumo explicitamente para evitar NPE por lazy load.
        // O fundo pode vir de cache/proxy Hibernate sem a sessão inicializada.
        SessaoConsumo sessao = fundo.getSessaoConsumo();
        if (sessao == null) {
            // Fallback: recarregar fundo com sessão via repositório
            final long fundoId = fundo.getId();
            fundo = fundoConsumoRepository.findById(fundoId)
                .orElseThrow(() -> new ResourceNotFoundException("Fundo não encontrado: " + fundoId));
            sessao = fundo.getSessaoConsumo();
        }

        // Regra Pós-Pago x Pré-Pago — usando saldo real do ledger para validação
        BigDecimal saldoReal = transacaoFundoRepository.calcularSaldoAgregado(fundo.getId());
        boolean isPosPago = sessao != null &&
                com.restaurante.model.enums.TipoSessao.POS_PAGO.equals(sessao.getTipoSessao());

        if (!isPosPago && saldoReal.compareTo(valor) < 0) {
            throw new SaldoInsuficienteException(saldoReal, valor);
        }

        Pedido pedido = pedidoRepository.findById(pedidoId)
            .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + pedidoId));

        BigDecimal saldoAnterior = fundo.getSaldoAtual();
        
        TransacaoFundo transacao = TransacaoFundo.builder()
            .fundoConsumo(fundo)
            .valor(valor)
            .tipo(TipoTransacaoFundo.DEBITO)
            .pedido(pedido)
            .saldoAnterior(saldoAnterior)
            .saldoNovo(saldoAnterior.subtract(valor)) // Simulado para salvar no evento, o agregrado processa.
            .observacoes("Débito automático - Pedido #" + pedidoId)
            .build();

        transacao = transacaoFundoRepository.save(transacao);
        
        // Append-only constraint: Atualizar a derivação do saldo.
        // fundo.debitar(valor) não exposto publicamente para manter agregação.
        // A atualização de `@Version` ocorre implicitamente se tocamos fundo (porem não é obrigatorio dado ser persistido transacao isolada, se Fundo.saldoAtual dependesse de calculo)
        // Para acionar o optimistic locking no fundo e notificar que mudou:
        fundo.atualizarSaldoCache(transacaoFundoRepository.calcularSaldoAgregado(fundo.getId()));
        fundoConsumoRepository.save(fundo);
        
        log.info("Débito concluído. Saldo anterior: {}, Saldo novo: {}", com.restaurante.util.MoneyFormatter.format(saldoAnterior), com.restaurante.util.MoneyFormatter.format(fundo.getSaldoAtual()));
        return transacao;
    }

    /**
     * Debita pelo QR Code da sessão (compatibilidade com fluxo de balcão).
     */
    @Retryable(
        retryFor = { ObjectOptimisticLockingFailureException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 500)
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransacaoFundo debitarPorToken(String qrCodeSessao, Long pedidoId, BigDecimal valor) {
        FundoConsumo fundo = buscarPorToken(qrCodeSessao);
        return debitarDireto(fundo, pedidoId, valor);
    }

    /**
     * Debita pelo ID da sessão.
     */
    @Retryable(
        retryFor = { ObjectOptimisticLockingFailureException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 500)
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransacaoFundo debitar(Long sessaoId, Long pedidoId, BigDecimal valor) {
        FundoConsumo fundo = buscarPorSessaoId(sessaoId);
        return debitarDireto(fundo, pedidoId, valor);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ESTORNO
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Estorna valor de pedido cancelado (idempotente).
     * Opera por pedidoId — não depende de sessaoId/token.
     */
    @Retryable(
        retryFor = { ObjectOptimisticLockingFailureException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 500)
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransacaoFundo estornar(Long pedidoId) {
        log.info("Estornando valor do pedido {}", pedidoId);

        var estornoExistente = transacaoFundoRepository.findByPedidoIdAndTipo(pedidoId, TipoTransacaoFundo.ESTORNO);
        if (estornoExistente.isPresent()) {
            log.info("Pedido {} já estornado. Retornando transação existente.", pedidoId);
            return estornoExistente.get();
        }

        TransacaoFundo debito = transacaoFundoRepository.findByPedidoIdAndTipo(pedidoId, TipoTransacaoFundo.DEBITO)
            .orElseThrow(() -> new BusinessException("Não existe débito para o pedido " + pedidoId));

        FundoConsumo fundo = debito.getFundoConsumo();

        if (!fundo.getAtivo()) {
            throw new BusinessException("Fundo de consumo encerrado. Não é possível estornar.");
        }

        BigDecimal valorEstorno = debito.getValor();
        BigDecimal saldoAnterior = fundo.getSaldoAtual();
        
        Pedido pedido = pedidoRepository.findById(pedidoId)
            .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + pedidoId));

        TransacaoFundo transacao = TransacaoFundo.builder()
            .fundoConsumo(fundo)
            .valor(valorEstorno)
            .tipo(TipoTransacaoFundo.ESTORNO)
            .pedido(pedido)
            .saldoAnterior(saldoAnterior)
            .saldoNovo(saldoAnterior.add(valorEstorno))
            .observacoes("Estorno automático - Pedido #" + pedidoId + " cancelado")
            .build();

        transacao = transacaoFundoRepository.save(transacao);

        fundo.atualizarSaldoCache(transacaoFundoRepository.calcularSaldoAgregado(fundo.getId()));
        fundoConsumoRepository.save(fundo);
        
        log.info("Estorno concluído. Valor: {}, Saldo anterior: {}, Saldo novo: {}",
                 com.restaurante.util.MoneyFormatter.format(valorEstorno), com.restaurante.util.MoneyFormatter.format(saldoAnterior), com.restaurante.util.MoneyFormatter.format(fundo.getSaldoAtual()));
        return transacao;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // AJUSTE ADMINISTRATIVO (II-1)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Aplica ajuste administrativo ao saldo do fundo.
     *
     * RESTRIÇÕES:
     * - Apenas utilizadores com ROLE_ADMIN podem invocar este método.
     * - O motivo é OBRIGATÓRIO para auditoria.
     * - Valor positivo = aumenta saldo; valor negativo = diminui saldo.
     *
     * @param sessaoId  ID da sessão de consumo
     * @param valor     valor de ajuste (positivo ou negativo, nunca zero)
     * @param motivo    motivo obrigatório para compliance
     * @return          transação gerada
     */
    @Retryable(
        retryFor = { ObjectOptimisticLockingFailureException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 500)
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransacaoFundo ajustarSaldo(Long sessaoId, java.math.BigDecimal valor, String motivo) {
        if (valor == null || valor.compareTo(java.math.BigDecimal.ZERO) == 0) {
            throw new BusinessException("Valor de ajuste não pode ser zero");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException("Motivo é obrigatório para ajuste administrativo");
        }

        log.warn("AJUSTE ADMINISTRATIVO: sessão={}, valor={}, motivo={}", sessaoId, valor, motivo);

        FundoConsumo fundo = buscarPorSessaoId(sessaoId);
        if (!fundo.getAtivo()) {
            throw new BusinessException("Fundo de consumo encerrado. Não é possível ajustar.");
        }

        BigDecimal saldoAnterior = transacaoFundoRepository.calcularSaldoAgregado(fundo.getId());
        BigDecimal saldoNovo = saldoAnterior.add(valor);

        // Sessoes PRE_PAGO não podem ter saldo negativo após ajuste
        SessaoConsumo sessao = fundo.getSessaoConsumo();
        boolean isPosPago = sessao != null &&
                com.restaurante.model.enums.TipoSessao.POS_PAGO.equals(sessao.getTipoSessao());
        if (!isPosPago && saldoNovo.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new BusinessException(
                String.format("Ajuste resultaria em saldo negativo (%s) numa sessão PRE_PAGO", com.restaurante.util.MoneyFormatter.format(saldoNovo)));
        }

        // Valor absoluto para a transação de ajuste
        java.math.BigDecimal valorAbsoluto = valor.abs();

        TransacaoFundo transacao = TransacaoFundo.builder()
            .fundoConsumo(fundo)
            .valor(valorAbsoluto)
            .tipo(com.restaurante.model.enums.TipoTransacaoFundo.AJUSTE)
            .saldoAnterior(saldoAnterior)
            .saldoNovo(saldoNovo)
            .observacoes("AJUSTE ADMIN: " + motivo)
            .build();

        transacao = transacaoFundoRepository.save(transacao);
        fundo.atualizarSaldoCache(saldoNovo);
        fundoConsumoRepository.save(fundo);

        log.warn("Ajuste concluído. Saldo anterior: {} → Saldo novo: {}", com.restaurante.util.MoneyFormatter.format(saldoAnterior), com.restaurante.util.MoneyFormatter.format(saldoNovo));
        return transacao;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ENCERRAMENTO
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Encerra fundo pelo QR Code da sessão.
     */
    @Transactional
    public void encerrarFundoPorToken(String qrCodeSessao) {
        log.info("Encerrando fundo pelo QR Code: {}", qrCodeSessao);
        FundoConsumo fundo = buscarPorToken(qrCodeSessao);
        fundo.encerrar();
        fundoConsumoRepository.save(fundo);
    }

    /**
     * Encerra fundo pelo ID da sessão.
     */
    @Transactional
    public void encerrarFundo(Long sessaoId) {
        log.info("Encerrando fundo da sessão ID={}", sessaoId);
        FundoConsumo fundo = buscarPorSessaoId(sessaoId);
        fundo.encerrar();
        fundoConsumoRepository.save(fundo);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // VALIDAÇÃO
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Valida se a sessão tem saldo suficiente no fundo.
     */
    public void validarSaldoSuficiente(Long sessaoId, BigDecimal valorTotal) {
        log.info("Validando saldo suficiente para sessão {} - valor {}", sessaoId, valorTotal);
        try {
            FundoConsumo fundo = buscarPorSessaoId(sessaoId);
            SessaoConsumo sessao = fundo.getSessaoConsumo();
            boolean isPosPago = sessao != null &&
                    com.restaurante.model.enums.TipoSessao.POS_PAGO.equals(sessao.getTipoSessao());

            if (!isPosPago && !fundo.temSaldoSuficiente(valorTotal)) {
                throw new SaldoInsuficienteException(fundo.getSaldoAtual(), valorTotal);
            }
        } catch (ResourceNotFoundException e) {
            throw new SaldoInsuficienteException(
                    "Fundo de Consumo não encontrado. Recarregue antes de fazer pedidos.");
        }
    }

    /**
     * Valida saldo suficiente pelo QR Code da sessão (token público).
     */
    public void validarSaldoSuficientePorToken(String qrCodeSessao, BigDecimal valorTotal) {
        try {
            FundoConsumo fundo = buscarPorToken(qrCodeSessao);
            SessaoConsumo sessao = fundo.getSessaoConsumo();
            boolean isPosPago = sessao != null &&
                    com.restaurante.model.enums.TipoSessao.POS_PAGO.equals(sessao.getTipoSessao());

            if (!isPosPago && !fundo.temSaldoSuficiente(valorTotal)) {
                throw new SaldoInsuficienteException(fundo.getSaldoAtual(), valorTotal);
            }
        } catch (ResourceNotFoundException e) {
            throw new SaldoInsuficienteException(
                    "Fundo de Consumo não encontrado para este QR Code. Recarregue antes de fazer pedidos.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HISTÓRICO
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Histórico de transações pelo ID do fundo (paginado).
     */
    @Transactional(readOnly = true)
    public Page<TransacaoFundo> buscarHistoricoPorFundo(Long fundoId, Pageable pageable) {
        return transacaoFundoRepository.findByFundoConsumoIdOrderByCreatedAtDesc(fundoId, pageable);
    }

    /**
     * Histórico de transações pelo ID da sessão (paginado).
     */
    @Transactional(readOnly = true)
    public Page<TransacaoFundo> buscarHistorico(Long sessaoId, Pageable pageable) {
        FundoConsumo fundo = buscarPorSessaoId(sessaoId);
        return transacaoFundoRepository.findByFundoConsumoIdOrderByCreatedAtDesc(fundo.getId(), pageable);
    }

    /**
     * Histórico em período específico pelo ID da sessão.
     */
    @Transactional(readOnly = true)
    public List<TransacaoFundo> buscarHistoricoPeriodo(Long sessaoId, LocalDateTime dataInicio, LocalDateTime dataFim) {
        FundoConsumo fundo = buscarPorSessaoId(sessaoId);
        return transacaoFundoRepository.findByFundoConsumoIdAndPeriodo(fundo.getId(), dataInicio, dataFim);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS INTERNOS
    // ═══════════════════════════════════════════════════════════════════════

    private TransacaoFundo executarCredito(FundoConsumo fundo, BigDecimal valor, String observacoes) {
        if (!fundo.getAtivo()) {
            throw new BusinessException("Fundo de consumo encerrado. Não é possível recarregar.");
        }

        BigDecimal saldoAnterior = fundo.getSaldoAtual();
        
        TransacaoFundo transacao = TransacaoFundo.builder()
            .fundoConsumo(fundo)
            .valor(valor)
            .tipo(TipoTransacaoFundo.CREDITO)
            .saldoAnterior(saldoAnterior)
            .saldoNovo(saldoAnterior.add(valor))
            .observacoes(observacoes)
            .build();

        transacao = transacaoFundoRepository.save(transacao);

        fundo.atualizarSaldoCache(transacaoFundoRepository.calcularSaldoAgregado(fundo.getId()));
        fundoConsumoRepository.save(fundo);
        
        log.info("Recarga concluída. Saldo anterior: {}, Saldo novo: {}", com.restaurante.util.MoneyFormatter.format(saldoAnterior), com.restaurante.util.MoneyFormatter.format(fundo.getSaldoAtual()));
        return transacao;
    }

    private void validarValorPositivo(BigDecimal valor) {
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Valor deve ser maior que zero");
        }
    }

    private void validarValorMinimo(BigDecimal valor) {
        BigDecimal valorMinimo = configuracaoFinanceiraService.buscarOuCriarConfiguracao().getValorMinimoOperacao();
        if (valor.compareTo(valorMinimo) < 0) {
            throw new BusinessException(String.format(
                "Valor (%s) abaixo do mínimo permitido (%s)", com.restaurante.util.MoneyFormatter.format(valor), com.restaurante.util.MoneyFormatter.format(valorMinimo)));
        }
    }
}
