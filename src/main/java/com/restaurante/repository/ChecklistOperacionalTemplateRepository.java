package com.restaurante.repository;

import com.restaurante.model.entity.ChecklistOperacionalTemplate;
import com.restaurante.model.enums.ChecklistTipo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChecklistOperacionalTemplateRepository extends JpaRepository<ChecklistOperacionalTemplate, Long> {
    List<ChecklistOperacionalTemplate> findByTipoAndAtivoTrueOrderByIdAsc(ChecklistTipo tipo);
    List<ChecklistOperacionalTemplate> findByTenantIdAndTipoAndAtivoTrueOrderByIdAsc(Long tenantId, ChecklistTipo tipo);
}

