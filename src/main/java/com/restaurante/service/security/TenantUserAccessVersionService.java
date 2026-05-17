package com.restaurante.service.security;

import com.restaurante.exception.ResourceNotFoundException;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantUserAccessVersion;
import com.restaurante.model.entity.User;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserAccessVersionRepository;
import com.restaurante.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TenantUserAccessVersionService {

    private final TenantUserAccessVersionRepository repository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    @Transactional
    public TenantUserAccessVersion ensureExists(Long tenantId, Long userId) {
        return repository.findByTenantIdAndUserId(tenantId, userId).orElseGet(() -> {
            Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));
            User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado."));

            TenantUserAccessVersion row = new TenantUserAccessVersion();
            row.setTenant(tenant);
            row.setUser(user);
            row.setAccessVersion(1);
            row.setPermissionsUpdatedAt(LocalDateTime.now());
            return repository.save(row);
        });
    }

    @Transactional
    public TenantUserAccessVersion increment(Long tenantId, Long userId) {
        TenantUserAccessVersion row = ensureExists(tenantId, userId);
        row.setAccessVersion(row.getAccessVersion() + 1);
        row.setPermissionsUpdatedAt(LocalDateTime.now());
        return repository.save(row);
    }

    @Transactional(readOnly = true)
    public int getAccessVersion(Long tenantId, Long userId) {
        return repository.findAccessVersion(tenantId, userId).orElse(1);
    }

    @Transactional(readOnly = true)
    public LocalDateTime getPermissionsUpdatedAt(Long tenantId, Long userId) {
        return repository.findPermissionsUpdatedAt(tenantId, userId).orElse(null);
    }
}

