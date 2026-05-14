package com.restaurante.repository;

import com.restaurante.model.entity.Plano;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanoRepository extends JpaRepository<Plano, Long> {

    Optional<Plano> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    List<Plano> findByAtivoTrue();
}

