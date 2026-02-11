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

        // Cria fundo com saldo zero
        FundoConsumo fundo = FundoConsumo.builder()
            .cliente(cliente)
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
        log.info("Recarregando R$ {} no fundo do cliente {}", valor, clienteId);

        // Valida valor positivo
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Valor de recarga deve ser maior que zero");
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

        // Busca fundo ativo
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
     * Busca histórico de transações com paginação
     */
    @Transactional(readOnly = true)
    public Page<TransacaoFundo> buscarHistorico(Long clienteId, Pageable pageable) {
        FundoConsumo fundo = buscarPorCliente(clienteId);
        return transacaoFundoRepository.findByFundoConsumoIdOrderByCreatedAtDesc(fundo.getId(), pageable);
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
     * Encerra fundo (não permite mais movimentação)
     */
    @Transactional
    public void encerrarFundo(Long clienteId) {
        log.info("Encerrando fundo de consumo do cliente {}", clienteId);
        FundoConsumo fundo = buscarPorCliente(clienteId);
        fundo.encerrar();
        fundoConsumoRepository.save(fundo);
    }
}
