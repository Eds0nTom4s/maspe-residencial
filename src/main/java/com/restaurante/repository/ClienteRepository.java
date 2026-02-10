package com.restaurante.repository;

import com.restaurante.model.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository para operações de banco de dados com Cliente
 */
@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    /**
     * Busca cliente por telefone
     */
    Optional<Cliente> findByTelefone(String telefone);

    /**
     * Verifica se existe cliente com o telefone informado
     */
    boolean existsByTelefone(String telefone);

    /**
     * Busca cliente ativo por telefone
     */
    Optional<Cliente> findByTelefoneAndAtivoTrue(String telefone);
}
