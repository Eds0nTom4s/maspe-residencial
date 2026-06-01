package com.restaurante.tenantcore;

import com.restaurante.exception.BusinessException;
import com.restaurante.model.entity.Instituicao;
import com.restaurante.model.entity.Plano;
import com.restaurante.model.entity.Subscricao;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.entity.TenantLimiteOverride;
import com.restaurante.model.entity.TenantUser;
import com.restaurante.model.entity.User;
import com.restaurante.model.enums.Role;
import com.restaurante.model.enums.SubscricaoEstado;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.model.enums.TenantUserEstado;
import com.restaurante.model.enums.TenantUserRole;
import com.restaurante.repository.InstituicaoRepository;
import com.restaurante.repository.PlanoRepository;
import com.restaurante.repository.SubscricaoRepository;
import com.restaurante.repository.TenantLimiteOverrideRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.repository.TenantUserRepository;
import com.restaurante.repository.UserRepository;
import com.restaurante.service.InstituicaoService;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import com.restaurante.testsupport.UniqueTestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integração real (PostgreSQL + Flyway) para validar:
 * - baseline V1 + V2 aplicam em banco limpo
 * - constraints críticas (uniques / partial indexes) funcionam em PostgreSQL
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

        Tenant tenant = new Tenant();
        tenant.setNome("Banca da Tia Rosa");
        tenant.setSlug(UniqueTestData.uniqueSlug("banca-tia-rosa"));
        tenant.setTenantCode(UniqueTestData.uniqueTenantCode("BTR"));
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

        User user = User.builder()
                .username(UniqueTestData.uniqueUsername("owner_btr"))
                .password("x")
                .email(UniqueTestData.uniqueEmail("owner_btr"))
                .telefone(UniqueTestData.uniqueTelefone())
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

        TenantLimiteOverride override = new TenantLimiteOverride();
        override.setTenant(tenant);
        override.setMotivo("Ajuste manual piloto");
        override.setConfiguradoPor("platform-admin@consuma.local");
        override.setConfiguradoEm(LocalDateTime.now());
        override.setMaxProdutos(150);
        override.setAtivo(true);
        tenantLimiteOverrideRepository.saveAndFlush(override);
    }

    @Test
    @Transactional
    void tenantMustEnforceUniqueSlug() {
        String duplicatedSlug = UniqueTestData.uniqueSlug("bar-do-joao");

        Tenant t1 = new Tenant();
        t1.setNome("Bar do João");
        t1.setSlug(duplicatedSlug);
        t1.setTenantCode(UniqueTestData.uniqueTenantCode("BDJ"));
        t1.setTipo(TenantTipo.BAR);
        t1.setEstado(TenantEstado.ATIVO);
        tenantRepository.saveAndFlush(t1);

        Tenant tSlugDup = new Tenant();
        tSlugDup.setNome("Outra Conta");
        tSlugDup.setSlug(duplicatedSlug);
        tSlugDup.setTenantCode(UniqueTestData.uniqueTenantCode("OUT"));
        tSlugDup.setTipo(TenantTipo.BAR);
        tSlugDup.setEstado(TenantEstado.ATIVO);
        assertThrows(DataIntegrityViolationException.class, () -> tenantRepository.saveAndFlush(tSlugDup));
    }

    @Test
    @Transactional
    void tenantMustEnforceUniqueTenantCode() {
        String duplicatedCode = UniqueTestData.uniqueTenantCode("BDJ");

        Tenant t1 = new Tenant();
        t1.setNome("Bar do João");
        t1.setSlug(UniqueTestData.uniqueSlug("bar-do-joao-code"));
        t1.setTenantCode(duplicatedCode);
        t1.setTipo(TenantTipo.BAR);
        t1.setEstado(TenantEstado.ATIVO);
        tenantRepository.saveAndFlush(t1);

        Tenant tCodeDup = new Tenant();
        tCodeDup.setNome("Outra Conta 2");
        tCodeDup.setSlug(UniqueTestData.uniqueSlug("outra-conta-2"));
        tCodeDup.setTenantCode(duplicatedCode);
        tCodeDup.setTipo(TenantTipo.BAR);
        tCodeDup.setEstado(TenantEstado.ATIVO);
        assertThrows(DataIntegrityViolationException.class, () -> tenantRepository.saveAndFlush(tCodeDup));
    }

    @Test
    @Transactional
    void tenantMustEnforceSingleActiveSubscriptionPerTenant() {
        Plano piloto = planoRepository.findByCodigo("PILOTO").orElseThrow();

        Tenant tenant = new Tenant();
        tenant.setNome("Tenant Subscription Unique");
        tenant.setSlug(UniqueTestData.uniqueSlug("tenant-sub-unique"));
        tenant.setTenantCode(UniqueTestData.uniqueTenantCode("TSU"));
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(TenantEstado.ATIVO);
        tenant = tenantRepository.saveAndFlush(tenant);

        Subscricao s1 = new Subscricao();
        s1.setTenant(tenant);
        s1.setPlano(piloto);
        s1.setEstado(SubscricaoEstado.ATIVA);
        s1.setInicioEm(LocalDate.now());
        s1.setRenovacaoAutomatica(false);
        subscricaoRepository.saveAndFlush(s1);

        Subscricao s2 = new Subscricao();
        s2.setTenant(tenant);
        s2.setPlano(piloto);
        s2.setEstado(SubscricaoEstado.ATIVA);
        s2.setInicioEm(LocalDate.now());
        s2.setRenovacaoAutomatica(false);
        assertThrows(DataIntegrityViolationException.class, () -> subscricaoRepository.saveAndFlush(s2));
    }

    @Test
    @Transactional
    void tenantCanOwnMultipleInstituicoesAndInstituicaoRequiresTenant() {
        Tenant tenant = new Tenant();
        tenant.setNome("Tenant Test 1N");
        tenant.setSlug(UniqueTestData.uniqueSlug("tenant-test-1n"));
        tenant.setTenantCode(UniqueTestData.uniqueTenantCode("T1N"));
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(TenantEstado.ATIVO);
        tenant = tenantRepository.saveAndFlush(tenant);
        Long tenantId = tenant.getId();

        Instituicao i1 = Instituicao.builder()
                .tenant(tenant)
                .nome("Instituição A")
                .sigla(UniqueTestData.uniqueInstituicaoSigla("T1NA"))
                .nif(UniqueTestData.uniqueNif("T1N-A"))
                .telefoneAutorizacao(UniqueTestData.uniqueTelefone())
                .ativa(true)
                .build();
        instituicaoRepository.saveAndFlush(i1);

        Instituicao i2 = Instituicao.builder()
                .tenant(tenant)
                .nome("Instituição B")
                .sigla(UniqueTestData.uniqueInstituicaoSigla("T1NB"))
                .nif(UniqueTestData.uniqueNif("T1N-B"))
                .telefoneAutorizacao(UniqueTestData.uniqueTelefone())
                .ativa(true)
                .build();
        instituicaoRepository.saveAndFlush(i2);

        assertEquals(2, instituicaoRepository.findByTenantId(tenantId).size());
        assertTrue(instituicaoRepository.findFirstByTenantIdAndAtivaTrue(tenantId).isPresent());

        Instituicao semTenant = Instituicao.builder()
                .nome("Sem Tenant")
                .sigla(UniqueTestData.uniqueInstituicaoSigla("NOTEN"))
                .nif(UniqueTestData.uniqueNif("NO-TENANT"))
                .telefoneAutorizacao(UniqueTestData.uniqueTelefone())
                .ativa(true)
                .build();
        assertThrows(DataIntegrityViolationException.class, () -> instituicaoRepository.saveAndFlush(semTenant));
    }

    @Test
    @Transactional
    void planoPilotoMustLimitInstituicoesUnlessOverride() {
        Plano piloto = planoRepository.findByCodigo("PILOTO").orElseThrow();

        Tenant tenant = new Tenant();
        tenant.setNome("Tenant Limites");
        tenant.setSlug(UniqueTestData.uniqueSlug("tenant-limites"));
        tenant.setTenantCode(UniqueTestData.uniqueTenantCode("TLIM"));
        tenant.setTipo(TenantTipo.RESTAURANTE);
        tenant.setEstado(TenantEstado.ATIVO);
        tenant = tenantRepository.saveAndFlush(tenant);
        Long tenantId = tenant.getId();

        Subscricao subs = new Subscricao();
        subs.setTenant(tenant);
        subs.setPlano(piloto);
        subs.setEstado(SubscricaoEstado.ATIVA);
        subs.setInicioEm(LocalDate.now());
        subs.setRenovacaoAutomatica(false);
        subscricaoRepository.saveAndFlush(subs);

        instituicaoService.criarInstituicao(
                tenantId,
                "Inst 1",
                UniqueTestData.uniqueInstituicaoSigla("TLIM1"),
                UniqueTestData.uniqueNif("TLIM-1"),
                null,
                UniqueTestData.uniqueTelefone()
        );

        assertThrows(BusinessException.class, () -> instituicaoService.criarInstituicao(
                tenantId,
                "Inst 2",
                UniqueTestData.uniqueInstituicaoSigla("TLIM2"),
                UniqueTestData.uniqueNif("TLIM-2"),
                null,
                UniqueTestData.uniqueTelefone()
        ));

        TenantLimiteOverride override = new TenantLimiteOverride();
        override.setTenant(tenant);
        override.setMaxInstituicoes(3);
        override.setAtivo(true);
        tenantLimiteOverrideRepository.saveAndFlush(override);

        instituicaoService.criarInstituicao(
                tenantId,
                "Inst 2",
                UniqueTestData.uniqueInstituicaoSigla("TLIM2B"),
                UniqueTestData.uniqueNif("TLIM-2B"),
                null,
                UniqueTestData.uniqueTelefone()
        );
        instituicaoService.criarInstituicao(
                tenantId,
                "Inst 3",
                UniqueTestData.uniqueInstituicaoSigla("TLIM3"),
                UniqueTestData.uniqueNif("TLIM-3"),
                null,
                UniqueTestData.uniqueTelefone()
        );

        assertThrows(BusinessException.class, () -> instituicaoService.criarInstituicao(
                tenantId,
                "Inst 4",
                UniqueTestData.uniqueInstituicaoSigla("TLIM4"),
                UniqueTestData.uniqueNif("TLIM-4"),
                null,
                UniqueTestData.uniqueTelefone()
        ));
    }
}
