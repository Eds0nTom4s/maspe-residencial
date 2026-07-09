package com.restaurante.financeiro.snapshot.evidence.repository;

import com.restaurante.financeiro.snapshot.evidence.entity.TurnoEvidenceBundleAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TurnoEvidenceBundleAccessLogRepository extends JpaRepository<TurnoEvidenceBundleAccessLog, Long> {
}

