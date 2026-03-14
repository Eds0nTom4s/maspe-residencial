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
import com.restaurante.model.enums.TipoUsuario;
import com.restaurante.repository.AtendenteRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.JwtTokenProvider;
import com.restaurante.util.PhoneNumberUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final AtendenteRepository atendenteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       AtendenteRepository atendenteRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.atendenteRepository = atendenteRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

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
     * Login para Atendente/Gerente/Admin.
     * Aceita username (User entity) ou telefone (Atendente entity — legado).
     */
    @Transactional(readOnly = true)
    public LoginAtendenteResponse loginAtendente(LoginAtendenteRequest request) {
        log.info("=== SERVICE: Processando login ===");

        // Validação mínima: username ou telefone é obrigatório
        if ((request.getUsername() == null || request.getUsername().isBlank())
                && (request.getTelefone() == null || request.getTelefone().isBlank())) {
            throw new BusinessException("Informe username ou telefone para fazer login");
        }

        try {
            // --- Autenticação via User (username) ---
            if (request.getUsername() != null && !request.getUsername().isBlank()) {
                return loginPorUsername(request);
            }
            // --- Autenticação via Atendente (telefone) — modo legado ---
            return loginPorTelefone(request);

        } catch (BusinessException e) {
            log.error("Erro de negócio no login: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Erro inesperado no login: {}", e.getMessage(), e);
            throw new BusinessException("Erro ao processar login");
        }
    }

    private LoginAtendenteResponse loginPorUsername(LoginAtendenteRequest request) {
        log.info("Autenticando via username: {}", request.getUsername());

        User user = userRepository.findByUsername(request.getUsername())
            .filter(u -> Boolean.TRUE.equals(u.getAtivo()))
            .orElseThrow(() -> {
                log.warn("Username não encontrado ou inactivo: {}", request.getUsername());
                return new BusinessException("Credenciais inválidas");
            });

        if (!passwordEncoder.matches(request.getSenha(), user.getPassword())) {
            log.warn("Senha incorrecta para username: {}", request.getUsername());
            throw new BusinessException("Credenciais inválidas");
        }

        // Roles separadas por vírgula para o JWT
        String rolesStr = user.getRoles().stream()
            .map(Enum::name)
            .reduce((a, b) -> a + "," + b)
            .orElse("ROLE_ATENDENTE");

        String token = jwtTokenProvider.generateToken(user.getUsername(), rolesStr);

        // Deriva TipoUsuario da role principal
        TipoUsuario tipoUsuario = user.getRoles().stream()
            .map(role -> {
                switch (role.name()) {
                    case "ROLE_ADMIN":     return TipoUsuario.ADMIN;
                    case "ROLE_GERENTE":   return TipoUsuario.GERENTE;
                    default:               return TipoUsuario.ATENDENTE;
                }
            })
            .findFirst()
            .orElse(TipoUsuario.ATENDENTE);

        log.info("Login por username concluído: {} ({})", user.getUsername(), tipoUsuario);

        return LoginAtendenteResponse.builder()
            .id(user.getId())
            .nome(user.getNomeCompleto() != null ? user.getNomeCompleto() : user.getUsername())
            .telefone(user.getTelefone())
            .email(user.getEmail())
            .tipoUsuario(tipoUsuario)
            .token(token)
            .expiresIn(jwtTokenProvider.getExpirationMs() / 1000L)
            .build();
    }

    private LoginAtendenteResponse loginPorTelefone(LoginAtendenteRequest request) {
        log.info("Autenticando via telefone: {}", request.getTelefone());

        String telefoneParaBuscar = request.getTelefone();
        try {
            telefoneParaBuscar = PhoneNumberUtil.normalize(request.getTelefone());
        } catch (IllegalArgumentException e) {
            log.warn("Erro ao normalizar telefone: {}", e.getMessage());
        }
        final String telefoneNorm = telefoneParaBuscar;

        Atendente atendente = atendenteRepository.findByTelefoneAndAtivoTrue(telefoneNorm)
            .orElseThrow(() -> {
                log.warn("Telefone não encontrado ou atendente inactivo: {}", telefoneNorm);
                return new BusinessException("Credenciais inválidas");
            });

        if (!passwordEncoder.matches(request.getSenha(), atendente.getSenha())) {
            log.warn("Senha incorrecta para telefone: {}", request.getTelefone());
            throw new BusinessException("Credenciais inválidas");
        }

        String role = "ROLE_" + atendente.getTipoUsuario().name();
        String token = jwtTokenProvider.generateToken(atendente.getTelefone(), role);

        log.info("Login por telefone concluído: {} ({})", atendente.getNome(), atendente.getTipoUsuario());

        return LoginAtendenteResponse.builder()
            .id(atendente.getId())
            .nome(atendente.getNome())
            .telefone(atendente.getTelefone())
            .email(atendente.getEmail())
            .tipoUsuario(atendente.getTipoUsuario())
            .token(token)
            .expiresIn(jwtTokenProvider.getExpirationMs() / 1000L)
            .build();
    }
}

