package com.restaurante.repository;

import com.restaurante.model.entity.TenantAuditLog;
import com.restaurante.model.enums.TenantAuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantAuditLogRepository extends JpaRepository<TenantAuditLog, Long> {

    Page<TenantAuditLog> findByTenantId(Long tenantId, Pageable pageable);

    Page<TenantAuditLog> findByTenantIdAndAction(Long tenantId, TenantAuditAction action, Pageable pageable);

    Page<TenantAuditLog> findByTenantIdAndTargetUserId(Long tenantId, Long targetUserId, Pageable pageable);
}

