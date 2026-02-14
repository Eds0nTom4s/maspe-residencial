package com.restaurante.notificacao.gateway.telcosms;

import com.restaurante.notificacao.gateway.SmsResponse;
import com.restaurante.notificacao.gateway.telcosms.dto.TelcoSmsRequest;
import com.restaurante.notificacao.gateway.telcosms.dto.TelcoSmsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes do TelcoSmsGateway
 * 
 * Cobertura:
 * - Normalização de números de telefone
 * - Envio de SMS real via API
 * - Modo MOCK para desenvolvimento
 * - Tratamento de erros de API
 * - Validação de resposta
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelcoSmsGatewayTest {

    @Mock
    private TelcoSmsProperties properties;
    
    @Mock
    private RestTemplate restTemplate;
    
    private TelcoSmsGateway gateway;
    
    @BeforeEach
    void setUp() {
        // Configuração padrão
        when(properties.getMock()).thenReturn(false);
        when(properties.getDebug()).thenReturn(false);
        when(properties.getBaseUrl()).thenReturn("https://www.telcosms.co.ao");
        when(properties.getApiKey()).thenReturn("test-api-key-123");
        
        gateway = new TelcoSmsGateway(properties, restTemplate);
    }
    
    @Test
    void deveEnviarSmsComSucesso() {
        // Arrange
        String phoneNumber = "+244925813939";
        String message = "Seu código OTP é: 1234";
        
        TelcoSmsResponse mockResponse = new TelcoSmsResponse();
        mockResponse.setStatus("success");
        mockResponse.setMessage("SMS enviado com sucesso");
        mockResponse.setMessageId("MSG-12345");
        
        when(restTemplate.exchange(
            eq("https://www.telcosms.co.ao/send_message"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(TelcoSmsResponse.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));
        
        // Act
        SmsResponse response = gateway.sendSms(phoneNumber, message);
        
        // Assert
        assertTrue(response.isSuccess());
        assertEquals("SMS enviado com sucesso", response.getMessage());
        assertEquals("MSG-12345", response.getMessageId());
        
        // Verifica que a chamada foi feita com número normalizado
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), any(), captor.capture(), eq(TelcoSmsResponse.class));
        
        TelcoSmsRequest sentRequest = (TelcoSmsRequest) captor.getValue().getBody();
        assertEquals("244925813939", sentRequest.getMessage().getPhoneNumber());
    }
    
    @Test
    void deveNormalizarTelefoneComZeroInicial() {
        // Arrange
        String phoneNumber = "0925813939"; // Formato local de Angola
        String message = "Teste de normalização";
        
        TelcoSmsResponse mockResponse = new TelcoSmsResponse();
        mockResponse.setStatus("success");
        mockResponse.setMessageId("MSG-NORM-1");
        
        when(restTemplate.exchange(
            anyString(),
            any(),
            any(HttpEntity.class),
            eq(TelcoSmsResponse.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));
        
        // Act
        gateway.sendSms(phoneNumber, message);
        
        // Assert
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), any(), captor.capture(), eq(TelcoSmsResponse.class));
        
        TelcoSmsRequest sentRequest = (TelcoSmsRequest) captor.getValue().getBody();
        assertEquals("244925813939", sentRequest.getMessage().getPhoneNumber());
    }
    
    @Test
    void deveNormalizarTelefoneComEspacosEHifens() {
        // Arrange
        String phoneNumber = "+244 925-813-939"; // Formato formatado
        String message = "Teste";
        
        TelcoSmsResponse mockResponse = new TelcoSmsResponse();
        mockResponse.setStatus("success");
        mockResponse.setMessageId("MSG-NORM-2");
        
        when(restTemplate.exchange(
            anyString(),
            any(),
            any(HttpEntity.class),
            eq(TelcoSmsResponse.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));
        
        // Act
        gateway.sendSms(phoneNumber, message);
        
        // Assert
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), any(), captor.capture(), eq(TelcoSmsResponse.class));
        
        TelcoSmsRequest sentRequest = (TelcoSmsRequest) captor.getValue().getBody();
        assertEquals("244925813939", sentRequest.getMessage().getPhoneNumber());
    }
    
    @Test
    void deveAdicionarCodigoPaisQuandoAusente() {
        // Arrange
        String phoneNumber = "925813939"; // Sem código do país
        String message = "Teste";
        
        TelcoSmsResponse mockResponse = new TelcoSmsResponse();
        mockResponse.setStatus("success");
        mockResponse.setMessageId("MSG-NORM-3");
        
        when(restTemplate.exchange(
            anyString(),
            any(),
            any(HttpEntity.class),
            eq(TelcoSmsResponse.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));
        
        // Act
        gateway.sendSms(phoneNumber, message);
        
        // Assert
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), any(), captor.capture(), eq(TelcoSmsResponse.class));
        
        TelcoSmsRequest sentRequest = (TelcoSmsRequest) captor.getValue().getBody();
        assertEquals("244925813939", sentRequest.getMessage().getPhoneNumber());
    }
    
    @Test
    void deveLancarExcecaoQuandoNumeroVazio() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            gateway.sendSms("", "Mensagem");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            gateway.sendSms(null, "Mensagem");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            gateway.sendSms("   ", "Mensagem");
        });
    }
    
    @Test
    void deveRetornarErroQuandoApiRetornaSucessoFalso() {
        // Arrange
        String phoneNumber = "+244925813939";
        String message = "Teste falha";
        
        TelcoSmsResponse mockResponse = new TelcoSmsResponse();
        mockResponse.setStatus("error");
        mockResponse.setMessage("Créditos insuficientes");
        mockResponse.setErrorCode("INSUFFICIENT_CREDITS");
        
        when(restTemplate.exchange(
            anyString(),
            any(),
            any(HttpEntity.class),
            eq(TelcoSmsResponse.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));
        
        // Act
        SmsResponse response = gateway.sendSms(phoneNumber, message);
        
        // Assert
        assertFalse(response.isSuccess());
        assertEquals("Créditos insuficientes", response.getMessage());
        assertEquals("INSUFFICIENT_CREDITS", response.getErrorCode());
    }
    
    @Test
    void deveRetornarErroQuandoExcecaoOcorre() {
        // Arrange
        String phoneNumber = "+244925813939";
        String message = "Teste exceção";
        
        when(restTemplate.exchange(
            anyString(),
            any(),
            any(HttpEntity.class),
            eq(TelcoSmsResponse.class)
        )).thenThrow(new RestClientException("Connection timeout"));
        
        // Act
        SmsResponse response = gateway.sendSms(phoneNumber, message);
        
        // Assert
        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("Connection timeout"));
        assertEquals("SEND_FAILED", response.getErrorCode());
    }
    
    @Test
    void deveUsarModoMockQuandoConfigurado() {
        // Arrange
        when(properties.getMock()).thenReturn(true);
        
        String phoneNumber = "+244925813939";
        String message = "Teste MOCK";
        
        // Act
        SmsResponse response = gateway.sendSms(phoneNumber, message);
        
        // Assert
        assertTrue(response.isSuccess());
        assertTrue(response.getMessage().contains("modo mock"));
        assertTrue(response.getMessageId().startsWith("MOCK-"));
    }
    
    @Test
    void deveRetornarTrueParaIsMockModeQuandoMockAtivado() {
        // Arrange
        when(properties.getMock()).thenReturn(true);
        
        // Act & Assert
        assertTrue(gateway.isMockMode());
    }
    
    @Test
    void deveRetornarFalseParaIsMockModeQuandoMockDesativado() {
        // Arrange
        when(properties.getMock()).thenReturn(false);
        
        // Act & Assert
        assertFalse(gateway.isMockMode());
    }
    
    @Test
    void deveRetornarNomeDoProvedor() {
        // Act & Assert
        assertEquals("TelcoSMS", gateway.getProviderName());
    }
    
    @Test
    void deveEnviarApiKeyNoHeader() {
        // Arrange
        String phoneNumber = "+244925813939";
        String message = "Teste API Key";
        
        TelcoSmsResponse mockResponse = new TelcoSmsResponse();
        mockResponse.setStatus("success");
        mockResponse.setMessageId("MSG-KEY-1");
        
        when(restTemplate.exchange(
            anyString(),
            any(),
            any(HttpEntity.class),
            eq(TelcoSmsResponse.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));
        
        // Act
        gateway.sendSms(phoneNumber, message);
        
        // Assert
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), any(), captor.capture(), eq(TelcoSmsResponse.class));
        
        TelcoSmsRequest sentRequest = (TelcoSmsRequest) captor.getValue().getBody();
        assertEquals("test-api-key-123", sentRequest.getMessage().getApiKeyApp());
    }
    
    @Test
    void deveUsarContentTypeApplicationJson() {
        // Arrange
        String phoneNumber = "+244925813939";
        String message = "Teste Content-Type";
        
        TelcoSmsResponse mockResponse = new TelcoSmsResponse();
        mockResponse.setStatus("success");
        mockResponse.setMessageId("MSG-CT-1");
        
        when(restTemplate.exchange(
            anyString(),
            any(),
            any(HttpEntity.class),
            eq(TelcoSmsResponse.class)
        )).thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));
        
        // Act
        gateway.sendSms(phoneNumber, message);
        
        // Assert
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), any(), captor.capture(), eq(TelcoSmsResponse.class));
        
        HttpHeaders headers = captor.getValue().getHeaders();
        assertEquals(MediaType.APPLICATION_JSON, headers.getContentType());
    }
    
    @Test
    void deveRetornarErroQuandoResponseNulo() {
        // Arrange
        String phoneNumber = "+244925813939";
        String message = "Teste response nulo";
        
        when(restTemplate.exchange(
            anyString(),
            any(),
            any(HttpEntity.class),
            eq(TelcoSmsResponse.class)
        )).thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
        
        // Act
        SmsResponse response = gateway.sendSms(phoneNumber, message);
        
        // Assert
        assertFalse(response.isSuccess());
        assertEquals("Resposta nula", response.getMessage());
    }
}
