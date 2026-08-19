package com.restaurante.ponto;

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
class PontoReleaseCandidateMigrationPostgresIT extends PostgresTestcontainersConfig {
    private static final MigrationVersion PREVIOUS = MigrationVersion.fromVersion("20260808.01");
    private static final String CURRENT = "20260809.01";

    @Autowired DataSource dataSource;
    @Autowired DataSourceProperties properties;

    @Test
    void migratesFreshAndExistingSchemaPreservingLegacyDataAndRestartsCleanly() {
        withDatabase("ponto_rc_existing_", isolated -> {
            flyway(isolated, PREVIOUS).migrate();
            JdbcTemplate jdbc = new JdbcTemplate(isolated);
            seedLegacyVariation(jdbc);
            long legacyBefore = jdbc.queryForObject("select count(*) from variacoes_produto", Long.class);

            assertThat(flyway(isolated, null).migrate().migrationsExecuted).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from variacoes_produto", Long.class))
                    .isEqualTo(legacyBefore);
            assertSchema(jdbc);
            assertThat(flyway(isolated, null).migrate().migrationsExecuted).isZero();
            assertThat(latest(jdbc)).isEqualTo(CURRENT);
        });

        withDatabase("ponto_rc_fresh_", isolated -> {
            JdbcTemplate jdbc = new JdbcTemplate(isolated);
            assertThat(flyway(isolated, null).migrate().migrationsExecuted).isPositive();
            assertSchema(jdbc);
            assertThat(latest(jdbc)).isEqualTo(CURRENT);
            assertThat(flyway(isolated, null).migrate().migrationsExecuted).isZero();
        });
    }

    private void seedLegacyVariation(JdbcTemplate jdbc) {
        long tenantId = jdbc.queryForObject("insert into tenants "
                        + "(version, created_at, nome, slug, tenant_code, tipo, estado, merchant_public_id, discovery_published) "
                        + "values (0, current_timestamp, 'Legacy Ponto', ?, ?, 'RESTAURANTE', 'ATIVO', ?, false) returning id",
                Long.class, "legacy-ponto-" + suffix(), "LPR" + suffix().substring(0, 5), UUID.randomUUID());
        long categoryId = jdbc.queryForObject("insert into categoria_produtos "
                        + "(version, created_at, tenant_id, nome, slug, ordem, ativo, public_id) "
                        + "values (0, current_timestamp, ?, 'Legacy', ?, 0, true, ?) returning id",
                Long.class, tenantId, "legacy-category-" + suffix(), UUID.randomUUID());
        long productId = jdbc.queryForObject("insert into produtos "
                        + "(version, created_at, tenant_id, categoria_produto_id, categoria, codigo, nome, preco, disponivel, ativo, public_id) "
                        + "values (0, current_timestamp, ?, ?, 'OUTROS', ?, 'Legacy', 100.00, true, true, ?) returning id",
                Long.class, tenantId, categoryId, "LEG-" + suffix(), UUID.randomUUID());
        jdbc.update("insert into variacoes_produto "
                + "(version, created_at, produto_id, tipo, valor, ativo) "
                + "values (0, current_timestamp, ?, 'TAMANHO', 'M', true)", productId);
    }

    private void assertSchema(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_name in "
                + "('tenant_pdv_order_idempotency_records','tenant_payment_confirmation_idempotency_records')", Long.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where "
                + "(table_name='users' and column_name in ('must_change_password','password_reset_required',"
                + "'temporary_password_expires_at','last_password_changed_at')) or "
                + "(table_name='ordens_pagamento' and column_name in ('metodo_confirmado','valor_recebido','troco')) or "
                + "(table_name='caixa_operador_sessions' and column_name='channel') or "
                + "(table_name='fiscal_documents' and column_name in ('public_share_token_hash','public_share_expires_at'))",
                Long.class)).isEqualTo(10);
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
        try { action.run(isolated); }
        finally { admin.execute("drop database " + name + " with (force)"); }
    }

    private Flyway flyway(DataSource source, MigrationVersion target) {
        var config = Flyway.configure().dataSource(source).locations("classpath:db/migration");
        if (target != null) config.target(target);
        return config.load();
    }

    private String suffix() { return UUID.randomUUID().toString().replace("-", "").substring(0, 8); }
    private interface DatabaseAction { void run(DataSource source); }
}
