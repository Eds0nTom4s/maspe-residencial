package com.restaurante.android.foundation.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.restaurante.testsupport.PostgresTestcontainersConfig;
import java.util.List;
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
class AndroidPublicIdentityMigrationPostgresIT extends PostgresTestcontainersConfig {

    private static final MigrationVersion PREVIOUS = MigrationVersion.fromVersion("20260722.01");
    private static final String CURRENT = "20260807.01";

    @Autowired DataSource dataSource;
    @Autowired DataSourceProperties dataSourceProperties;

    @Test
    void migratesExistingDataBackfillsConstraintsIndexesAndDoesNotReapply() {
        withDatabase("android_identity_existing_", migrationDataSource -> {
            Flyway old = flyway(migrationDataSource, PREVIOUS);
            assertThat(old.migrate().migrationsExecuted).isPositive();
            JdbcTemplate jdbc = new JdbcTemplate(migrationDataSource);
            ExistingRows rows = insertExistingRows(jdbc);

            var migration = flyway(migrationDataSource, MigrationVersion.fromVersion(CURRENT)).migrate();
            assertThat(migration.migrationsExecuted).isEqualTo(2);
            assertBackfilled(jdbc);
            assertConstraintsAndIndexes(jdbc);

            UUID merchantPublicId = jdbc.queryForObject(
                    "select merchant_public_id from tenants where id = ?", UUID.class, rows.tenantA());
            UUID productPublicId = jdbc.queryForObject(
                    "select public_id from produtos where id = ?", UUID.class, rows.productA());
            assertThat(merchantPublicId).isNotNull().isNotEqualTo(productPublicId);
            assertThatThrownBy(() -> jdbc.update(
                    "update tenants set merchant_public_id = ? where id = ?", UUID.randomUUID(), rows.tenantA()))
                    .hasMessageContaining("merchant_public_id is immutable");
            assertThatThrownBy(() -> jdbc.update(
                    "update produtos set public_id = ? where id = ?", UUID.randomUUID(), rows.productA()))
                    .hasMessageContaining("public_id is immutable");
            assertThatThrownBy(() -> jdbc.update(
                    "update produtos set categoria_produto_id = ? where id = ?", rows.categoryB(), rows.productA()))
                    .hasMessageContaining("fk_produtos_tenant_categoria");

            var restart = flyway(migrationDataSource, MigrationVersion.fromVersion(CURRENT)).migrate();
            assertThat(restart.migrationsExecuted).isZero();
            assertThat(jdbc.queryForObject(
                    "select count(*) from flyway_schema_history where version = ? and success = true",
                    Long.class, CURRENT)).isEqualTo(1);
        });
    }

    @Test
    void migratesFreshDatabaseThroughCompleteChain() {
        withDatabase("android_identity_fresh_", migrationDataSource -> {
            var result = flyway(migrationDataSource, MigrationVersion.fromVersion(CURRENT)).migrate();
            JdbcTemplate jdbc = new JdbcTemplate(migrationDataSource);

            assertThat(result.migrationsExecuted).isPositive();
            assertThat(jdbc.queryForObject(
                    "select version from flyway_schema_history where success = true order by installed_rank desc limit 1",
                    String.class)).isEqualTo(CURRENT);
            assertConstraintsAndIndexes(jdbc);
            assertThat(flyway(migrationDataSource, MigrationVersion.fromVersion(CURRENT)).migrate().migrationsExecuted).isZero();
        });
    }

    private ExistingRows insertExistingRows(JdbcTemplate jdbc) {
        long tenantA = insertTenant(jdbc, "A");
        long tenantB = insertTenant(jdbc, "B");
        long categoryA = insertCategory(jdbc, tenantA, "A");
        long categoryB = insertCategory(jdbc, tenantB, "B");
        long productA = insertProduct(jdbc, tenantA, categoryA, "A");
        insertProduct(jdbc, tenantB, categoryB, "B");
        insertOrder(jdbc, tenantA, "A");
        insertOrder(jdbc, tenantB, "B");
        return new ExistingRows(tenantA, categoryB, productA);
    }

    private long insertTenant(JdbcTemplate jdbc, String suffix) {
        return jdbc.queryForObject("""
                insert into tenants (version, created_at, nome, slug, tenant_code, tipo, estado)
                values (0, current_timestamp, ?, ?, ?, 'RESTAURANTE', 'ATIVO') returning id
                """, Long.class, "Migration Merchant " + suffix,
                "migration-merchant-" + suffix.toLowerCase(), "MIG" + suffix);
    }

