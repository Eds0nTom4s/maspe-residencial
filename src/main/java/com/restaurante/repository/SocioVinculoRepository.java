package com.restaurante.repository;

import com.restaurante.model.entity.SocioVinculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositório para SocioVinculo.
 *
 * <p>Usado exclusivamente pelo {@code SocioAuthFilter} e {@code StoreService}
 * para resolver a identidade do sócio Associagest no contexto do motor.
 */
@Repository
public interface SocioVinculoRepository extends JpaRepository<SocioVinculo, Long> {

    /**
     * Busca vínculo pelo ID externo do Associagest (claim {@code sub} do JWT).
     */
    Optional<SocioVinculo> findBySocioId(String socioId);

    /**
     * Busca vínculo pelo número de telefone do sócio.
     */
    Optional<SocioVinculo> findByTelefone(String telefone);

    /**
     * Verifica existência de vínculo pelo ID externo.
     */
    boolean existsBySocioId(String socioId);
}
