package com.restaurante.service;

import com.restaurante.dto.request.LoginRequest;
import com.restaurante.dto.request.RefreshTokenRequest;
import com.restaurante.dto.request.RegisterRequest;
import com.restaurante.dto.response.AuthResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.User;
import com.restaurante.repository.UserRepository;
import com.restaurante.security.JwtTokenProvider;
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
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Realiza login
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Tentativa de login para usuário: {}", request.getUsername());

        // Autentica
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Gera tokens
        String accessToken = jwtTokenProvider.generateToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(request.getUsername());

        // Atualiza último acesso
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("Usuário não encontrado"));
        user.atualizarUltimoAcesso();
        userRepository.save(user);

        log.info("Login realizado com sucesso para: {}", request.getUsername());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400000L) // 24 horas em ms
                .username(user.getUsername())
                .roles(user.getRoles())
                .build();
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
}
