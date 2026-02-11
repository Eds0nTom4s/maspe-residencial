package com.restaurante.repository;

import com.restaurante.model.entity.ConfiguracaoFinanceiraSistema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfiguracaoFinanceiraSistemaRepository extends JpaRepository<ConfiguracaoFinanceiraSistema, Long> {

    /**
     * Busca configuração ativa (sempre deve existir apenas uma)
     */
    @Query("SELECT c FROM ConfiguracaoFinanceiraSistema c ORDER BY c.updatedAt DESC LIMIT 1")
    Optional<ConfiguracaoFinanceiraSistema> findAtual();
}
