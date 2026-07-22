package com.restaurante.platform;

import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("it-postgres")
class PlatformOnboardingCanonicalMigrationPostgresIT extends PostgresTestcontainersConfig {
    @Autowired DataSource dataSource;
    @Autowired DataSourceProperties properties;

    @Test
    void v85PreservesLegacyStatesAndDuplicateNifsAndIsRestartSafe() {
        String databaseName = "migration_v85_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate database = new JdbcTemplate(dataSource);
        database.execute("create database " + databaseName);
        DriverManagerDataSource isolated = new DriverManagerDataSource();
        String sourceUrl = properties.determineUrl();
        isolated.setUrl(sourceUrl.substring(0, sourceUrl.lastIndexOf('/') + 1) + databaseName);
        isolated.setUsername(properties.determineUsername());
        isolated.setPassword(properties.determinePassword());
        isolated.setDriverClassName(properties.determineDriverClassName());
        try {
            assertThat(flyway(isolated, MigrationVersion.fromVersion("84")).migrate().migrationsExecuted)
                    .isPositive();
            JdbcTemplate jdbc = new JdbcTemplate(isolated);
            List<String> states = List.of("PENDENTE", "APROVADO", "ATIVADO", "REJEITADO",
                    "AGUARDANDO_PAGAMENTO");
            for (int i = 0; i < states.size(); i++) {
                jdbc.update("""
                        insert into onboarding_requests
                            (version, created_at, nome_solicitante, telefone, nome_negocio, nif,
                             status, status_pagamento, moeda)
                        values (0, current_timestamp, ?, '+244900000000', ?, 'DUPLICATED LEGACY NIF',
                                ?, 'NAO_APLICAVEL', 'AOA')
                        """, "Legacy " + i, "Legacy business " + i, states.get(i));
            }

            assertThat(flyway(isolated, null).migrate().migrationsExecuted).isEqualTo(2);
            assertThat(jdbc.queryForList("select status from onboarding_requests order by id", String.class))
                    .containsExactlyElementsOf(states);
            assertThat(jdbc.queryForObject("""
                    select count(*) from onboarding_requests
                    where contract_version is null and completed_at is null and normalized_nif is null
                    """, Long.class)).isEqualTo(5);
            assertThat(jdbc.queryForObject("""
                    select count(*) from information_schema.tables
                    where table_schema = 'public'
                      and table_name in ('onboarding_command_records', 'onboarding_nif_reservations')
                    """, Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("""
                    select count(*) from flyway_schema_history where version = '85' and success = true
                    """, Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    select count(*) from information_schema.columns
                    where table_schema = 'public' and table_name = 'tenant_users'
                      and column_name = 'access_origin'
                    """, Long.class)).isEqualTo(1);
            assertThat(flyway(isolated, null).migrate().migrationsExecuted).isZero();
        } finally {
            database.execute("drop database " + databaseName + " with (force)");
        }
    }

    private Flyway flyway(DataSource source, MigrationVersion target) {
        var configuration = Flyway.configure().dataSource(source).locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }
}
