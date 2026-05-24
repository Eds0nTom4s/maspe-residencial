package com.restaurante.financeiro.caixa.repository;

import com.restaurante.model.entity.CaixaOperadorSessionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CaixaOperadorSessionItemRepository extends JpaRepository<CaixaOperadorSessionItem, Long> {
    List<CaixaOperadorSessionItem> findByTenantIdAndCaixaOperadorSessionId(Long tenantId, Long caixaOperadorSessionId);
}

