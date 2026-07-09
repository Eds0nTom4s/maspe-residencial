package com.restaurante.consumo.identificacao.repository;

import com.restaurante.consumo.identificacao.entity.TelefoneOtpChallenge;
import com.restaurante.model.enums.OtpStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TelefoneOtpChallengeRepository extends JpaRepository<TelefoneOtpChallenge, Long> {

    Optional<TelefoneOtpChallenge> findByIdAndTenant_Id(Long id, Long tenantId);

    @Query("""
            select c
            from TelefoneOtpChallenge c
            where c.tenant.id = :tenantId
              and c.telefoneNormalizado = :phone
              and c.status = com.restaurante.model.enums.OtpStatus.PENDING
              and c.expiresAt > :now
            order by c.id desc
            """)
    List<TelefoneOtpChallenge> findActivePendingByPhone(@Param("tenantId") Long tenantId,
                                                        @Param("phone") String telefoneNormalizado,
                                                        @Param("now") Instant now);

    @Query("""
            select count(c)
            from TelefoneOtpChallenge c
            where c.tenant.id = :tenantId
              and c.telefoneNormalizado = :phone
              and c.createdAt >= :since
            """)
    long countRecentByPhone(@Param("tenantId") Long tenantId, @Param("phone") String phone, @Param("since") Instant since);

    @Query("""
            select count(c)
            from TelefoneOtpChallenge c
            where c.tenant.id = :tenantId
              and c.clientIp = :ip
              and c.createdAt >= :since
            """)
    long countRecentByIp(@Param("tenantId") Long tenantId, @Param("ip") String ip, @Param("since") Instant since);

    @Query("""
            select c
            from TelefoneOtpChallenge c
            where c.tenant.id = :tenantId
              and c.status = :status
              and c.expiresAt <= :now
            """)
    List<TelefoneOtpChallenge> findExpired(@Param("tenantId") Long tenantId, @Param("status") OtpStatus status, @Param("now") Instant now);
}

