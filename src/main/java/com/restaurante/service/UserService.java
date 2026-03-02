package com.restaurante.service;

import com.restaurante.dto.response.UserResponse;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.User;
import com.restaurante.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<UserResponse> listarTodos(Pageable pageable) {
        log.info("Listando usuários (paginado)");
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse buscarPorId(Long id) {
        log.info("Buscando usuário por ID: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado com ID: " + id));
        return toResponse(user);
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
