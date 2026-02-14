package com.restaurante.financeiro.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.financeiro.enums.MetodoPagamentoAppyPay;
import com.restaurante.financeiro.enums.StatusPagamentoGateway;
import com.restaurante.financeiro.enums.TipoPagamentoFinanceiro;
import com.restaurante.financeiro.enums.TipoEventoFinanceiro;
import com.restaurante.financeiro.gateway.appypay.AppyPayClient;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayCallback;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeRequest;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeResponse;
import com.restaurante.financeiro.repository.PagamentoEventLogRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.*;
import com.restaurante.model.enums.StatusFinanceiroPedido;
import com.restaurante.model.enums.TipoTransacaoFundo;
import com.restaurante.repository.FundoConsumoRepository;
import com.restaurante.repository.PedidoRepository;
import com.restaurante.repository.TransacaoFundoRepository;
import com.restaurante.service.PedidoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Serviço de Pagamentos com Gateway AppyPay
 * 
 * RESPONSABILIDADES:
 * - Criar pagamentos (pré-pago e pós-pago)
 * - Integrar com AppyPay (GPO/REF)
 * - Processar callbacks
 * - Confirmar/estornar pagamentos
 * - Registrar auditoria financeira
 * 
 * REGRAS:
 * - Idempotência por externalReference
 * - Callback duplicado = NO-OP
 * - Confirmação atualiza StatusFinanceiroPedido
 * - Recalcula status do Pedido após confirmação
 * - Auditoria obrigatória para ações críticas
 * 
 * BASEADO EM ARENATICKET (VALIDADO EM PRODUÇÃO)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PagamentoGatewayService {
    
    private final PagamentoGatewayRepository pagamentoRepository;
    private final PagamentoEventLogRepository eventLogRepository;
    private final FundoConsumoRepository fundoConsumoRepository;
    private final TransacaoFundoRepository transacaoFundoRepository;
    private final PedidoRepository pedidoRepository;
    private final AppyPayClient appyPayClient;
    private final PedidoService pedidoService;
    private final ObjectMapper objectMapper;
    
    /**
     * Cria pagamento para recarga de fundo (PRÉ-PAGO)
     * 
     * FLUXO:
     * 1. Valida cliente e fundo
     * 2. Cria Pagamento (PENDENTE)
     * 3. Chama AppyPay /charges
     * 4. GPO: confirma imediatamente + credita fundo
     * 5. REF: retorna entidade/referência + aguarda callback
     * 
     * @param fundoId ID do fundo de consumo
     * @param valor Valor em AOA
     * @param metodo GPO ou REF
     * @param usuario Usuário que solicitou (auditoria)
     * @param role Role do usuário
     * @param ip IP da requisição
     * @return Pagamento criado
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Pagamento criarPagamentoRecargaFundo(
        Long fundoId, 
        BigDecimal valor, 
        MetodoPagamentoAppyPay metodo,
        String usuario,
        String role,
        String ip
    ) {
        log.info("Criando pagamento recarga fundo: fundoId={}, valor={}, metodo={}", 
            fundoId, valor, metodo);
        
        // Valida fundo ativo
        FundoConsumo fundo = fundoConsumoRepository.findById(fundoId)
            .orElseThrow(() -> new ResourceNotFoundException("Fundo não encontrado"));
        
        if (!fundo.getAtivo()) {
            throw new BusinessException("Fundo encerrado. Não é possível recarregar.");
        }
        
        // Valida valor
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Valor deve ser maior que zero");
        }
        
        // Gera referência externa curta (<= 15 chars)
        String externalRef = gerarExternalReference();
        
        // Cria Pagamento (PENDENTE)
        Pagamento pagamento = Pagamento.builder()
            .fundoConsumo(fundo)
            .pedido(null)
            .tipoPagamento(TipoPagamentoFinanceiro.PRE_PAGO)
            .metodo(metodo)
            .amount(valor)
            .status(StatusPagamentoGateway.PENDENTE)
            .externalReference(externalRef)
            .observacoes("Recarga de fundo #" + fundoId)
            .build();
        
        pagamento = pagamentoRepository.save(pagamento);
        
        // Registra evento
        registrarEvento(
            TipoEventoFinanceiro.PAGAMENTO_CRIADO,
            pagamento,
            null,
            usuario,
            role,
            ip,
            "Recarga de fundo solicitada: " + valor + " AOA"
        );
        
        // Chama AppyPay
        try {
            AppyPayChargeRequest request = AppyPayChargeRequest.builder()
                .merchantTransactionId(externalRef)
                .amount(converterParaCentavos(valor))
                .paymentMethod(metodo.getCodigo())
                .description("Recarga Fundo #" + fundoId)
                .build();
            
            AppyPayChargeResponse response = appyPayClient.createCharge(request);
            
            // Atualiza pagamento com dados do gateway
            pagamento.setGatewayChargeId(response.getChargeId());
            pagamento.setGatewayResponse(serializarJson(response));
            
            // REF: salva entidade e referência
            if ("REF".equals(response.getPaymentMethod())) {
                pagamento.setEntidade(response.getEntity());
                pagamento.setReferencia(response.getReference());
            }
            
            pagamentoRepository.save(pagamento);
            
            // GPO: confirmação imediata
            if ("GPO".equals(response.getPaymentMethod()) && 
                "CONFIRMED".equals(response.getStatus())) {
                confirmarPagamentoRecargaFundo(pagamento.getId(), "SYSTEM", "SYSTEM", null);
            }
            
            log.info("Pagamento recarga criado: id={}, chargeId={}, status={}", 
                pagamento.getId(), response.getChargeId(), response.getStatus());
            
            return pagamento;
            
        } catch (Exception e) {
            log.error("Erro ao criar cobrança AppyPay: {}", e.getMessage(), e);
            
            // Marca pagamento como falho
            pagamento.marcarComoFalho("Erro gateway: " + e.getMessage());
            pagamentoRepository.save(pagamento);
            
            // Registra evento de falha
            registrarEvento(
                TipoEventoFinanceiro.PAGAMENTO_FALHOU,
                pagamento,
                null,
                usuario,
                role,
                ip,
                "Erro ao criar cobrança: " + e.getMessage()
            );
            
            throw new BusinessException("Falha ao processar pagamento: " + e.getMessage());
        }
    }
    
    /**
     * Confirma pagamento de recarga de fundo
     * 
     * FLUXO:
     * 1. Valida pagamento PENDENTE
     * 2. Marca como CONFIRMADO
     * 3. Cria TransacaoFundo (CREDITO)
     * 4. Atualiza saldo do fundo
     * 5. Registra auditoria
     * 
     * IDEMPOTENTE: se já confirmado, retorna sem erro
     * 
     * @param pagamentoId ID do pagamento
     * @param usuario Usuário (ou SYSTEM para callback)
     * @param role Role
     * @param ip IP
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void confirmarPagamentoRecargaFundo(
        Long pagamentoId, 
        String usuario, 
        String role, 
        String ip
    ) {
        log.info("Confirmando pagamento recarga: pagamentoId={}", pagamentoId);
        
        Pagamento pagamento = pagamentoRepository.findById(pagamentoId)
            .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado"));
        
        // IDEMPOTÊNCIA: já confirmado
        if (pagamento.isConfirmado()) {
            log.info("Pagamento já confirmado anteriormente. NO-OP.");
            return;
        }
        
        // Valida tipo
        if (!pagamento.isPrePago()) {
            throw new BusinessException("Pagamento não é pré-pago");
        }
        
        FundoConsumo fundo = pagamento.getFundoConsumo();
        if (fundo == null) {
            throw new BusinessException("Pagamento sem fundo vinculado");
        }
        
        // Confirma pagamento
        pagamento.confirmar();
        pagamentoRepository.save(pagamento);
        
        // Registra TransacaoFundo (CREDITO)
        BigDecimal saldoAnterior = fundo.getSaldoAtual();
        fundo.creditar(pagamento.getAmount());
        BigDecimal saldoNovo = fundo.getSaldoAtual();
        
        fundoConsumoRepository.save(fundo);
        
        TransacaoFundo transacao = TransacaoFundo.builder()
            .fundoConsumo(fundo)
            .valor(pagamento.getAmount())
            .tipo(TipoTransacaoFundo.CREDITO)
            .pedido(null)
            .saldoAnterior(saldoAnterior)
            .saldoNovo(saldoNovo)
            .observacoes("Recarga via AppyPay - Ref: " + pagamento.getExternalReference())
            .build();
        
        transacaoFundoRepository.save(transacao);
        
        // Registra evento
        registrarEvento(
            TipoEventoFinanceiro.CONFIRMACAO_PAGAMENTO,
            pagamento,
            null,
            usuario,
            role,
            ip,
            String.format("Recarga confirmada: %s AOA. Saldo: %s → %s", 
                pagamento.getAmount(), saldoAnterior, saldoNovo)
        );
        
        log.info("Pagamento recarga confirmado com sucesso. Novo saldo: {}", saldoNovo);
    }
    
    /**
     * Gera referência externa curta (<= 15 chars)
     * Formato: REC{timestamp_curto}{random}
     */
    private String gerarExternalReference() {
        long timestamp = System.currentTimeMillis() % 100000000; // 8 dígitos
        int random = (int)(Math.random() * 1000); // 3 dígitos
        return String.format("REC%08d%03d", timestamp, random);
    }
    
    /**
     * Converte BigDecimal para centavos (Long)
     * AppyPay espera valores sem decimais
     */
    private Long converterParaCentavos(BigDecimal valor) {
        return valor.multiply(BigDecimal.valueOf(100)).longValue();
    }
    
    /**
     * Serializa objeto para JSON
     */
    private String serializarJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Erro ao serializar JSON: {}", e.getMessage());
            return obj.toString();
        }
    }
    
    /**
     * Registra evento de auditoria
     */
    private void registrarEvento(
        TipoEventoFinanceiro tipo,
        Pagamento pagamento,
        Pedido pedido,
        String usuario,
        String role,
        String ip,
        String motivo
    ) {
        PagamentoEventLog event = PagamentoEventLog.builder()
            .tipoEvento(tipo)
            .pagamento(pagamento)
            .pedido(pedido)
            .usuario(usuario != null ? usuario : "SYSTEM")
            .role(role != null ? role : "SYSTEM")
            .ip(ip)
            .motivo(motivo)
            .build();
        
        eventLogRepository.save(event);
    }
}
