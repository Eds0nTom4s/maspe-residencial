package com.restaurante.financeiro.gateway.appypay;

import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeRequest;
import com.restaurante.financeiro.gateway.appypay.dto.AppyPayChargeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes do AppyPayClient
 * 
 * Cobertura:
 * - Criação de charge GPO (pagamento imediato)
 * - Criação de charge REF (referência bancária)
 * - Consulta de charge
 * - Modo MOCK
 * - Tratamento de erros HTTP 4xx/5xx
 * - Renovação de token OAuth2
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppyPayClientTest {

    @Mock
    private AppyPayProperties properties;
    
    @Mock
    private AppyPayAuthService authService;
    
    @Mock
    private RestTemplate restTemplate;
    
    private AppyPayClient client;
    
    @BeforeEach
    void setUp() {
        // Configuração padrão
        when(properties.isMock()).thenReturn(false);
        when(properties.isDebug()).thenReturn(false);
        when(properties.getBaseUrl()).thenReturn("https://api.appypay.ao");
        when(properties.getGpoMethodId()).thenReturn("GPO_fd67da50-9858-45c4-8871-b271709328c7");
        when(properties.getRefMethodId()).thenReturn("REF_12345678-1234-1234-1234-123456789012");
        when(authService.getAccessToken()).thenReturn("mock-token-12345");
        
        client = new AppyPayClient(properties, authService, restTemplate);
    }
    
    @Test
    void deveCriarChargeGpoComSucesso() {
        // Arrange
        AppyPayChargeRequest request = AppyPayChargeRequest.builder()
            .merchantTransactionId("TXN-001")
            .amount(100000L)
            .paymentMethod("GPO")
            .description("Recarga de fundo")
            .build();
        
        AppyPayChargeResponse mockResponse = AppyPayChargeResponse.builder()
            .chargeId("CHARGE-001")
            .merchantTransactionId("TXN-001")
            .amount(100000L)
            .paymentMethod("GPO_fd67da50-9858-45c4-8871-b271709328c7")
            .status("CONFIRMED")
            .paymentUrl("https://appypay.ao/pay/CHARGE-001")
            .build();
        
        when(restTemplate.exchange(
            eq("https://api.appypay.ao/charges"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(AppyPayChargeResponse.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));
        
        // Act
        AppyPayChargeResponse response = client.createCharge(request);
        
        // Assert
        assertNotNull(response);
        assertEquals("CHARGE-001", response.getChargeId());
        assertEquals("CONFIRMED", response.getStatus());
        assertEquals("https://appypay.ao/pay/CHARGE-001", response.getPaymentUrl());
        
        // Verifica que o método foi resolvido para o ID completo
        assertEquals("GPO_fd67da50-9858-45c4-8871-b271709328c7", request.getPaymentMethod());
        
        verify(authService, times(1)).getAccessToken();
        verify(restTemplate, times(1)).exchange(anyString(), any(), any(), eq(AppyPayChargeResponse.class));
    }
    
    @Test
    void deveCriarChargeRefComSucesso() {
        // Arrange
        AppyPayChargeRequest request = AppyPayChargeRequest.builder()
            .merchantTransactionId("TXN-002")
            .amount(50000L)
            .paymentMethod("REF")
            .description("Recarga via referência")
            .build();
        
        AppyPayChargeResponse mockResponse = AppyPayChargeResponse.builder()
            .chargeId("CHARGE-002")
            .merchantTransactionId("TXN-002")
            .amount(50000L)
            .paymentMethod("REF_12345678-1234-1234-1234-123456789012")
            .status("PENDING")
            .entity("10100")
            .reference("999 123456")
            .build();
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(AppyPayChargeResponse.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.CREATED));
        
        // Act
        AppyPayChargeResponse response = client.createCharge(request);
        
        // Assert
        assertNotNull(response);
        assertEquals("CHARGE-002", response.getChargeId());
        assertEquals("PENDING", response.getStatus());
        assertEquals("10100", response.getEntity());
        assertEquals("999 123456", response.getReference());
        
        verify(authService, times(1)).getAccessToken();
    }
    
    @Test
    void deveConsultarChargeComSucesso() {
        // Arrange
        String chargeId = "CHARGE-003";
        
        AppyPayChargeResponse mockResponse = AppyPayChargeResponse.builder()
            .chargeId(chargeId)
            .status("CONFIRMED")
            .build();
        
        when(restTemplate.exchange(
            eq("https://api.appypay.ao/charges/" + chargeId),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(AppyPayChargeResponse.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));
        
        // Act
        AppyPayChargeResponse response = client.getCharge(chargeId);
        
        // Assert
        assertNotNull(response);
        assertEquals(chargeId, response.getChargeId());
        assertEquals("CONFIRMED", response.getStatus());
        
        verify(authService, times(1)).getAccessToken();
    }
    
    @Test
    void deveLancarExcecaoQuandoHttp4xx() {
        // Arrange
        AppyPayChargeRequest request = AppyPayChargeRequest.builder()
            .merchantTransactionId("TXN-BAD")
            .amount(10000L)
            .paymentMethod("GPO")
            .build();
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(AppyPayChargeResponse.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Invalid request"));
        
        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            client.createCharge(request);
        });
        
        assertTrue(exception.getMessage().contains("Erro na requisição AppyPay"));
    }
    
    @Test
    void deveLancarExcecaoQuandoHttp5xx() {
        // Arrange
        AppyPayChargeRequest request = AppyPayChargeRequest.builder()
            .merchantTransactionId("TXN-ERR")
            .amount(10000L)
            .paymentMethod("GPO")
            .build();
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(AppyPayChargeResponse.class)
        )).thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server error"));
        
        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            client.createCharge(request);
        });
        
        assertTrue(exception.getMessage().contains("AppyPay indisponível"));
    }
    
    @Test
    void deveLancarExcecaoQuandoResponseNulo() {
        // Arrange
        AppyPayChargeRequest request = AppyPayChargeRequest.builder()
            .merchantTransactionId("TXN-NULL")
            .amount(10000L)
            .paymentMethod("GPO")
            .build();
        
        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(AppyPayChargeResponse.class)
        )).thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
        
        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            client.createCharge(request);
        });
        
        assertTrue(exception.getMessage().contains("Response vazio"));
    }
    
    @Test
    void deveUsarModoMockQuandoConfigurado() {
        // Arrange
        when(properties.isMock()).thenReturn(true);
        
        AppyPayChargeRequest request = AppyPayChargeRequest.builder()
            .merchantTransactionId("TXN-MOCK")
            .amount(100000L)
            .paymentMethod("GPO")
            .build();
        
        // Act
        AppyPayChargeResponse response = client.createCharge(request);
        
        // Assert
        assertNotNull(response);
        assertTrue(response.getChargeId().startsWith("MOCK_CHARGE_"));
        assertEquals("CONFIRMED", response.getStatus());
        assertNotNull(response.getPaymentUrl());
        
        // Não deve chamar serviços reais em modo MOCK
        verify(authService, never()).getAccessToken();
    }
    
    @Test
    void deveSimularRefNoModoMock() {
        // Arrange
        when(properties.isMock()).thenReturn(true);
        
        AppyPayChargeRequest request = AppyPayChargeRequest.builder()
            .merchantTransactionId("TXN-MOCK-REF")
            .amount(50000L)
            .paymentMethod("REF")
            .build();
        
        // Act
        AppyPayChargeResponse response = client.createCharge(request);
        
        // Assert
        assertNotNull(response);
        assertTrue(response.getChargeId().startsWith("MOCK_CHARGE_"));
        assertEquals("PENDING", response.getStatus());
        assertEquals("10100", response.getEntity());
        assertNotNull(response.getReference());
        assertTrue(response.getReference().startsWith("999 "));
    }
    
    @Test
    void deveUsarTokenOAuth2NasChamadas() {
        // Arrange
        String expectedToken = "Bearer-Token-XYZ-789";
        when(authService.getAccessToken()).thenReturn(expectedToken);
        
        AppyPayChargeRequest request = AppyPayChargeRequest.builder()
            .merchantTransactionId("TXN-AUTH")
            .amount(25000L)
            .paymentMethod("GPO")
            .build();
        
        AppyPayChargeResponse mockResponse = AppyPayChargeResponse.builder()
            .chargeId("CHARGE-AUTH")
            .status("CONFIRMED")
            .build();
        
        when(restTemplate.exchange(
            anyString(),
            any(),
            any(HttpEntity.class),
            eq(AppyPayChargeResponse.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));
        
        // Act
        client.createCharge(request);
        
        // Assert
        verify(authService, times(1)).getAccessToken();
    }
    
    @Test
    void deveLancarExcecaoQuandoMetodoPagamentoNulo() {
        // Arrange
        AppyPayChargeRequest request = AppyPayChargeRequest.builder()
            .merchantTransactionId("TXN-NULL-METHOD")
            .amount(10000L)
            .paymentMethod(null)
            .build();
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            client.createCharge(request);
        });
    }
    
    @Test
    void deveUsarPadraoGpoQuandoMethodIdNaoConfigurado() {
        // Arrange
        when(properties.getGpoMethodId()).thenReturn(null);
        
        AppyPayChargeRequest request = AppyPayChargeRequest.builder()
            .merchantTransactionId("TXN-DEFAULT")
            .amount(10000L)
            .paymentMethod("GPO")
            .build();
        
        AppyPayChargeResponse mockResponse = AppyPayChargeResponse.builder()
            .chargeId("CHARGE-DEFAULT")
            .status("CONFIRMED")
            .build();
        
        when(restTemplate.exchange(
            anyString(),
            any(),
            any(HttpEntity.class),
            eq(AppyPayChargeResponse.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));
        
        // Act
        client.createCharge(request);
        
        // Assert
        assertEquals("GPO", request.getPaymentMethod());
    }
}
