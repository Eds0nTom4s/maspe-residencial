package com.restaurante.financeiro.service;

import com.restaurante.exception.BusinessException;
import com.restaurante.financeiro.enums.TipoEventoFinanceiro;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayCallback;
import com.restaurante.financeiro.repository.PagamentoEventLogRepository;
import com.restaurante.financeiro.repository.PagamentoGatewayRepository;
import com.restaurante.model.entity.Pagamento;
import com.restaurante.model.entity.PagamentoEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviço para processar callbacks da AppyPay
 * 
 * RESPONSABILIDADES:
 * - Validar signature do callback
 * - Localizar pagamento por merchantTransactionId
 * - Confirmar/falhar pagamento
 * - Idempotência: ignorar duplicados
 * - Registrar auditoria
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PagamentoCallbackService {
    
    private final PagamentoGatewayRepository pagamentoRepository;
    private final PagamentoEventLogRepository eventLogRepository;
    private final PagamentoGatewayService pagamentoGatewayService;
    
    /**
     * Processa callback da AppyPay
     * 
     * FLUXO:
     * 1. Valida signature HMAC
     * 2. Busca pagamento por externalReference
     * 3. Se CONFIRMED: confirma pagamento + credita fundo
     * 4. Se FAILED/CANCELLED: marca como falho
     * 5. Registra evento de auditoria
     * 
     * IDEMPOTENTE: se já processado, retorna sem erro
     */
    @Transactional
    public void processarCallback(AppyPayCallback callback) {
        log.info("Processando callback: merchantTxId={}, status={}", 
            callback.getMerchantTransactionId(), 
            callback.getStatus());
        
        // TODO: Validar signature HMAC
        // validarSignature(callback);
        
        // Busca pagamento
        Pagamento pagamento = pagamentoRepository
            .findByExternalReference(callback.getMerchantTransactionId())
            .orElseThrow(() -> new BusinessException(
                "Pagamento não encontrado: " + callback.getMerchantTransactionId()
            ));
        
        // Registra callback recebido
        registrarEvento(
            TipoEventoFinanceiro.CALLBACK_RECEBIDO,
            pagamento,
            "Callback AppyPay: status=" + callback.getStatus()
        );
        
        // Processa conforme status
        switch (callback.getStatus()) {
            case "CONFIRMED":
                if (!pagamento.isConfirmado()) {
                    // Confirma pagamento (idempotente)
                    if (pagamento.isPrePago()) {
                        pagamentoGatewayService.confirmarPagamentoRecargaFundo(
                            pagamento.getId(), 
                            "APPYPAY_CALLBACK", 
                            "SYSTEM", 
                            null
                        );
                    } else {
                        // TODO: Implementar confirmação pós-pago
                        log.warn("Callback para pós-pago ainda não implementado");
                    }
                } else {
                    log.info("Pagamento já confirmado anteriormente. Callback duplicado.");
                }
                break;
                
            case "FAILED":
            case "CANCELLED":
                if (!pagamento.getStatus().isTerminal()) {
                    pagamento.marcarComoFalho("Gateway: " + callback.getStatus());
                    pagamentoRepository.save(pagamento);
                    
                    registrarEvento(
                        TipoEventoFinanceiro.PAGAMENTO_FALHOU,
                        pagamento,
                        "Pagamento falhou via callback: " + callback.getStatus()
                    );
                }
                break;
                
            default:
                log.warn("Status desconhecido no callback: {}", callback.getStatus());
        }
        
        log.info("Callback processado com sucesso");
    }
    
    /**
     * Registra evento de auditoria
     */
    private void registrarEvento(
        TipoEventoFinanceiro tipo,
        Pagamento pagamento,
        String motivo
    ) {
        PagamentoEventLog event = PagamentoEventLog.builder()
            .tipoEvento(tipo)
            .pagamento(pagamento)
            .pedido(pagamento.getPedido())
            .usuario("APPYPAY_CALLBACK")
            .role("SYSTEM")
            .ip(null)
            .motivo(motivo)
            .build();
        
        eventLogRepository.save(event);
    }
}