    private long insertCategory(JdbcTemplate jdbc, long tenantId, String suffix) {
        return jdbc.queryForObject("""
                insert into categoria_produtos
                    (version, created_at, tenant_id, nome, slug, ordem, ativo)
                values (0, current_timestamp, ?, ?, ?, 1, true) returning id
                """, Long.class, tenantId, "Migration Category " + suffix,
                "migration-category-" + suffix.toLowerCase());
    }

    private long insertProduct(JdbcTemplate jdbc, long tenantId, long categoryId, String suffix) {
        return jdbc.queryForObject("""
                insert into produtos
                    (version, created_at, tenant_id, categoria_produto_id, categoria,
                     codigo, nome, preco, disponivel, ativo)
                values (0, current_timestamp, ?, ?, 'OUTROS', ?, ?, 100.00, true, true) returning id
                """, Long.class, tenantId, categoryId, "MIG-PROD-" + suffix, "Migration Product " + suffix);
    }

    private void insertOrder(JdbcTemplate jdbc, long tenantId, String suffix) {
        jdbc.update("""
                insert into pedidos
                    (version, created_at, tenant_id, numero, status, status_financeiro, tipo_pagamento, total)
                values (0, current_timestamp, ?, ?, 'CRIADO', 'NAO_PAGO', 'POS_PAGO', 100.00)
                """, tenantId, "MIG-PED-" + suffix);
    }

    private void assertBackfilled(JdbcTemplate jdbc) {
        assertNoNullOrDuplicate(jdbc, "tenants", "merchant_public_id");
        assertNoNullOrDuplicate(jdbc, "categoria_produtos", "public_id");
        assertNoNullOrDuplicate(jdbc, "produtos", "public_id");
        assertNoNullOrDuplicate(jdbc, "pedidos", "public_id");
    }

    private void assertNoNullOrDuplicate(JdbcTemplate jdbc, String table, String column) {
        assertThat(jdbc.queryForObject(
                "select count(*) from " + table + " where " + column + " is null", Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from (select " + column + " from " + table
                        + " group by " + column + " having count(*) > 1) duplicates", Long.class)).isZero();
    }

    private void assertConstraintsAndIndexes(JdbcTemplate jdbc) {
        List<String> constraints = jdbc.queryForList("""
                select conname from pg_constraint where conname in (
                    'uq_tenants_merchant_public_id', 'uq_produtos_public_id',
                    'uq_categoria_produtos_public_id', 'uq_pedidos_public_id',
                    'fk_produtos_tenant_categoria') order by conname
                """, String.class);
        assertThat(constraints).containsExactlyInAnyOrder(
                "uq_tenants_merchant_public_id", "uq_produtos_public_id",
                "uq_categoria_produtos_public_id", "uq_pedidos_public_id",
                "fk_produtos_tenant_categoria");

        List<String> indexes = jdbc.queryForList("""
                select indexname from pg_indexes where indexname in (
                    'idx_produtos_tenant_public_id', 'idx_categoria_produtos_tenant_public_id',
                    'idx_pedidos_tenant_public_id')
                """, String.class);
        assertThat(indexes).containsExactlyInAnyOrder(
                "idx_produtos_tenant_public_id", "idx_categoria_produtos_tenant_public_id",
                "idx_pedidos_tenant_public_id");

        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public'
                  and ((table_name = 'tenants' and column_name = 'merchant_public_id')
                    or (table_name in ('produtos','categoria_produtos','pedidos') and column_name = 'public_id'))
                  and is_nullable = 'NO' and data_type = 'uuid'
                """, Long.class)).isEqualTo(4);
    }

    private void withDatabase(String prefix, DatabaseAction action) {
        String databaseName = prefix + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(dataSource);
        admin.execute("create database " + databaseName);
        DriverManagerDataSource isolated = new DriverManagerDataSource();
        String sourceUrl = dataSourceProperties.determineUrl();
        isolated.setUrl(sourceUrl.substring(0, sourceUrl.lastIndexOf('/') + 1) + databaseName);
        isolated.setUsername(dataSourceProperties.determineUsername());
        isolated.setPassword(dataSourceProperties.determinePassword());
        isolated.setDriverClassName(dataSourceProperties.determineDriverClassName());
        try {
            action.run(isolated);
        } finally {
            admin.execute("drop database " + databaseName + " with (force)");
        }
    }

    private Flyway flyway(DataSource migrationDataSource, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(migrationDataSource)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private record ExistingRows(long tenantA, long categoryB, long productA) {
    }

    @FunctionalInterface
    private interface DatabaseAction {
        void run(DataSource dataSource);
    }
}
