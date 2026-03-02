package com.restaurante.service;

import com.restaurante.dto.request.LoginAtendenteRequest;
import com.restaurante.dto.request.LoginRequest;
import com.restaurante.dto.request.RefreshTokenRequest;
import com.restaurante.dto.request.RegisterRequest;
import com.restaurante.dto.response.AuthResponse;
import com.restaurante.dto.response.LoginAtendenteResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Atendente;
import com.restaurante.model.entity.User;
import com.restaurante.repository.AtendenteRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.JwtTokenProvider;
import com.restaurante.util.PhoneNumberUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service para autenticação e autorização
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final AtendenteRepository atendenteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Realiza login
     * 
     * ⚠️ SEGURANÇA: Mensagens genéricas para não vazar informação sobre usuários existentes
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Tentativa de login JWT - Input: {}", request.getUsername());

        try {
            // Autentica
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Gera tokens usando o username do principal autenticado
        String authenticatedUsername = authentication.getName();
        String accessToken = jwtTokenProvider.generateToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authenticatedUsername);

        // Busca usuário pelo username real (não pelo input que pode ser telefone)
        User user = userRepository.findByUsername(authenticatedUsername)
                .orElseThrow(() -> new BusinessException("Credenciais inválidas"));
        user.atualizarUltimoAcesso();
        userRepository.save(user);

            log.info("✅ Login JWT bem-sucedido - Username: {}, Roles: {}", user.getUsername(), user.getRoles());

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(3600000L) // 1 hora em ms
                    .username(user.getUsername())
                    .roles(user.getRoles())
                    .build();
        } catch (Exception e) {
            // ⚠️ SEGURANÇA: Log interno detalhado, mas mensagem genérica para usuário
            log.error("❌ Falha no login JWT - Input: {} - Tipo: {} - Mensagem: {}", 
                     request.getUsername(), e.getClass().getSimpleName(), e.getMessage(), e);
            throw new BusinessException("Credenciais inválidas");
        }
    }

    /**
     * Registra novo usuário
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Tentativa de registro para usuário: {}", request.getUsername());

        // Valida username único
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username já está em uso");
        }

        // Valida email único
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email já está em uso");
        }

        // Cria usuário
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .nomeCompleto(request.getNomeCompleto())
                .telefone(request.getTelefone())
                .roles(request.getRoles())
                .ativo(true)
                .build();

        user = userRepository.save(user);

        // Gera tokens
        String accessToken = jwtTokenProvider.generateToken(user.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        log.info("Usuário registrado com sucesso: {}", user.getUsername());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400000L)
                .username(user.getUsername())
                .roles(user.getRoles())
                .build();
    }

    /**
     * Renova access token usando refresh token
     */
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        // Valida refresh token
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException("Refresh token inválido ou expirado");
        }

        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException("Token fornecido não é um refresh token");
        }

        // Extrai username
        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);

        // Busca usuário
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado"));

        if (!user.getAtivo()) {
            throw new BusinessException("Usuário inativo");
        }

        // Gera novo access token
        String newAccessToken = jwtTokenProvider.generateToken(username);

        log.info("Token renovado para usuário: {}", username);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken) // Mantém o mesmo refresh token
                .tokenType("Bearer")
                .expiresIn(86400000L)
                .username(user.getUsername())
                .roles(user.getRoles())
                .build();
    }

    /**
     * Obtém usuário autenticado atual
     */
    public User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username).orElse(null);
    }
    
    /**
     * Login para Atendente/Gerente usando telefone + senha
     */
    @Transactional(readOnly = true)
    public LoginAtendenteResponse loginAtendente(LoginAtendenteRequest request) {
        log.info("=== SERVICE: Processando login Atendente ===");
        log.info("Telefone recebido: {}", request.getTelefone());
        log.info("Senha length: {}", request.getSenha() != null ? request.getSenha().length() : 0);
        
        try {
            // Busca atendente por telefone
            log.debug("Buscando atendente no banco de dados...");
            
            // Normaliza o telefone antes de buscar
            String telefoneParaBuscar = request.getTelefone();
            try {
                telefoneParaBuscar = PhoneNumberUtil.normalize(request.getTelefone());
                log.info("Telefone normalizado: {} -> {}", request.getTelefone(), telefoneParaBuscar);
            } catch (IllegalArgumentException e) {
                log.warn("Erro ao normalizar telefone: {} - {}", request.getTelefone(), e.getMessage());
            }
            
            final String telefoneNormalizado = telefoneParaBuscar;
            
            Atendente atendente = atendenteRepository.findByTelefoneAndAtivoTrue(telefoneNormalizado)
                .orElseThrow(() -> {
                    log.warn("Telefone não encontrado ou atendente inativo: {} (normalizado: {})", 
                             request.getTelefone(), telefoneNormalizado);
                    return new BusinessException("Telefone ou senha inválidos");
                });
            
            log.info("Atendente encontrado: ID={}, Nome={}, Tipo={}", 
                     atendente.getId(), atendente.getNome(), atendente.getTipoUsuario());
        
            // Valida senha
            log.debug("Validando senha...");
            if (!passwordEncoder.matches(request.getSenha(), atendente.getSenha())) {
                log.warn("Senha incorreta para telefone: {}", request.getTelefone());
                throw new BusinessException("Telefone ou senha inválidos");
            }
            log.info("Senha validada com sucesso");
            
            // Gera token JWT com role
            log.debug("Gerando token JWT...");
            String role = "ROLE_" + atendente.getTipoUsuario().name();
            log.info("Role atribuída: {}", role);
            
            String token = jwtTokenProvider.generateToken(
                atendente.getTelefone(),
                role
            );
            
            log.info("Login atendente concluído com sucesso: {} ({})", atendente.getNome(), atendente.getTipoUsuario());
            log.info("Token gerado com sucesso (length: {})", token != null ? token.length() : 0);
            
            return LoginAtendenteResponse.builder()
                .id(atendente.getId())
                .nome(atendente.getNome())
                .telefone(atendente.getTelefone())
                .email(atendente.getEmail())
                .tipoUsuario(atendente.getTipoUsuario())
                .token(token)
                .expiresIn(86400L) // 24 horas em segundos
                .build();
        } catch (BusinessException e) {
            log.error("Erro de negócio no login atendente: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Erro inesperado no login atendente para telefone: {}", request.getTelefone(), e);
            log.error("Tipo de erro: {}", e.getClass().getName());
            throw new BusinessException("Erro ao processar login");
        }
    }
}

