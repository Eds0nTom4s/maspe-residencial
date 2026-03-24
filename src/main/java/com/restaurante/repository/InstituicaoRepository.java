package com.restaurante.repository;

import com.restaurante.model.entity.Instituicao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InstituicaoRepository extends JpaRepository<Instituicao, Long> {

    Optional<Instituicao> findBySigla(String sigla);

    // Método utiizado para encontrar a instituição principal (num modelo single-tenant pseudo escalável)
    Optional<Instituicao> findFirstByAtivaTrue();
}
