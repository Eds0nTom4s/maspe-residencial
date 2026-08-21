package com.restaurante.producao;

import com.restaurante.testsupport.PostgresTestcontainersConfig;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("it-postgres")
class CanonicalProductionRoutingMigrationPostgresIT extends PostgresTestcontainersConfig {
    private static final MigrationVersion PREVIOUS = MigrationVersion.fromVersion("20260809.01");
    private static final String CURRENT = "20260810.01";

    @Autowired DataSource dataSource;
    @Autowired DataSourceProperties properties;

    @Test
    void migratesFreshAndExistingSchemasPreservingLegacyKitchensAndEnforcingScope() {
        withDatabase("canonical_routing_existing_", isolated -> {
            flyway(isolated, PREVIOUS).migrate();
            JdbcTemplate jdbc = new JdbcTemplate(isolated);
            LegacyFixture fixture = seedLegacyRoute(jdbc);
            long legacyBefore = jdbc.queryForObject("select count(*) from cozinhas", Long.class);

            assertThat(flyway(isolated, null).migrate().migrationsExecuted).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from cozinhas", Long.class))
                    .isEqualTo(legacyBefore);
            assertThat(jdbc.queryForObject(
                    "select instituicao_id from rotas_producao_categoria where id=?",
                    Long.class, fixture.routeId())).isEqualTo(fixture.institutionAId());
            assertThat(jdbc.queryForObject("select is_nullable from information_schema.columns "
                    + "where table_name='sub_pedidos' and column_name='cozinha_id'", String.class))
                    .isEqualTo("YES");
            assertThat(jdbc.queryForObject("select is_nullable from information_schema.columns "
                    + "where table_name='subpedido_event_log' and column_name='cozinha_id'", String.class))
                    .isEqualTo("YES");

            assertThatThrownBy(() -> jdbc.update(
                    "insert into unidades_producao "
                            + "(version,created_at,tenant_id,instituicao_id,nome,codigo,tipo,ativo,ordem,criado_em) "
                            + "values (0,current_timestamp,?,?, 'Cross tenant','CROSS','COZINHA',true,0,current_timestamp)",
                    fixture.tenantAId(), fixture.institutionBId()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(flyway(isolated, null).migrate().migrationsExecuted).isZero();
            assertThat(latest(jdbc)).isEqualTo(CURRENT);
        });

        withDatabase("canonical_routing_fresh_", isolated -> {
            JdbcTemplate jdbc = new JdbcTemplate(isolated);
            assertThat(flyway(isolated, null).migrate().migrationsExecuted).isPositive();
            assertThat(latest(jdbc)).isEqualTo(CURRENT);
            assertThat(jdbc.queryForObject("select count(*) from information_schema.columns "
                    + "where table_name='rotas_producao_categoria' and column_name='instituicao_id'", Long.class))
                    .isEqualTo(1);
            assertThat(flyway(isolated, null).migrate().migrationsExecuted).isZero();
        });
    }

    private LegacyFixture seedLegacyRoute(JdbcTemplate jdbc) {
        long tenantA = tenant(jdbc, "A");
        long tenantB = tenant(jdbc, "B");
        long institutionA = institution(jdbc, tenantA, "A");
        long institutionB = institution(jdbc, tenantB, "B");
        long serviceA = serviceUnit(jdbc, institutionA, "A");
        long categoryA = category(jdbc, tenantA);
        long productionA = jdbc.queryForObject("insert into unidades_producao "
                        + "(version,created_at,tenant_id,instituicao_id,unidade_atendimento_id,nome,codigo,tipo,ativo,ordem,criado_em) "
                        + "values (0,current_timestamp,?,?,?,'Legacy Kitchen','GERAL','COZINHA',true,0,current_timestamp) returning id",
                Long.class, tenantA, institutionA, serviceA);
        long route = jdbc.queryForObject("insert into rotas_producao_categoria "
                        + "(version,created_at,tenant_id,categoria_produto_id,unidade_producao_id,ativo,prioridade,criado_em) "
                        + "values (0,current_timestamp,?,?,?,true,0,current_timestamp) returning id",
                Long.class, tenantA, categoryA, productionA);
        jdbc.update("insert into cozinhas (version,created_at,nome,tipo,ativa) "
                + "values (0,current_timestamp,'Legacy Kitchen','CENTRAL',true)");
        return new LegacyFixture(tenantA, institutionA, institutionB, route);
    }

    private long tenant(JdbcTemplate jdbc, String label) {
        String suffix = suffix();
        return jdbc.queryForObject("insert into tenants "
                        + "(version,created_at,nome,slug,tenant_code,tipo,estado,merchant_public_id,discovery_published) "
                        + "values (0,current_timestamp,?,?,?,'RESTAURANTE','ATIVO',?,false) returning id",
                Long.class, "Routing " + label, "routing-" + suffix,
                "R" + suffix.substring(0, 8).toUpperCase(), UUID.randomUUID());
    }

    private long institution(JdbcTemplate jdbc, long tenantId, String label) {
        String suffix = suffix();
        return jdbc.queryForObject("insert into instituicoes "
                        + "(version,created_at,tenant_id,nome,sigla,telefone_autorizacao,nif,ativa) "
                        + "values (0,current_timestamp,?,?,?,?,?,true) returning id",
                Long.class, tenantId, "Institution " + label,
                ("I" + suffix).substring(0, 9).toUpperCase(), "+2449" + suffix.substring(0, 8), "NIF-" + suffix);
    }

    private long serviceUnit(JdbcTemplate jdbc, long institutionId, String label) {
        return jdbc.queryForObject("insert into unidades_atendimento "
                        + "(version,created_at,instituicao_id,nome,tipo,ativa) "
                        + "values (0,current_timestamp,?,?,'RESTAURANTE',true) returning id",
                Long.class, institutionId, "Service " + label);
    }

    private long category(JdbcTemplate jdbc, long tenantId) {
        String suffix = suffix();
        return jdbc.queryForObject("insert into categoria_produtos "
                        + "(version,created_at,tenant_id,nome,slug,ordem,ativo,public_id) "
                        + "values (0,current_timestamp,?,'Lanches',?,0,true,?) returning id",
                Long.class, tenantId, "lanches-" + suffix, UUID.randomUUID());
    }

    private String latest(JdbcTemplate jdbc) {
        return jdbc.queryForObject("select version from flyway_schema_history where success=true "
                + "order by installed_rank desc limit 1", String.class);
    }

    private void withDatabase(String prefix, DatabaseAction action) {
        String name = prefix + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(dataSource);
        admin.execute("create database " + name);
        DriverManagerDataSource isolated = new DriverManagerDataSource();
        String base = properties.determineUrl();
        isolated.setUrl(base.substring(0, base.lastIndexOf('/') + 1) + name);
        isolated.setUsername(properties.determineUsername());
        isolated.setPassword(properties.determinePassword());
        isolated.setDriverClassName(properties.determineDriverClassName());
        try {
            action.run(isolated);
        } finally {
            admin.execute("drop database " + name + " with (force)");
        }
    }

    private Flyway flyway(DataSource source, MigrationVersion target) {
        var config = Flyway.configure().dataSource(source).locations("classpath:db/migration");
        if (target != null) config.target(target);
        return config.load();
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record LegacyFixture(long tenantAId, long institutionAId, long institutionBId, long routeId) {
    }

    private interface DatabaseAction {
        void run(DataSource source);
    }
}
