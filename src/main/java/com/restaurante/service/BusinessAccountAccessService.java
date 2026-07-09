package com.restaurante.service;

import com.restaurante.model.entity.BusinessAccount;
import com.restaurante.model.enums.BusinessAccountMemberEstado;
import com.restaurante.model.enums.BusinessAccountRole;
import com.restaurante.repository.BusinessAccountMemberRepository;
import com.restaurante.repository.BusinessAccountRepository;
import com.restaurante.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BusinessAccountAccessService {

    private final BusinessAccountRepository businessAccountRepository;
    private final BusinessAccountMemberRepository businessAccountMemberRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public boolean userBelongsToBusinessAccount(Long userId, Long businessAccountId) {
        if (userId == null || businessAccountId == null) {
            return false;
        }
        if (businessAccountMemberRepository.existsByBusinessAccountIdAndUserIdAndEstado(
                businessAccountId,
                userId,
                BusinessAccountMemberEstado.ATIVO
        )) {
            return true;
        }
        return businessAccountRepository.findById(businessAccountId)
                .map(BusinessAccount::getResponsavel)
                .map(responsavel -> responsavel.getId().equals(userId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean userIsOwnerOrAdmin(Long userId, Long businessAccountId) {
        if (userId == null || businessAccountId == null) {
            return false;
        }
        if (businessAccountMemberRepository.existsByBusinessAccountIdAndUserIdAndRoleInAndEstado(
                businessAccountId,
                userId,
                List.of(BusinessAccountRole.OWNER, BusinessAccountRole.ADMIN),
                BusinessAccountMemberEstado.ATIVO
        )) {
            return true;
        }
        return businessAccountRepository.findById(businessAccountId)
                .map(BusinessAccount::getResponsavel)
                .map(responsavel -> responsavel.getId().equals(userId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean tenantBelongsToBusinessAccount(Long tenantId, Long businessAccountId) {
        if (tenantId == null || businessAccountId == null) {
            return false;
        }
        return tenantRepository.existsByIdAndBusinessAccountId(tenantId, businessAccountId);
    }
}
