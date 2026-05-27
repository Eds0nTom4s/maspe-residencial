package com.restaurante.tenantcore;

import com.restaurante.model.entity.*;
import com.restaurante.exception.BusinessException;
import com.restaurante.model.enums.*;
import com.restaurante.repository.*;
import com.restaurante.service.InstituicaoService;
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

    @Autowired
    private InstituicaoRepository instituicaoRepository;

    @Autowired
    private InstituicaoService instituicaoService;

    @Test
    void planoPilotoMustExistFromMigrationSeed() {
        assertTrue(planoRepository.findByCodigo("PILOTO").isPresent());
    }

    @Test
    @Transactional
    void canCreateTenantAndSubscriptionAndMembership() {
        Plano piloto = planoRepository.findByCodigo("PILOTO").orElseThrow();
        String suffix = String.valueOf(Math.abs(System.nanoTime() % 1_000_000L));

        Tenant tenant = new Tenant();
        tenant.setNome("Banca da Tia Rosa");
        tenant.setSlug("banca-tia-rosa-" + suffix);
        tenant.setTenantCode("BTR" + suffix);
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
                .username("owner_btr_" + suffix)
                .password("x")
                .email("owner_btr_" + suffix + "@consuma.local")
                .telefone("+24490" + String.format("%06d", Integer.parseInt(suffix)))
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

    @Test
    @Transactional
    void tenantCanOwnMultipleInstituicoesAndInstituicaoRequiresTenant() {
        Tenant tenant = new Tenant();
        tenant.setNome("Tenant Test 1N");
        tenant.setSlug("tenant-test-1n");
        tenant.setTenantCode("T1N");
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(TenantEstado.ATIVO);
        tenant = tenantRepository.saveAndFlush(tenant);
        final Long tenantId = tenant.getId();

        Instituicao i1 = Instituicao.builder()
                .tenant(tenant)
                .nome("Instituição A")
                .sigla(uniqueSigla("T1N"))
                .nif("T1N-A-001")
                .telefoneAutorizacao("+244900000010")
                .ativa(true)
                .build();
        instituicaoRepository.saveAndFlush(i1);

        Instituicao i2 = Instituicao.builder()
                .tenant(tenant)
                .nome("Instituição B")
                .sigla(uniqueSigla("T1N"))
                .nif("T1N-B-001")
                .telefoneAutorizacao("+244900000011")
                .ativa(true)
                .build();
        instituicaoRepository.saveAndFlush(i2);

        assertEquals(2, instituicaoRepository.findByTenantId(tenant.getId()).size());
        assertTrue(instituicaoRepository.findFirstByTenantIdAndAtivaTrue(tenant.getId()).isPresent());

        Instituicao semTenant = Instituicao.builder()
                .nome("Sem Tenant")
                .sigla(uniqueSigla("NO"))
                .nif("NO-TENANT-001")
                .telefoneAutorizacao("+244900000012")
                .ativa(true)
                .build();
        assertThrows(DataIntegrityViolationException.class, () -> instituicaoRepository.saveAndFlush(semTenant));
    }

    private static String uniqueSigla(String prefix) {
        String normalizedPrefix = prefix == null ? "I" : prefix.replaceAll("[^A-Z0-9]", "");
        if (normalizedPrefix.isBlank()) {
            normalizedPrefix = "I";
        }
        if (normalizedPrefix.length() > 3) {
            normalizedPrefix = normalizedPrefix.substring(0, 3);
        }

        long suffix = Math.abs(System.nanoTime() % 10_000_000L);
        return normalizedPrefix + String.format("%07d", suffix);
    }

    @Test
    @Transactional
    void planoPilotoMustLimitInstituicoesUnlessOverride() {
        // Tenant com plano PILOTO (seed V2)
        Plano piloto = planoRepository.findByCodigo("PILOTO").orElseThrow();

        Tenant tenant = new Tenant();
        tenant.setNome("Tenant Limites");
        tenant.setSlug("tenant-limites");
        tenant.setTenantCode("TLIM");
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(TenantEstado.ATIVO);
        tenant = tenantRepository.saveAndFlush(tenant);
        final Long tenantId = tenant.getId();

        Subscricao subs = new Subscricao();
        subs.setTenant(tenant);
        subs.setPlano(piloto);
        subs.setEstado(SubscricaoEstado.ATIVA);
        subs.setInicioEm(LocalDate.now());
        subs.setRenovacaoAutomatica(false);
        subscricaoRepository.saveAndFlush(subs);

        // 1a Instituicao: OK (limite do PILOTO = 1)
        instituicaoService.criarInstituicao(
                tenantId,
                "Inst 1",
                "TLIM1",
                "TLIM-NIF-1",
                null,
                "+244900000020"
        );

        // 2a Instituicao: deve falhar sem override
        assertThrows(BusinessException.class, () -> instituicaoService.criarInstituicao(
                tenantId,
                "Inst 2",
                "TLIM2",
                "TLIM-NIF-2",
                null,
                "+244900000021"
        ));

        // Override maxInstituicoes = 3
        TenantLimiteOverride override = new TenantLimiteOverride();
        override.setTenant(tenant);
        override.setMaxInstituicoes(3);
        override.setAtivo(true);
        tenantLimiteOverrideRepository.saveAndFlush(override);

        // 2a e 3a: OK agora
        instituicaoService.criarInstituicao(
                tenantId,
                "Inst 2",
                "TLIM2B",
                "TLIM-NIF-2B",
                null,
                "+244900000022"
        );
        instituicaoService.criarInstituicao(
                tenantId,
                "Inst 3",
                "TLIM3",
                "TLIM-NIF-3",
                null,
                "+244900000023"
        );

        // 4a: falha
        assertThrows(BusinessException.class, () -> instituicaoService.criarInstituicao(
                tenantId,
                "Inst 4",
                "TLIM4",
                "TLIM-NIF-4",
                null,
                "+244900000024"
        ));
    }
}
