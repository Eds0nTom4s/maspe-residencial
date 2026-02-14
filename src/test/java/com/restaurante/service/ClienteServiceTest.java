package com.restaurante.service;

import com.restaurante.dto.request.SolicitarOtpRequest;
import com.restaurante.dto.request.ValidarOtpRequest;
import com.restaurante.dto.response.ClienteResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Cliente;
import com.restaurante.notificacao.service.NotificacaoService;
import com.restaurante.repository.ClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para ClienteService (Autenticação OTP)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClienteService - Testes de Autenticação OTP")
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private NotificacaoService notificacaoService;

    @InjectMocks
    private ClienteService clienteService;

    private SolicitarOtpRequest solicitarOtpRequest;
    private ValidarOtpRequest validarOtpRequest;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        solicitarOtpRequest = SolicitarOtpRequest.builder()
                .telefone("+244925813939")
                .build();

        validarOtpRequest = ValidarOtpRequest.builder()
                .telefone("+244925813939")
                .codigo("1234")
                .build();

        cliente = Cliente.builder()
                .telefone("+244925813939")
                .telefoneVerificado(false)
                .ativo(true)
                .otpCode("1234")
                .otpExpiration(LocalDateTime.now().plusMinutes(5))
                .build();
    }

    @Test
    @DisplayName("Deve solicitar OTP para cliente existente")
    void deveSolicitarOtpParaClienteExistente() {
        // Arrange
        when(clienteRepository.findByTelefone(anyString())).thenReturn(Optional.of(cliente));
        when(clienteRepository.save(any(Cliente.class))).thenReturn(cliente);
        when(notificacaoService.enviarOtp(anyString(), anyString())).thenReturn(true);

        // Act
        clienteService.solicitarOtp(solicitarOtpRequest);

        // Assert
        ArgumentCaptor<Cliente> clienteCaptor = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepository, times(1)).save(clienteCaptor.capture());
        
        Cliente clienteSalvo = clienteCaptor.getValue();
        assertNotNull(clienteSalvo.getOtpCode());
        assertEquals(4, clienteSalvo.getOtpCode().length()); // OTP deve ter 4 dígitos
        assertNotNull(clienteSalvo.getOtpExpiration());
        
        verify(notificacaoService, times(1)).enviarOtp(eq("+244925813939"), anyString());
    }

    @Test
    @DisplayName("Deve criar novo cliente ao solicitar OTP pela primeira vez")
    void deveCriarNovoClienteAoSolicitarOtpPelaPrimeiraVez() {
        // Arrange
        when(clienteRepository.findByTelefone(anyString())).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenReturn(cliente);
        when(notificacaoService.enviarOtp(anyString(), anyString())).thenReturn(true);

        // Act
        clienteService.solicitarOtp(solicitarOtpRequest);

        // Assert
        verify(clienteRepository, times(2)).save(any(Cliente.class)); // 1 para criar + 1 para salvar OTP
        verify(notificacaoService, times(1)).enviarOtp(anyString(), anyString());
    }

    @Test
    @DisplayName("Deve gerar OTP de 4 dígitos numéricos")
    void deveGerarOtpDe4DigitosNumericos() {
        // Arrange
        when(clienteRepository.findByTelefone(anyString())).thenReturn(Optional.of(cliente));
        when(clienteRepository.save(any(Cliente.class))).thenReturn(cliente);
        when(notificacaoService.enviarOtp(anyString(), anyString())).thenReturn(true);

        // Act
        clienteService.solicitarOtp(solicitarOtpRequest);

        // Assert
        ArgumentCaptor<Cliente> clienteCaptor = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepository).save(clienteCaptor.capture());
        
        String otp = clienteCaptor.getValue().getOtpCode();
        assertNotNull(otp);
        assertEquals(4, otp.length());
        assertTrue(otp.matches("\\d{4}"), "OTP deve conter apenas dígitos");
    }

    @Test
    @DisplayName("Deve continuar mesmo se envio de SMS falhar")
    void deveContinuarMesmoSeEnvioDeSMSFalhar() {
        // Arrange
        when(clienteRepository.findByTelefone(anyString())).thenReturn(Optional.of(cliente));
        when(clienteRepository.save(any(Cliente.class))).thenReturn(cliente);
        when(notificacaoService.enviarOtp(anyString(), anyString())).thenReturn(false);

        // Act & Assert - não deve lançar exceção
        assertDoesNotThrow(() -> clienteService.solicitarOtp(solicitarOtpRequest));
        verify(clienteRepository, times(1)).save(any(Cliente.class));
    }

    @Test
    @DisplayName("Deve validar OTP correto com sucesso")
    void deveValidarOtpCorretoComSucesso() {
        // Arrange
        when(clienteRepository.findByTelefone(anyString())).thenReturn(Optional.of(cliente));
        when(clienteRepository.save(any(Cliente.class))).thenReturn(cliente);

        // Act
        ClienteResponse response = clienteService.validarOtp(validarOtpRequest);

        // Assert
        assertNotNull(response);
        assertEquals(cliente.getId(), response.getId());
        assertEquals(cliente.getTelefone(), response.getTelefone());
        assertTrue(response.getTelefoneVerificado());
        
        ArgumentCaptor<Cliente> clienteCaptor = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepository).save(clienteCaptor.capture());
        
        Cliente clienteSalvo = clienteCaptor.getValue();
        assertNull(clienteSalvo.getOtpCode());
        assertNull(clienteSalvo.getOtpExpiration());
        assertTrue(clienteSalvo.getTelefoneVerificado());
    }

    @Test
    @DisplayName("Deve rejeitar OTP incorreto")
    void deveRejeitarOtpIncorreto() {
        // Arrange
        cliente.setOtpCode("5678"); // OTP diferente
        when(clienteRepository.findByTelefone(anyString())).thenReturn(Optional.of(cliente));

        // Act & Assert
        assertThrows(BusinessException.class, () -> {
            clienteService.validarOtp(validarOtpRequest);
        });
        
        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    @Test
    @DisplayName("Deve rejeitar OTP expirado")
    void deveRejeitarOtpExpirado() {
        // Arrange
        cliente.setOtpExpiration(LocalDateTime.now().minusMinutes(1)); // Expirado
        when(clienteRepository.findByTelefone(anyString())).thenReturn(Optional.of(cliente));

        // Act & Assert
        assertThrows(BusinessException.class, () -> {
            clienteService.validarOtp(validarOtpRequest);
        });
        
        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    @Test
    @DisplayName("Deve lançar exceção ao validar OTP de cliente inexistente")
    void deveLancarExcecaoAoValidarOtpDeClienteInexistente() {
        // Arrange
        when(clienteRepository.findByTelefone(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            clienteService.validarOtp(validarOtpRequest);
        });
    }

    @Test
    @DisplayName("Deve buscar cliente por ID")
    void deveBuscarClientePorId() {
        // Arrange
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));

        // Act
        Cliente resultado = clienteService.buscarPorId(1L);

        // Assert
        assertNotNull(resultado);
        assertEquals(cliente.getId(), resultado.getId());
        verify(clienteRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Deve buscar cliente por telefone")
    void deveBuscarClientePorTelefone() {
        // Arrange
        when(clienteRepository.findByTelefone(anyString())).thenReturn(Optional.of(cliente));

        // Act
        Cliente resultado = clienteService.buscarPorTelefone("+244925813939");

        // Assert
        assertNotNull(resultado);
        assertEquals(cliente.getTelefone(), resultado.getTelefone());
        verify(clienteRepository, times(1)).findByTelefone("+244925813939");
    }
}
