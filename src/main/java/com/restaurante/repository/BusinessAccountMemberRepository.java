package com.restaurante.repository;

import com.restaurante.model.entity.BusinessAccountMember;
import com.restaurante.model.enums.BusinessAccountMemberEstado;
import com.restaurante.model.enums.BusinessAccountRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessAccountMemberRepository extends JpaRepository<BusinessAccountMember, Long> {

    List<BusinessAccountMember> findByBusinessAccountIdOrderByIdAsc(Long businessAccountId);

    List<BusinessAccountMember> findByUserIdOrderByIdAsc(Long userId);

    Optional<BusinessAccountMember> findByBusinessAccountIdAndUserId(Long businessAccountId, Long userId);

    Optional<BusinessAccountMember> findByBusinessAccountIdAndId(Long businessAccountId, Long id);

    boolean existsByBusinessAccountIdAndUserIdAndEstado(Long businessAccountId, Long userId, BusinessAccountMemberEstado estado);

    boolean existsByBusinessAccountIdAndUserIdAndRoleInAndEstado(Long businessAccountId, Long userId, List<BusinessAccountRole> roles, BusinessAccountMemberEstado estado);

    long countByBusinessAccountId(Long businessAccountId);

    long countByBusinessAccountIdAndRoleAndEstado(Long businessAccountId,
                                                  BusinessAccountRole role,
                                                  BusinessAccountMemberEstado estado);
}
