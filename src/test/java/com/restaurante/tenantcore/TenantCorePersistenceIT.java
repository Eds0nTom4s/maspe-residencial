package com.restaurante.tenantcore;

import com.restaurante.model.entity.*;
import com.restaurante.model.enums.*;
import com.restaurante.repository.*;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integração real (PostgreSQL + Flyway) para validar:
 * - baseline V1 + V2 aplicam em banco limpo
 * - constraints críticas (uniques / partial indexes) funcionam em PostgreSQL
 *
 * Será automaticamente ignorado quando Docker não estiver disponível.
 */
@SpringBootTest
@ActiveProfiles("it-postgres")
class TenantCorePersistenceIT extends PostgresTestcontainersConfig {

    @Autowired
    private PlanoRepository planoRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private SubscricaoRepository subscricaoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantUserRepository tenantUserRepository;

    @Autowired
    private TenantLimiteOverrideRepository tenantLimiteOverrideRepository;

    @Test
    void planoPilotoMustExistFromMigrationSeed() {
        assertTrue(planoRepository.findByCodigo("PILOTO").isPresent());
    }

    @Test
    @Transactional
    void canCreateTenantAndSubscriptionAndMembership() {
        Plano piloto = planoRepository.findByCodigo("PILOTO").orElseThrow();

        Tenant tenant = new Tenant();
        tenant.setNome("Banca da Tia Rosa");
        tenant.setSlug("banca-tia-rosa");
        tenant.setTenantCode("BTR");
        tenant.setTipo(TenantTipo.VENDEDOR_RUA);
        tenant.setEstado(TenantEstado.ATIVO);
        tenant = tenantRepository.saveAndFlush(tenant);

        Subscricao s1 = new Subscricao();
        s1.setTenant(tenant);
        s1.setPlano(piloto);
        s1.setEstado(SubscricaoEstado.ATIVA);
        s1.setInicioEm(LocalDate.now());
        s1.setRenovacaoAutomatica(false);
        subscricaoRepository.saveAndFlush(s1);

        // 2a subscrição ATIVA para mesmo tenant deve falhar (índice parcial)
        Subscricao s2 = new Subscricao();
        s2.setTenant(tenant);
        s2.setPlano(piloto);
        s2.setEstado(SubscricaoEstado.ATIVA);
        s2.setInicioEm(LocalDate.now());
        s2.setRenovacaoAutomatica(false);
        assertThrows(DataIntegrityViolationException.class, () -> subscricaoRepository.saveAndFlush(s2));

        User user = User.builder()
                .username("owner_btr")
                .password("x")
                .email("owner_btr@consuma.local")
                .telefone("+244900000001")
                .roles(Set.of(Role.ROLE_ADMIN))
                .ativo(true)
                .build();
        user = userRepository.saveAndFlush(user);

        TenantUser membership = new TenantUser();
        membership.setTenant(tenant);
        membership.setUser(user);
        membership.setRole(TenantUserRole.TENANT_OWNER);
        membership.setEstado(TenantUserEstado.ATIVO);
        tenantUserRepository.saveAndFlush(membership);

        // duplicado (tenant_id, user_id, role) deve falhar
        TenantUser dup = new TenantUser();
        dup.setTenant(tenant);
        dup.setUser(user);
        dup.setRole(TenantUserRole.TENANT_OWNER);
        dup.setEstado(TenantUserEstado.ATIVO);
        assertThrows(DataIntegrityViolationException.class, () -> tenantUserRepository.saveAndFlush(dup));

        TenantLimiteOverride override = new TenantLimiteOverride();
        override.setTenant(tenant);
        override.setMotivo("Ajuste manual piloto");
        override.setConfiguradoPor("platform-admin@consuma.local");
        override.setConfiguradoEm(LocalDateTime.now());
        override.setMaxProdutos(150);
        override.setAtivo(true);
        tenantLimiteOverrideRepository.saveAndFlush(override);

        // 2o override ativo deve falhar (índice parcial)
        TenantLimiteOverride override2 = new TenantLimiteOverride();
        override2.setTenant(tenant);
        override2.setMotivo("Outro override");
        override2.setAtivo(true);
        assertThrows(DataIntegrityViolationException.class, () -> tenantLimiteOverrideRepository.saveAndFlush(override2));
    }

    @Test
    @Transactional
    void tenantMustEnforceUniqueSlugAndTenantCode() {
        Tenant t1 = new Tenant();
        t1.setNome("Bar do João");
        t1.setSlug("bar-do-joao");
        t1.setTenantCode("BDJ");
        t1.setTipo(TenantTipo.BAR);
        t1.setEstado(TenantEstado.ATIVO);
        tenantRepository.saveAndFlush(t1);

        Tenant tSlugDup = new Tenant();
        tSlugDup.setNome("Outra Conta");
        tSlugDup.setSlug("bar-do-joao");
        tSlugDup.setTenantCode("OUT");
        tSlugDup.setTipo(TenantTipo.BAR);
        tSlugDup.setEstado(TenantEstado.ATIVO);
        assertThrows(DataIntegrityViolationException.class, () -> tenantRepository.saveAndFlush(tSlugDup));

        Tenant tCodeDup = new Tenant();
        tCodeDup.setNome("Outra Conta 2");
        tCodeDup.setSlug("outra-conta-2");
        tCodeDup.setTenantCode("BDJ");
        tCodeDup.setTipo(TenantTipo.BAR);
        tCodeDup.setEstado(TenantEstado.ATIVO);
        assertThrows(DataIntegrityViolationException.class, () -> tenantRepository.saveAndFlush(tCodeDup));
    }
}

