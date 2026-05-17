package com.restaurante.security;

import com.restaurante.exception.BusinessException;
import com.restaurante.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validação leve de "usuário ainda está ativo" para reduzir risco de token stale,
 * sem precisar carregar UserDetails completo.
 */
@Component
@RequiredArgsConstructor
public class JwtUserStatusValidator {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public void validateUserStillActive(Long userId) {
        if (userId == null) {
            throw new BusinessException("userId ausente no token para validação.");
        }
        boolean active = userRepository.existsByIdAndAtivoTrue(userId);
        if (!active) {
            throw new BusinessException("Usuário inativo ou não encontrado.");
        }
    }
}

