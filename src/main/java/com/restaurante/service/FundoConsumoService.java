package com.restaurante.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.exception.SaldoInsuficienteException;
import com.restaurante.model.entity.Cliente;
import com.restaurante.model.entity.FundoConsumo;
import com.restaurante.model.entity.Pedido;
import com.restaurante.model.entity.TransacaoFundo;
import com.restaurante.model.enums.TipoTransacaoFundo;
import com.restaurante.repository.ClienteRepository;
import com.restaurante.repository.FundoConsumoRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.TransacaoFundoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service para gerenciar Fundo de Consumo (pré-pago)
 * 
 * RESPONSABILIDADES:
 * - Criar fundo para cliente
 * - Recarregar saldo (CREDITO)
 * - Debitar pedido (DEBITO) com validação de saldo
 * - Estornar cancelamento (ESTORNO)
 * - Consultar saldo e histórico
 * 
 * GARANTIAS:
 * - Concorrência protegida com @Version
 * - Isolation SERIALIZABLE para operações financeiras
 * - Idempotência: mesma operação não executa duas vezes
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FundoConsumoService {

    private final FundoConsumoRepository fundoConsumoRepository;
    private final TransacaoFundoRepository transacaoFundoRepository;
    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final ConfiguracaoFinanceiraService configuracaoFinanceiraService;

    /**
     * Cria fundo para cliente
     * Cliente pode ter apenas 1 fundo ativo
     */
    @Transactional
    public FundoConsumo criarFundo(Long clienteId) {
        log.info("Criando fundo de consumo para cliente {}", clienteId);

        // Valida cliente existe
        Cliente cliente = clienteRepository.findById(clienteId)
            .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado: " + clienteId));

        // Valida não existe fundo ativo
        if (fundoConsumoRepository.existsByClienteIdAndAtivoTrue(clienteId)) {
            throw new BusinessException("Cliente já possui fundo de consumo ativo");
        }

        // Cria fundo com saldo zero.
        // tokenPortador é gerado automaticamente — o QR Code impresso para o cliente
        // é derivado deste token, tal como no fluxo anónimo.
        FundoConsumo fundo = FundoConsumo.builder()
            .cliente(cliente)
            .tokenPortador(UUID.randomUUID().toString())
            .saldoAtual(BigDecimal.ZERO)
            .ativo(true)
            .build();

        return fundoConsumoRepository.save(fundo);
    }

    /**
     * Busca fundo ativo por cliente
     */
    @Transactional(readOnly = true)
    public FundoConsumo buscarPorCliente(Long clienteId) {
        return fundoConsumoRepository.findByClienteIdAndAtivoTrue(clienteId)
            .orElseThrow(() -> new ResourceNotFoundException("Fundo de consumo não encontrado para cliente: " + clienteId));
    }

    /**
     * Consulta saldo atual
     */
    @Transactional(readOnly = true)
    public BigDecimal consultarSaldo(Long clienteId) {
        FundoConsumo fundo = buscarPorCliente(clienteId);
        return fundo.getSaldoAtual();
    }

    /**
     * Recarrega saldo (CREDITO)
     * 
     * @param clienteId cliente
     * @param valor valor a creditar (> 0)
     * @param observacoes motivo da recarga
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransacaoFundo recarregar(Long clienteId, BigDecimal valor, String observacoes) {
        log.info("Recarregando {} AOA no fundo do cliente {}", valor, clienteId);

        // Valida valor positivo
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Valor de recarga deve ser maior que zero");
        }

        // Valida valor mínimo de operação
        BigDecimal valorMinimo = configuracaoFinanceiraService.buscarOuCriarConfiguracao().getValorMinimoOperacao();
        if (valor.compareTo(valorMinimo) < 0) {
            throw new BusinessException(
                String.format("Valor de recarga (%s AOA) abaixo do mínimo permitido (%s AOA)", 
                    valor, valorMinimo)
            );
        }

        // Busca fundo ativo
        FundoConsumo fundo = buscarPorCliente(clienteId);

        // Valida fundo ativo
        if (!fundo.getAtivo()) {
            throw new BusinessException("Fundo de consumo encerrado. Não é possível recarregar.");
        }

        // Credita valor
        BigDecimal saldoAnterior = fundo.getSaldoAtual();
        fundo.creditar(valor);
        BigDecimal saldoNovo = fundo.getSaldoAtual();

        // Salva fundo (@Version incrementa)
        fundoConsumoRepository.save(fundo);

        // Registra transação
        TransacaoFundo transacao = TransacaoFundo.builder()
            .fundoConsumo(fundo)
            .valor(valor)
            .tipo(TipoTransacaoFundo.CREDITO)
            .pedido(null)
            .saldoAnterior(saldoAnterior)
            .saldoNovo(saldoNovo)
            .observacoes(observacoes)
            .build();

        transacao = transacaoFundoRepository.save(transacao);

        log.info("Recarga concluída. Saldo anterior: R$ {}, Saldo novo: R$ {}", saldoAnterior, saldoNovo);
        return transacao;
    }

    /**
     * Debita valor para pedido (DEBITO)
     * 
     * IDEMPOTÊNCIA: Se pedido já foi debitado, retorna transação existente
     * 
     * @param clienteId cliente
     * @param pedidoId pedido
     * @param valor valor a debitar
     * @throws SaldoInsuficienteException se saldo insuficiente
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransacaoFundo debitar(Long clienteId, Long pedidoId, BigDecimal valor) {
        log.info("Debitando R$ {} do fundo do cliente {} para pedido {}", valor, clienteId, pedidoId);

        // IDEMPOTÊNCIA: verifica se já existe débito para este pedido
        var transacaoExistente = transacaoFundoRepository.findByPedidoIdAndTipo(pedidoId, TipoTransacaoFundo.DEBITO);
        if (transacaoExistente.isPresent()) {
            log.info("Pedido {} já foi debitado anteriormente. Retornando transação existente.", pedidoId);
            return transacaoExistente.get();
        }

        // Valida valor positivo
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Valor de débito deve ser maior que zero");
        }

        // Busca fundo ativo (usa @Version para concorrência otimista)
        FundoConsumo fundo = buscarPorCliente(clienteId);

        // Valida fundo ativo
        if (!fundo.getAtivo()) {
            throw new BusinessException("Fundo de consumo encerrado. Não é possível debitar.");
        }

        // Valida saldo suficiente
        if (!fundo.temSaldoSuficiente(valor)) {
            throw new SaldoInsuficienteException(fundo.getSaldoAtual(), valor);
        }

        // Busca pedido
        Pedido pedido = pedidoRepository.findById(pedidoId)
            .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + pedidoId));

        // Debita valor
        BigDecimal saldoAnterior = fundo.getSaldoAtual();
        fundo.debitar(valor);
        BigDecimal saldoNovo = fundo.getSaldoAtual();

        // Salva fundo (@Version incrementa)
        fundoConsumoRepository.save(fundo);

        // Registra transação
        TransacaoFundo transacao = TransacaoFundo.builder()
            .fundoConsumo(fundo)
            .valor(valor)
            .tipo(TipoTransacaoFundo.DEBITO)
            .pedido(pedido)
            .saldoAnterior(saldoAnterior)
            .saldoNovo(saldoNovo)
            .observacoes("Débito automático - Pedido #" + pedidoId)
            .build();

        transacao = transacaoFundoRepository.save(transacao);

        log.info("Débito concluído. Saldo anterior: R$ {}, Saldo novo: R$ {}", saldoAnterior, saldoNovo);
        return transacao;
    }

    /**
     * Estorna valor de pedido cancelado (ESTORNO)
     * 
     * IDEMPOTÊNCIA: Se estorno já foi feito, retorna transação existente
     * 
     * @param pedidoId pedido cancelado
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransacaoFundo estornar(Long pedidoId) {
        log.info("Estornando valor do pedido {}", pedidoId);

        // IDEMPOTÊNCIA: verifica se já existe estorno para este pedido
        var estornoExistente = transacaoFundoRepository.findByPedidoIdAndTipo(pedidoId, TipoTransacaoFundo.ESTORNO);
        if (estornoExistente.isPresent()) {
            log.info("Pedido {} já foi estornado anteriormente. Retornando transação existente.", pedidoId);
            return estornoExistente.get();
        }

        // Busca débito original
        TransacaoFundo debito = transacaoFundoRepository.findByPedidoIdAndTipo(pedidoId, TipoTransacaoFundo.DEBITO)
            .orElseThrow(() -> new BusinessException("Não existe débito para o pedido " + pedidoId));

        // Busca fundo
        FundoConsumo fundo = debito.getFundoConsumo();

        // Valida fundo ativo
        if (!fundo.getAtivo()) {
            throw new BusinessException("Fundo de consumo encerrado. Não é possível estornar.");
        }

        // Estorna valor (credita de volta)
        BigDecimal valorEstorno = debito.getValor();
        BigDecimal saldoAnterior = fundo.getSaldoAtual();
        fundo.creditar(valorEstorno);
        BigDecimal saldoNovo = fundo.getSaldoAtual();

        // Salva fundo (@Version incrementa)
        fundoConsumoRepository.save(fundo);

        // Busca pedido
        Pedido pedido = pedidoRepository.findById(pedidoId)
            .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + pedidoId));

        // Registra transação de estorno
        TransacaoFundo transacao = TransacaoFundo.builder()
            .fundoConsumo(fundo)
            .valor(valorEstorno)
            .tipo(TipoTransacaoFundo.ESTORNO)
            .pedido(pedido)
            .saldoAnterior(saldoAnterior)
            .saldoNovo(saldoNovo)
            .observacoes("Estorno automático - Pedido #" + pedidoId + " cancelado")
            .build();

        transacao = transacaoFundoRepository.save(transacao);

        log.info("Estorno concluído. Valor: R$ {}, Saldo anterior: R$ {}, Saldo novo: R$ {}", 
                 valorEstorno, saldoAnterior, saldoNovo);
        return transacao;
    }

    /**
     * Busca histórico de transações com paginação (por clienteId)
     */
    @Transactional(readOnly = true)
    public Page<TransacaoFundo> buscarHistorico(Long clienteId, Pageable pageable) {
        FundoConsumo fundo = buscarPorCliente(clienteId);
        return transacaoFundoRepository.findByFundoConsumoIdOrderByCreatedAtDesc(fundo.getId(), pageable);
    }

    /**
     * Busca histórico de transações directamente pelo ID do fundo.
     * Funciona para fundos identificados e anónimos.
     */
    @Transactional(readOnly = true)
    public Page<TransacaoFundo> buscarHistoricoPorFundo(Long fundoId, Pageable pageable) {
        return transacaoFundoRepository.findByFundoConsumoIdOrderByCreatedAtDesc(fundoId, pageable);
    }

    /**
     * Encerra fundo por token — funciona para ambos os tipos (identificado e anónimo).
     */
    @Transactional
    public void encerrarFundoPorToken(String tokenPortador) {
        log.info("Encerrando fundo pelo token: {}", tokenPortador);
        FundoConsumo fundo = buscarPorToken(tokenPortador);
        fundo.encerrar();
        fundoConsumoRepository.save(fundo);
    }

    /**
     * Busca histórico em período específico
     */
    @Transactional(readOnly = true)
    public List<TransacaoFundo> buscarHistoricoPeriodo(Long clienteId, LocalDateTime dataInicio, LocalDateTime dataFim) {
        FundoConsumo fundo = buscarPorCliente(clienteId);
        return transacaoFundoRepository.findByFundoConsumoIdAndPeriodo(fundo.getId(), dataInicio, dataFim);
    }

    /**
     * Encerra fundo (não permite mais movimentação) por clienteId
     */
    @Transactional
    public void encerrarFundo(Long clienteId) {
        log.info("Encerrando fundo de consumo do cliente {}", clienteId);
        FundoConsumo fundo = buscarPorCliente(clienteId);
        fundo.encerrar();
        fundoConsumoRepository.save(fundo);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FLUXO ANÓNIMO – operações por token portador (QR Code UUID)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Cria fundo anónimo identificado pelo token do QR Code.
     *
     * <p>Não exige cliente. O QR Code é o único portador do saldo.
     * <strong>Perda do QR = perda do saldo</strong>; não há recuperação.
     *
     * @param tokenPortador UUID único do QR Code emitido para o consumidor
     */
    @Transactional
    public FundoConsumo criarFundoAnonimo(String tokenPortador) {
        log.info("Criando fundo de consumo anónimo para token: {}", tokenPortador);

        if (tokenPortador == null || tokenPortador.isBlank()) {
            throw new BusinessException("Token portador é obrigatório para fundo anónimo");
        }

        if (fundoConsumoRepository.existsByTokenPortadorAndAtivoTrue(tokenPortador)) {
            throw new BusinessException("Já existe fundo de consumo ativo para este QR Code");
        }

        FundoConsumo fundo = FundoConsumo.builder()
                .cliente(null)
                .tokenPortador(tokenPortador)
                .saldoAtual(BigDecimal.ZERO)
                .ativo(true)
                .build();

        return fundoConsumoRepository.save(fundo);
    }

    /**
     * Busca fundo ativo pelo token portador.
     */
    @Transactional(readOnly = true)
    public FundoConsumo buscarPorToken(String tokenPortador) {
        return fundoConsumoRepository.findByTokenPortadorAndAtivoTrue(tokenPortador)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Fundo de consumo não encontrado para o QR Code: " + tokenPortador));
    }

    /**
     * Consulta saldo do fundo anónimo pelo token.
     */
    @Transactional(readOnly = true)
    public BigDecimal consultarSaldoPorToken(String tokenPortador) {
        return buscarPorToken(tokenPortador).getSaldoAtual();
    }

    /**
     * Recarrega saldo do fundo anónimo (CREDITO por token).
     * Mesmas regras de validação do fluxo identificado.
     *
     * @param tokenPortador UUID do QR Code
     * @param valor         valor a creditar (> mínimo configurado)
     * @param observacoes   motivo da recarga
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransacaoFundo recarregarPorToken(String tokenPortador, BigDecimal valor, String observacoes) {
        log.info("Recarregando {} AOA no fundo anónimo do token {}", valor, tokenPortador);

        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Valor de recarga deve ser maior que zero");
        }

        BigDecimal valorMinimo = configuracaoFinanceiraService.buscarOuCriarConfiguracao().getValorMinimoOperacao();
        if (valor.compareTo(valorMinimo) < 0) {
            throw new BusinessException(String.format(
                    "Valor de recarga (%s AOA) abaixo do mínimo permitido (%s AOA)", valor, valorMinimo));
        }

        FundoConsumo fundo = buscarPorToken(tokenPortador);

        if (!fundo.getAtivo()) {
            throw new BusinessException("Fundo de consumo anónimo encerrado. Não é possível recarregar.");
        }

        BigDecimal saldoAnterior = fundo.getSaldoAtual();
        fundo.creditar(valor);
        fundoConsumoRepository.save(fundo);

        TransacaoFundo transacao = TransacaoFundo.builder()
                .fundoConsumo(fundo)
                .valor(valor)
                .tipo(TipoTransacaoFundo.CREDITO)
                .saldoAnterior(saldoAnterior)
                .saldoNovo(fundo.getSaldoAtual())
                .observacoes(observacoes != null ? observacoes : "Recarga anónima")
                .build();

        transacao = transacaoFundoRepository.save(transacao);
        log.info("Recarga anónima concluída. Saldo anterior: {} AOA, Saldo novo: {} AOA",
                saldoAnterior, fundo.getSaldoAtual());
        return transacao;
    }

    /**
     * Debita valor para pedido a partir do fundo anónimo (DEBITO por token).
     *
     * <p>Idempotente: se o pedido já foi debitado, retorna a transação existente.
     *
     * @param tokenPortador UUID do QR Code
     * @param pedidoId      ID do pedido
     * @param valor         valor a debitar
     * @throws SaldoInsuficienteException se saldo insuficiente
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransacaoFundo debitarPorToken(String tokenPortador, Long pedidoId, BigDecimal valor) {
        log.info("Debitando {} AOA do fundo anónimo (token: {}) para pedido {}", valor, tokenPortador, pedidoId);

        // IDEMPOTÊNCIA
        var transacaoExistente = transacaoFundoRepository.findByPedidoIdAndTipo(pedidoId, TipoTransacaoFundo.DEBITO);
        if (transacaoExistente.isPresent()) {
            log.info("Pedido {} já foi debitado. Retornando transação existente.", pedidoId);
            return transacaoExistente.get();
        }

        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Valor de débito deve ser maior que zero");
        }

        FundoConsumo fundo = buscarPorToken(tokenPortador);

        if (!fundo.getAtivo()) {
            throw new BusinessException("Fundo de consumo anónimo encerrado. Não é possível debitar.");
        }

        if (!fundo.temSaldoSuficiente(valor)) {
            throw new SaldoInsuficienteException(fundo.getSaldoAtual(), valor);
        }

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: " + pedidoId));

        BigDecimal saldoAnterior = fundo.getSaldoAtual();
        fundo.debitar(valor);
        fundoConsumoRepository.save(fundo);

        TransacaoFundo transacao = TransacaoFundo.builder()
                .fundoConsumo(fundo)
                .valor(valor)
                .tipo(TipoTransacaoFundo.DEBITO)
                .pedido(pedido)
                .saldoAnterior(saldoAnterior)
                .saldoNovo(fundo.getSaldoAtual())
                .observacoes("Débito anónimo - Pedido #" + pedidoId)
                .build();

        transacao = transacaoFundoRepository.save(transacao);
        log.info("Débito anónimo concluído. Saldo anterior: {} AOA, Saldo novo: {} AOA",
                saldoAnterior, fundo.getSaldoAtual());
        return transacao;
    }

    /**
     * Valida se o fundo anónimo tem saldo suficiente.
     * Usado pelo motor financeiro antes de criar o pedido.
     */
    public void validarSaldoSuficientePorToken(String tokenPortador, BigDecimal valorTotal) {
        try {
            FundoConsumo fundo = buscarPorToken(tokenPortador);
            if (!fundo.temSaldoSuficiente(valorTotal)) {
                throw new SaldoInsuficienteException(fundo.getSaldoAtual(), valorTotal);
            }
        } catch (ResourceNotFoundException e) {
            throw new SaldoInsuficienteException(
                    "Fundo de Consumo não encontrado para este QR Code. Recarregue antes de fazer pedidos.");
        }
    }
}
