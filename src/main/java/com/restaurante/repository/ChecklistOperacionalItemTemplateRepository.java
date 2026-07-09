package com.restaurante.repository;

import com.restaurante.model.entity.ChecklistOperacionalItemTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChecklistOperacionalItemTemplateRepository extends JpaRepository<ChecklistOperacionalItemTemplate, Long> {
    List<ChecklistOperacionalItemTemplate> findByTemplateIdAndAtivoTrueOrderByOrdemAscIdAsc(Long templateId);
}

