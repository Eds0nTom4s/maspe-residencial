package com.restaurante.financeiro.gateway.appypay;

import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeRequest;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Cliente HTTP para comunicação com API AppyPay
 * 
 * RESPONSABILIDADES:
 * - Criar cobranças (charges)
 * - Consultar status de cobrança
 * - Tratar erros HTTP
 * - NÃO contém lógica de negócio
 * 
 * BASEADO EM ARENATICKET (VALIDADO EM PRODUÇÃO)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppyPayClient {
    
    private final AppyPayProperties properties;
    private final AppyPayAuthService authService;
    private final RestTemplate restTemplate;
    
    /**
     * Cria cobrança na AppyPay
     * 
     * @param request Dados da cobrança
     * @return Response com chargeId, entidade/referência (REF) ou paymentUrl (GPO)
     * @throws RuntimeException em caso de erro
     */
    public AppyPayChargeResponse createCharge(AppyPayChargeRequest request) {
        if (properties.isMock()) {
            log.warn("Modo MOCK ativado - simulando cobrança AppyPay");
            return criarChargeMock(request);
        }
        
        // Valida e resolve método de pagamento com ID
        String paymentMethodId = resolverMetodoPagamento(request.getPaymentMethod());
        request.setPaymentMethod(paymentMethodId);
        
        log.info("Criando cobrança AppyPay: merchantTxId={}, amount={}, method={}", 
            request.getMerchantTransactionId(), 
            request.getAmount(), 
            paymentMethodId
        );
        
        try {
            // Obtém token (cache ou renova)
            String token = authService.getAccessToken();
            
            // Monta request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);
            
            HttpEntity<AppyPayChargeRequest> httpRequest = new HttpEntity<>(request, headers);
            
            String url = properties.getBaseUrl() + "/charges";
            
            // Chama API
            ResponseEntity<AppyPayChargeResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                httpRequest,
                AppyPayChargeResponse.class
            );
            
            if (response.getStatusCode() != HttpStatus.OK && 
                response.getStatusCode() != HttpStatus.CREATED) {
                throw new RuntimeException("Status inesperado: " + response.getStatusCode());
            }
            
            AppyPayChargeResponse chargeResponse = response.getBody();
            if (chargeResponse == null) {
                throw new RuntimeException("Response vazio da AppyPay");
            }
            
            log.info("Cobrança criada com sucesso: chargeId={}, status={}", 
                chargeResponse.getChargeId(), 
                chargeResponse.getStatus()
            );
            
            if (properties.isDebug()) {
                log.debug("AppyPay Response: {}", chargeResponse);
            }
            
            return chargeResponse;
            
        } catch (HttpClientErrorException e) {
            log.error("Erro HTTP 4xx ao criar cobrança: status={}, body={}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Erro na requisição AppyPay: " + e.getMessage(), e);
            
        } catch (HttpServerErrorException e) {
            log.error("Erro HTTP 5xx ao criar cobrança: status={}, body={}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("AppyPay indisponível: " + e.getMessage(), e);
            
        } catch (Exception e) {
            log.error("Erro inesperado ao criar cobrança AppyPay: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao comunicar com AppyPay: " + e.getMessage(), e);
        }
    }
    
    /**
     * Consulta status de uma cobrança
     * 
     * @param chargeId ID da cobrança na AppyPay
     * @return Response atualizado
     */
    public AppyPayChargeResponse getCharge(String chargeId) {
        if (properties.isMock()) {
            log.warn("Modo MOCK ativado - simulando consulta");
            return AppyPayChargeResponse.builder()
                .chargeId(chargeId)
                .status("CONFIRMED")
                .build();
        }
        
        log.info("Consultando cobrança AppyPay: chargeId={}", chargeId);
        
        try {
            String token = authService.getAccessToken();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            String url = properties.getBaseUrl() + "/charges/" + chargeId;
            
            ResponseEntity<AppyPayChargeResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                AppyPayChargeResponse.class
            );
            
            AppyPayChargeResponse chargeResponse = response.getBody();
            
            log.info("Cobrança consultada: chargeId={}, status={}", 
                chargeId, 
                chargeResponse != null ? chargeResponse.getStatus() : "null"
            );
            
            return chargeResponse;
            
        } catch (Exception e) {
            log.error("Erro ao consultar cobrança: {}", e.getMessage());
            throw new RuntimeException("Falha ao consultar AppyPay: " + e.getMessage(), e);
        }
    }
    
    /**
     * Simula criação de cobrança (modo MOCK)
     * Útil para desenvolvimento local sem API real
     */
    private AppyPayChargeResponse criarChargeMock(AppyPayChargeRequest request) {
        String chargeId = "MOCK_CHARGE_" + System.currentTimeMillis();
        
        AppyPayChargeResponse.AppyPayChargeResponseBuilder builder = AppyPayChargeResponse.builder()
            .chargeId(chargeId)
            .merchantTransactionId(request.getMerchantTransactionId())
            .amount(request.getAmount())
            .paymentMethod(request.getPaymentMethod())
            .createdAt(java.time.Instant.now().toString());
        
        // GPO: confirmação imediata
        if ("GPO".equals(request.getPaymentMethod())) {
            builder.status("CONFIRMED")
                .paymentUrl("https://mock.appypay.ao/pay/" + chargeId);
        }
        // REF: aguarda pagamento
        else if ("REF".equals(request.getPaymentMethod())) {
            builder.status("PENDING")
                .entity("10100")
                .reference("999 " + (100000 + (int)(Math.random() * 899999)));
        }
        
        return builder.build();
    }
    
    /**
     * Resolve método de pagamento (GPO/REF) para ID específico
     * 
     * @param metodo "GPO" ou "REF"
     * @return ID completo (ex: GPO_fd67da50-9858-45c4-8871-b271709328c7)
     */
    private String resolverMetodoPagamento(String metodo) {
        if (metodo == null || metodo.isBlank()) {
            throw new IllegalArgumentException("Método de pagamento não pode ser nulo");
        }
        
        String metodoPadrao = metodo.toUpperCase();
        
        if ("GPO".equals(metodoPadrao)) {
            if (properties.getGpoMethodId() != null && !properties.getGpoMethodId().isBlank()) {
                return properties.getGpoMethodId();
            }
            log.warn("GPO method ID não configurado, usando padrão: GPO");
            return "GPO";
        }
        
        if ("REF".equals(metodoPadrao)) {
            if (properties.getRefMethodId() != null && !properties.getRefMethodId().isBlank()) {
                return properties.getRefMethodId();
            }
            log.warn("REF method ID não configurado, usando padrão: REF");
            return "REF";
        }
        
        // Retorna como está se não reconhecido
        log.warn("Método de pagamento desconhecido: {}", metodo);
        return metodo;
    }
}
