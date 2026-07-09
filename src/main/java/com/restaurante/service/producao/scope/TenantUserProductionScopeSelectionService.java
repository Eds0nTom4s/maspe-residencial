package com.restaurante.service.producao.scope;

import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUserProductionScope;
import com.restaurante.model.entity.UnidadeProducao;
import com.restaurante.model.entity.User;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserProductionScopeRepository;
import com.restaurante.repository.UnidadeProducaoRepository;
import com.restaurante.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TenantUserProductionScopeSelectionService {

    private final TenantUserProductionScopeRepository repository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final UnidadeProducaoRepository unidadeProducaoRepository;

    @Transactional(readOnly = true)
    public Optional<UnidadeProducao> findSelectedUnit(Long tenantId, Long userId) {
        return repository.findByTenantIdAndUserIdAndAtivoTrue(tenantId, userId).map(TenantUserProductionScope::getUnidadeProducao);
    }

    @Transactional
    public UnidadeProducao selectUnit(Long tenantId, Long userId, Long unidadeProducaoId) {
        UnidadeProducao unidade = unidadeProducaoRepository.findByIdAndTenantId(unidadeProducaoId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

        TenantUserProductionScope scope = repository.findByTenantIdAndUserId(tenantId, userId).orElseGet(TenantUserProductionScope::new);
        scope.setTenant(tenant);
        scope.setUser(user);
        scope.setUnidadeProducao(unidade);
        scope.setAtivo(true);
        scope.setAtualizadoEm(LocalDateTime.now());
        repository.save(scope);
        return unidade;
    }

    @Transactional
    public void clearSelection(Long tenantId, Long userId) {
        repository.findByTenantIdAndUserId(tenantId, userId).ifPresent(scope -> {
            scope.setAtivo(false);
            scope.setAtualizadoEm(LocalDateTime.now());
            repository.save(scope);
        });
    }
}

