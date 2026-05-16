package com.restaurante.repository;

import com.restaurante.model.entity.ProvisioningTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProvisioningTemplateRepository extends JpaRepository<ProvisioningTemplate, Long> {

    Optional<ProvisioningTemplate> findByCodigoAndAtivoTrue(String codigo);

    List<ProvisioningTemplate> findByAtivoTrue();

    boolean existsByCodigo(String codigo);
}

