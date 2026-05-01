package com.restaurante.store.security;

import com.restaurante.model.entity.SocioVinculo;
import com.restaurante.repository.ClienteRepository;
import com.restaurante.repository.SocioVinculoRepository;
import com.restaurante.model.entity.Cliente;
import com.restaurante.model.enums.TipoUsuario;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * SocioAuthFilter — Filtro de autenticação para o modo sócio da Loja Oficial GDSE.
 *
 * <p>Intercepta todos os requests para {@code /store/**} e valida o JWT
 * emitido pelo Associagest. Se válido:
 * <ol>
 *   <li>Extrai identidade mínima: {@code socioId}, {@code nome}, {@code telefone}, {@code email}</li>
 *   <li>Cria ou actualiza o {@link SocioVinculo} (tabela de mapeamento)</li>
 *   <li>Garante que o {@link Cliente} existe no motor (cria se não existir, pelo telefone)</li>
 *   <li>Injeta um {@link UsernamePasswordAuthenticationToken} com {@code telefone} como principal</li>
 * </ol>
 *
 * <p>O token é lido do header {@code Authorization: Bearer <token>} ou,
 * alternativamente, do header {@code X-Socio-Token: <token>}.
 *
 * <p><b>Claims esperados no JWT Associagest:</b>
 * <pre>
 * {
 *   "sub": "socio-uuid-123",        // socioId único
 *   "nome": "João Baptista",        // nome completo
 *   "telefone": "+244923456789",    // telefone (obrigatório)
 *   "email": "joao@email.com",      // email (opcional)
 *   "iss": "associagest"            // issuer (validado se configurado)
 * }
 * </pre>
 *
 * <p><b>Configuração:</b>
 * <pre>
 * store.socio.jwt.secret=&lt;shared-secret-base64&gt;
 * store.socio.jwt.issuer=associagest   # opcional
 * </pre>
 */
@Component
@Slf4j
public class SocioAuthFilter extends OncePerRequestFilter {

    private static final String STORE_PATH_PREFIX = "/store/";
    private static final String CLAIM_NOME = "nome";
    private static final String CLAIM_TELEFONE = "telefone";
    private static final String CLAIM_EMAIL = "email";

    @Value("${store.socio.jwt.secret}")
    private String socioJwtSecret;

    @Value("${store.socio.jwt.issuer:associagest}")
    private String expectedIssuer;

    private final SocioVinculoRepository socioVinculoRepository;
    private final ClienteRepository clienteRepository;

    public SocioAuthFilter(SocioVinculoRepository socioVinculoRepository,
                           ClienteRepository clienteRepository) {
        this.socioVinculoRepository = socioVinculoRepository;
        this.clienteRepository = clienteRepository;
    }

    // ── Activação ─────────────────────────────────────────────────────────────

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Este filtro só actua no namespace da loja
        // O catálogo público também passa aqui, mas sem token não autentica (e está permitido)
        return !path.contains(STORE_PATH_PREFIX);
    }

    // ── Lógica principal ──────────────────────────────────────────────────────

    @Override
    @Transactional
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Se já autenticado por outro filtro, deixa passar
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }

        String token = extrairToken(request);

        if (!StringUtils.hasText(token)) {
            // Sem token — deixa continuar (endpoints públicos não precisam)
            chain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = validarEExtrairClaims(token);

            String socioId = claims.getSubject();
            String nome = claims.get(CLAIM_NOME, String.class);
            String telefone = claims.get(CLAIM_TELEFONE, String.class);
            String email = claims.get(CLAIM_EMAIL, String.class);

            if (!StringUtils.hasText(socioId) || !StringUtils.hasText(telefone)) {
                log.warn("[SocioAuthFilter] JWT inválido: sub ou telefone em falta. Path={}", request.getRequestURI());
                chain.doFilter(request, response);
                return;
            }

            // Garantir vínculo e Cliente
            resolverIdentidade(socioId, nome, telefone, email);

            // Injectar autenticação — usa telefone como principal (compatível com motor existente)
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    telefone,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);

            log.debug("[SocioAuthFilter] Sócio autenticado: socioId={}, telefone={}", socioId, telefone);

        } catch (ExpiredJwtException ex) {
            log.warn("[SocioAuthFilter] Token Associagest expirado. Path={}", request.getRequestURI());
        } catch (JwtException ex) {
            log.warn("[SocioAuthFilter] Token Associagest inválido: {}. Path={}", ex.getMessage(), request.getRequestURI());
        } catch (Exception ex) {
            log.error("[SocioAuthFilter] Erro inesperado ao processar token: {}", ex.getMessage(), ex);
        }

        chain.doFilter(request, response);
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    /**
     * Extrai o JWT do header Authorization (Bearer) ou do header X-Socio-Token.
     */
    private String extrairToken(HttpServletRequest request) {
        // Prioridade 1: header padrão
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        // Prioridade 2: header dedicado
        String xToken = request.getHeader("X-Socio-Token");
        if (StringUtils.hasText(xToken)) {
            return xToken;
        }
        return null;
    }

    /**
     * Valida a assinatura e extrai os claims do JWT Associagest.
     */
    private Claims validarEExtrairClaims(String token) {
        return Jwts.parser()
                .verifyWith(getChaveAssinatura())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Cria ou actualiza SocioVinculo e garante existência do Cliente no motor.
     */
    private void resolverIdentidade(String socioId, String nome, String telefone, String email) {
        // 1. Criar ou actualizar SocioVinculo
        SocioVinculo vinculo = socioVinculoRepository.findBySocioId(socioId)
                .orElseGet(() -> {
                    log.info("[SocioAuthFilter] Primeiro acesso do sócio: socioId={}, telefone={}", socioId, telefone);
                    return SocioVinculo.builder()
                            .socioId(socioId)
                            .nome(nome)
                            .telefone(telefone)
                            .email(email)
                            .build();
                });

        vinculo.actualizarDados(nome, email);
        socioVinculoRepository.save(vinculo);

        // 2. Garantir que o Cliente existe no motor (pelo telefone)
        if (!clienteRepository.findByTelefone(telefone).isPresent()) {
            log.info("[SocioAuthFilter] Criando Cliente no motor para sócio: telefone={}", telefone);
            Cliente cliente = Cliente.builder()
                    .telefone(telefone)
                    .nome(nome)
                    .telefoneVerificado(true)  // Verificado pelo Associagest
                    .tipoUsuario(TipoUsuario.CLIENTE)
                    .ativo(true)
                    .build();
            clienteRepository.save(cliente);
        } else {
            // Actualizar nome se mudou
            clienteRepository.findByTelefone(telefone).ifPresent(cliente -> {
                if (nome != null && !nome.isBlank() && !nome.equals(cliente.getNome())) {
                    cliente.setNome(nome);
                    clienteRepository.save(cliente);
                }
            });
        }
    }

    /**
     * Deriva a chave HMAC-SHA256 a partir do segredo configurado.
     */
    private SecretKey getChaveAssinatura() {
        byte[] keyBytes = socioJwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
