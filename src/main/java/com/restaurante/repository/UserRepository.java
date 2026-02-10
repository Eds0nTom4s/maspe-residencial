package com.restaurante.repository;

import com.restaurante.model.entity.User;
import com.restaurante.model.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository para User
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Busca usuário por username
     */
    Optional<User> findByUsername(String username);

    /**
     * Busca usuário por email
     */
    Optional<User> findByEmail(String email);

    /**
     * Verifica se username existe
     */
    boolean existsByUsername(String username);

    /**
     * Verifica se email existe
     */
    boolean existsByEmail(String email);

    /**
     * Busca usuários ativos
     */
    List<User> findByAtivoTrue();

    /**
     * Busca usuários por role
     */
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r = :role")
    List<User> findByRole(@Param("role") Role role);

    /**
     * Busca usuários ativos por role
     */
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r = :role AND u.ativo = true")
    List<User> findByRoleAndAtivoTrue(@Param("role") Role role);
}
