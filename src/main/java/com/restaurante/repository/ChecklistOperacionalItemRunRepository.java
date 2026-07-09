package com.restaurante.repository;

import com.restaurante.model.entity.ChecklistOperacionalItemRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChecklistOperacionalItemRunRepository extends JpaRepository<ChecklistOperacionalItemRun, Long> {
    List<ChecklistOperacionalItemRun> findByRunIdOrderByIdAsc(Long runId);
}

