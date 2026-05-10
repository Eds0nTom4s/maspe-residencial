package com.restaurante.service;

import com.restaurante.dto.request.AtualizarUsuarioRequest;
import com.restaurante.dto.request.CriarUsuarioRequest;
import com.restaurante.dto.request.AlterarSenhaAdminRequest;
import com.restaurante.dto.response.UserResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.UnidadeAtendimento;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.Role;
import com.restaurante.repository.UnidadeAtendimentoRepository;
import com.restaurante.repository.UserRepository;
import jakarta.persistence.criteria.Join;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaFinanceiraService auditoriaService;
    private final InstituicaoService instituicaoService;
    private final UnidadeAtendimentoRepository unidadeAtendimentoRepository;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuditoriaFinanceiraService auditoriaService, InstituicaoService instituicaoService,
                       UnidadeAtendimentoRepository unidadeAtendimentoRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.instituicaoService = instituicaoService;
        this.unidadeAtendimentoRepository = unidadeAtendimentoRepository;
    }

    // ── Leitura ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<UserResponse> listarTodos(Pageable pageable) {
        return listarTodos(pageable, null, null, null);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listarTodos(Pageable pageable, Role role, Boolean ativo, String busca) {
        log.info("Listando usuários (paginado) role={} ativo={} busca={}", role, ativo, busca);

        Specification<User> spec = Specification.where(null);

        if (role != null) {
            spec = spec.and((root, query, criteriaBuilder) -> {
                if (query != null) {
                    query.distinct(true);
                }
                Join<User, Role> roles = root.join("roles");
                return criteriaBuilder.equal(roles, role);
            });
        }

        if (ativo != null) {
            spec = spec.and((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("ativo"), ativo));
        }

        if (busca != null && !busca.isBlank()) {
            String termo = "%" + busca.trim().toLowerCase() + "%";
            spec = spec.and((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("username")), termo),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("nomeCompleto")), termo),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), termo),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("telefone")), termo)
            ));
        }

        return userRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse buscarPorId(Long id) {
        log.info("Buscando usuário por ID: {}", id);
        return toResponse(buscarEntidade(id));
    }

    /**
     * Retorna mapa de roles disponíveis com as suas descrições legíveis.
     */
    @Transactional(readOnly = true)
    public Map<String, String> listarPermissoes() {
        Map<String, String> permissoes = new LinkedHashMap<>();
        for (Role role : Role.values()) {
            permissoes.put(role.name(), role.getDescricao());
        }
        return permissoes;
    }

    /**
     * Retorna lista de logs de acções do utilizador.
     * TODO: Implementar tabela de auditoria por utilizador quando disponível.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarLogs(Long id) {
        buscarEntidade(id); // valida existência
        log.info("Listando logs do usuário ID: {} (sem histórico disponível ainda)", id);
        return List.of();
    }

    // ── Escrita ───────────────────────────────────────────────────────────────

    /**
     * Cria novo utilizador. Username e telefone devem ser únicos; email é opcional.
     */
    @Transactional
    public UserResponse criar(CriarUsuarioRequest request) {
        log.info("Criando utilizador: {}", request.getUsername());

        // Validação obrigatória por OTP administrativo
        instituicaoService.validarOtpAutorizacao(request.getOtpAutorizacao());

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username já está em uso: " + request.getUsername());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank() && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email já está em uso: " + request.getEmail());
        }
        if (userRepository.existsByTelefone(request.getTelefone())) {
            throw new BusinessException("Telefone já está em uso: " + request.getTelefone());
        }

        UnidadeAtendimento unidadeAtendimento = resolverUnidade(request.getUnidadeAtendimentoId());

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getSenha()))
                .email(normalizarTextoOpcional(request.getEmail()))
                .nomeCompleto(request.getNomeCompleto())
                .telefone(request.getTelefone())
                .unidadeAtendimento(unidadeAtendimento)
                .roles(request.getRoles())
                .ativo(true)
                .build();

        return toResponse(userRepository.save(user));
    }

    /**
     * Actualiza dados de um utilizador (email, nome, telefone, roles).
     */
    @Transactional
    public UserResponse atualizar(Long id, AtualizarUsuarioRequest request) {
        log.info("Atualizando usuário ID: {}", id);
        User user = buscarEntidade(id);

        if (request.getEmail() != null) {
            String email = normalizarTextoOpcional(request.getEmail());
            if (email != null && !email.equals(user.getEmail())) {
                if (userRepository.existsByEmail(email)) {
                    throw new BusinessException("Email já está em uso: " + email);
                }
            }
            user.setEmail(email);
        }
        if (request.getNomeCompleto() != null) {
            user.setNomeCompleto(request.getNomeCompleto());
        }
        if (request.getTelefone() != null && !request.getTelefone().equals(user.getTelefone())) {
            if (userRepository.existsByTelefone(request.getTelefone())) {
                throw new BusinessException("Telefone já está em uso: " + request.getTelefone());
            }
            user.setTelefone(request.getTelefone());
        }
        if (request.getUnidadeAtendimentoId() != null) {
            user.setUnidadeAtendimento(resolverUnidade(request.getUnidadeAtendimentoId()));
        }
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            user.getRoles().clear();
            user.getRoles().addAll(request.getRoles());
        }

        return toResponse(userRepository.save(user));
    }

    /**
     * Desactiva utilizador (soft delete).
     */
    @Transactional
    public void desativar(Long id) {
        log.info("Desativando usuário ID: {}", id);
        User user = buscarEntidade(id);
        user.setAtivo(false);
        userRepository.save(user);
    }

    /**
     * Reactiva utilizador previamente desactivado.
     */
    @Transactional
    public UserResponse ativar(Long id) {
        log.info("Ativando usuário ID: {}", id);
        User user = buscarEntidade(id);
        user.setAtivo(true);
        return toResponse(userRepository.save(user));
    }

    /**
     * Altera senha de utilizador (operação ADMIN — sem verificar senha antiga).
     */
    @Transactional
    public void alterarSenha(Long id, AlterarSenhaAdminRequest request) {
        log.info("Alterando senha do usuário ID: {} (acção ADMIN)", id);
        User user = buscarEntidade(id);
        user.setPassword(passwordEncoder.encode(request.getNovaSenha()));
        userRepository.save(user);
    }

    /**
     * Reseta a senha de um utilizador e gera uma senha temporária.
     * O ADMIN recebe a senha temporária em texto claro para comunicar ao utilizador.
     * O utilizador deverá alterar a senha após o primeiro acesso.
     *
     * @param username username do utilizador a redefinir
     * @return mapa com {"senhaTemporária": "...", "username": "..."}
     */
    @Transactional
    public Map<String, String> resetSenha(Long id) {
        log.info("Reset de senha solicitado para usuário ID: {}", id);

        User user = buscarEntidade(id);

        // Gera senha temporária: prefixo legível + 8 chars aleatórios
        String charsPermitidos = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        java.util.Random random = new java.security.SecureRandom();
        StringBuilder senhaTemp = new StringBuilder("Tmp@");
        for (int i = 0; i < 8; i++) {
            senhaTemp.append(charsPermitidos.charAt(random.nextInt(charsPermitidos.length())));
        }
        String senhaTempStr = senhaTemp.toString();

        user.setPassword(passwordEncoder.encode(senhaTempStr));
        userRepository.save(user);

        // Registrar Auditoria
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            String operadorNome = (auth != null) ? auth.getName() : "system";
            String operadorRole = (auth != null && !auth.getAuthorities().isEmpty()) 
                    ? auth.getAuthorities().iterator().next().getAuthority() : "SYSTEM";
            
            auditoriaService.registrarResetSenha(user.getId(), user.getUsername(), operadorNome, operadorRole);
        } catch (Exception e) {
            log.error("Erro ao registrar auditoria de reset de senha", e);
        }

        log.info("Senha temporária gerada para utilizador '{}' (ID: {})", user.getUsername(), user.getId());

        Map<String, String> resultado = new java.util.LinkedHashMap<>();
        resultado.put("username", user.getUsername());
        resultado.put("senhaTemporária", senhaTempStr);
        resultado.put("mensagem", "Senha redefinida com sucesso. Comunique a senha temporária ao utilizador.");
        return resultado;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User buscarEntidade(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado com ID: " + id));
    }

    private UnidadeAtendimento resolverUnidade(Long unidadeAtendimentoId) {
        if (unidadeAtendimentoId == null) {
            return null;
        }
        return unidadeAtendimentoRepository.findById(unidadeAtendimentoId)
                .orElseThrow(() -> new BusinessException("Unidade de atendimento não encontrada com ID: " + unidadeAtendimentoId));
    }

    private String normalizarTextoOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    private UserResponse toResponse(User user) {
        UnidadeAtendimento unidade = user.getUnidadeAtendimento();
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nomeCompleto(user.getNomeCompleto())
                .telefone(user.getTelefone())
                .unidadeAtendimentoId(unidade != null ? unidade.getId() : null)
                .unidadeAtendimentoNome(unidade != null ? unidade.getNome() : null)
                .roles(user.getRoles())
                .ativo(user.getAtivo())
                .created_at(user.getCreatedAt())
                .updated_at(user.getUpdatedAt())
                .ultimoAcesso(user.getUltimoAcesso())
                .build();
    }
}
