package com.restaurante.repository;

import com.restaurante.model.entity.BusinessAccount;
import com.restaurante.model.enums.BusinessAccountEstado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BusinessAccountRepository extends JpaRepository<BusinessAccount, Long> {

    Optional<BusinessAccount> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<BusinessAccount> findByEstadoOrderByIdAsc(BusinessAccountEstado estado);

    List<BusinessAccount> findByResponsavelId(Long responsavelId);

    @Query("""
            select distinct bam.businessAccount
            from BusinessAccountMember bam
            where bam.user.id = :userId
            order by bam.businessAccount.id asc
            """)
    List<BusinessAccount> findByMemberUserId(@Param("userId") Long userId);

    @Query("""
            select ba
            from BusinessAccount ba
            where (:estado is null or ba.estado = :estado)
              and (:responsavelUserId is null or ba.responsavel.id = :responsavelUserId)
              and (:search is null or :search = ''
                   or lower(ba.nome) like lower(concat('%', :search, '%'))
                   or lower(ba.slug) like lower(concat('%', :search, '%'))
                   or lower(coalesce(ba.email, '')) like lower(concat('%', :search, '%')))
              and (:hasTenants is null
                   or (:hasTenants = true and exists (select 1 from Tenant t where t.businessAccount = ba))
                   or (:hasTenants = false and not exists (select 1 from Tenant t where t.businessAccount = ba)))
            order by ba.id asc
            """)
    Page<BusinessAccount> search(@Param("estado") BusinessAccountEstado estado,
                                 @Param("search") String search,
                                 @Param("responsavelUserId") Long responsavelUserId,
                                 @Param("hasTenants") Boolean hasTenants,
                                 Pageable pageable);
}
