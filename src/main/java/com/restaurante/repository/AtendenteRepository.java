package com.restaurante.repository;

import com.restaurante.model.entity.Atendente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository para operações de banco de dados com Atendente
 */
@Repository
public interface AtendenteRepository extends JpaRepository<Atendente, Long> {

    /**
     * Busca atendente por email
     */
    Optional<Atendente> findByEmail(String email);

    /**
     * Verifica se existe atendente com o email informado
     */
    boolean existsByEmail(String email);

    /**
     * Busca atendentes ativos
     */
    List<Atendente> findByAtivoTrue();

    /**
     * Busca atendente ativo por email
     */
    Optional<Atendente> findByEmailAndAtivoTrue(String email);
    
    /**
     * Busca atendente por telefone
     */
    Optional<Atendente> findByTelefone(String telefone);
    
    /**
     * Busca atendente ativo por telefone
     */
    Optional<Atendente> findByTelefoneAndAtivoTrue(String telefone);
}
