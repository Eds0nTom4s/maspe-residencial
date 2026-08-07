package com.restaurante.android.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.restaurante.testsupport.PostgresTestcontainersConfig;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("it-postgres")
class AndroidDiscoveryPublicationMigrationPostgresIT extends PostgresTestcontainersConfig {

    private static final MigrationVersion PREVIOUS = MigrationVersion.fromVersion("20260806.01");
    private static final String CURRENT = "20260807.01";

    @Autowired DataSource dataSource;
    @Autowired DataSourceProperties properties;

    @Test
    void migratesExistingTenantsAsOptedOutAndIsRestartSafe() {
        withDatabase("discovery_existing_", isolated -> {
            flyway(isolated, PREVIOUS).migrate();
            JdbcTemplate jdbc = new JdbcTemplate(isolated);
            jdbc.update("""
                    insert into tenants (version, created_at, nome, slug, tenant_code, tipo, estado, merchant_public_id)
                    values (0, current_timestamp, 'Existing', 'existing', 'EXISTING', 'RESTAURANTE', 'ATIVO', ?)
                    """, UUID.randomUUID());

            assertThat(flyway(isolated, null).migrate().migrationsExecuted).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "select discovery_published from tenants where tenant_code='EXISTING'", Boolean.class))
                    .isFalse();
            assertSchema(jdbc);
            assertThat(flyway(isolated, null).migrate().migrationsExecuted).isZero();
        });
    }

    @Test
    void migratesFreshDatabaseThroughCompleteChain() {
        withDatabase("discovery_fresh_", isolated -> {
            JdbcTemplate jdbc = new JdbcTemplate(isolated);
            assertThat(flyway(isolated, null).migrate().migrationsExecuted).isPositive();
            assertThat(jdbc.queryForObject(
                    "select version from flyway_schema_history where success=true order by installed_rank desc limit 1",
                    String.class)).isEqualTo(CURRENT);
            assertSchema(jdbc);
            assertThat(flyway(isolated, null).migrate().migrationsExecuted).isZero();
        });
    }

    private void assertSchema(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema='public' and table_name='tenants'
                  and column_name='discovery_published' and is_nullable='NO'
                  and column_default='false'
                """, Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from pg_indexes where schemaname='public'
                  and indexname='idx_tenants_discovery_name_public'
                """, Long.class)).isEqualTo(1);
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

    private Flyway flyway(DataSource isolated, MigrationVersion target) {
        var config = Flyway.configure().dataSource(isolated).locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    @FunctionalInterface
    private interface DatabaseAction {
        void run(DataSource dataSource);
    }
}
