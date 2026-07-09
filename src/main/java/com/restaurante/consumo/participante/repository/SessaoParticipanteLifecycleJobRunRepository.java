package com.restaurante.consumo.participante.repository;

import com.restaurante.consumo.participante.entity.SessaoParticipanteLifecycleJobRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Prompt 41.4 — Repository para registos de execuções do job de expiração.
 */
public interface SessaoParticipanteLifecycleJobRunRepository extends JpaRepository<SessaoParticipanteLifecycleJobRun, Long> {

    @Query("""
            select r from SessaoParticipanteLifecycleJobRun r
             where r.jobName = :jobName
             order by r.startedAt desc
            """)
    List<SessaoParticipanteLifecycleJobRun> findLastRunsByJobName(@Param("jobName") String jobName, Pageable pageable);

    Optional<SessaoParticipanteLifecycleJobRun> findByBatchId(String batchId);
}
