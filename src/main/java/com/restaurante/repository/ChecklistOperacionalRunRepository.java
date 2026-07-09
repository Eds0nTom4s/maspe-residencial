package com.restaurante.repository;

import com.restaurante.model.entity.ChecklistOperacionalRun;
import com.restaurante.model.enums.ChecklistTipo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChecklistOperacionalRunRepository extends JpaRepository<ChecklistOperacionalRun, Long> {
    List<ChecklistOperacionalRun> findByTenantIdAndTurnoIdOrderByIdAsc(Long tenantId, Long turnoId);
    List<ChecklistOperacionalRun> findByTenantIdAndTurnoIdAndTipoOrderByIdAsc(Long tenantId, Long turnoId, ChecklistTipo tipo);
}

