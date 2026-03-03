package com.restaurante.service;

import com.restaurante.dto.request.AtualizarUsuarioRequest;
import com.restaurante.dto.request.CriarUsuarioRequest;
import com.restaurante.dto.request.AlterarSenhaAdminRequest;
import com.restaurante.dto.response.UserResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.Role;
import com.restaurante.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ── Leitura ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<UserResponse> listarTodos(Pageable pageable) {
        log.info("Listando usuários (paginado)");
        return userRepository.findAll(pageable).map(this::toResponse);
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
     * Cria novo utilizador. Username e email devem ser únicos.
     */
    @Transactional
    public UserResponse criar(CriarUsuarioRequest request) {
        log.info("Criando utilizador: {}", request.getUsername());

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username já está em uso: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email já está em uso: " + request.getEmail());
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getSenha()))
                .email(request.getEmail())
                .nomeCompleto(request.getNomeCompleto())
                .telefone(request.getTelefone())
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

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new BusinessException("Email já está em uso: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }
        if (request.getNomeCompleto() != null) {
            user.setNomeCompleto(request.getNomeCompleto());
        }
        if (request.getTelefone() != null) {
            user.setTelefone(request.getTelefone());
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User buscarEntidade(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado com ID: " + id));
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nomeCompleto(user.getNomeCompleto())
                .telefone(user.getTelefone())
                .roles(user.getRoles())
                .ativo(user.getAtivo())
                .created_at(user.getCreatedAt())
                .updated_at(user.getUpdatedAt())
                .ultimoAcesso(user.getUltimoAcesso())
                .build();
    }
}
