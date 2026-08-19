package com.restaurante.android.options;

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
class CanonicalProductOptionsMigrationPostgresIT extends PostgresTestcontainersConfig {
    private static final MigrationVersion PREVIOUS = MigrationVersion.fromVersion("20260807.01");
    private static final String CURRENT = "20260809.01";

    @Autowired DataSource dataSource;
    @Autowired DataSourceProperties properties;

    @Test
    void migratesFreshAndExistingDatabasesWithoutTouchingLegacyVariations() {
        withDatabase("options_existing_", isolated -> {
            flyway(isolated, PREVIOUS).migrate();
            JdbcTemplate jdbc = new JdbcTemplate(isolated);
            ExistingRows rows = insertLegacyRows(jdbc);
            assertThat(flyway(isolated, null).migrate().migrationsExecuted).isEqualTo(2);

            assertThat(jdbc.queryForObject("select count(*) from variacoes_produto", Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from product_option_groups", Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from product_options", Long.class)).isZero();
            assertThatThrownBy(() -> jdbc.update("insert into product_option_groups "
                    + "(created_at, tenant_id, produto_id, name, min_selections, max_selections, sort_order, active) "
                    + "values (current_timestamp, ?, ?, 'cross', 0, 1, 0, true)", rows.tenantA(), rows.productB()))
                    .hasMessageContaining("fk_product_option_groups_product_tenant");
            assertSchema(jdbc);
            assertThat(flyway(isolated, null).migrate().migrationsExecuted).isZero();
        });
        withDatabase("options_fresh_", isolated -> {
            JdbcTemplate jdbc = new JdbcTemplate(isolated);
            assertThat(flyway(isolated, null).migrate().migrationsExecuted).isPositive();
            assertThat(jdbc.queryForObject("select version from flyway_schema_history where success=true "
                    + "order by installed_rank desc limit 1", String.class)).isEqualTo(CURRENT);
            assertSchema(jdbc);
            assertThat(flyway(isolated, null).migrate().migrationsExecuted).isZero();
        });
    }

    private ExistingRows insertLegacyRows(JdbcTemplate jdbc) {
        long tenantA = tenant(jdbc, "A");
        long tenantB = tenant(jdbc, "B");
        long categoryA = category(jdbc, tenantA, "A");
        long categoryB = category(jdbc, tenantB, "B");
        long productA = product(jdbc, tenantA, categoryA, "A");
        long productB = product(jdbc, tenantB, categoryB, "B");
        variation(jdbc, productA, "A");
        variation(jdbc, productB, "B");
        return new ExistingRows(tenantA, productB);
    }

    private long tenant(JdbcTemplate jdbc, String label) {
        return jdbc.queryForObject("insert into tenants (version, created_at, nome, slug, tenant_code, tipo, estado, merchant_public_id, discovery_published) "
                + "values (0, current_timestamp, ?, ?, ?, 'RESTAURANTE', 'ATIVO', ?, false) returning id", Long.class,
                "Legacy " + label, "legacy-options-" + label.toLowerCase(), "LOPT" + label, UUID.randomUUID());
    }
    private long category(JdbcTemplate jdbc, long tenantId, String label) {
        return jdbc.queryForObject("insert into categoria_produtos (version, created_at, tenant_id, nome, slug, ordem, ativo, public_id) "
                + "values (0, current_timestamp, ?, ?, ?, 0, true, ?) returning id", Long.class,
                tenantId, "Legacy category " + label, "legacy-options-category-" + label.toLowerCase(), UUID.randomUUID());
    }
    private long product(JdbcTemplate jdbc, long tenantId, long categoryId, String label) {
        return jdbc.queryForObject("insert into produtos (version, created_at, tenant_id, categoria_produto_id, categoria, codigo, nome, preco, disponivel, ativo, public_id) "
                + "values (0, current_timestamp, ?, ?, 'OUTROS', ?, ?, 100.00, true, true, ?) returning id", Long.class,
                tenantId, categoryId, "LOPT-" + label, "Legacy product " + label, UUID.randomUUID());
    }
    private void variation(JdbcTemplate jdbc, long productId, String label) {
        jdbc.update("insert into variacoes_produto (version, created_at, produto_id, tipo, valor, ativo) "
                + "values (0, current_timestamp, ?, 'TAMANHO', ?, true)", productId, "Legacy " + label);
    }

    private void assertSchema(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name in "
                + "('product_option_groups','product_options') and column_name='public_id' and is_nullable='NO' and data_type='uuid'", Long.class))
                .isEqualTo(2);
        List<String> constraints = jdbc.queryForList("select conname from pg_constraint where conname in "
                + "('fk_product_option_groups_product_tenant','fk_product_options_group_tenant',"
                + "'chk_product_option_groups_min_max','chk_product_options_price_nonnegative')", String.class);
        assertThat(constraints).containsExactlyInAnyOrder("fk_product_option_groups_product_tenant",
                "fk_product_options_group_tenant", "chk_product_option_groups_min_max", "chk_product_options_price_nonnegative");
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

    private record ExistingRows(long tenantA, long productB) { }
    @FunctionalInterface private interface DatabaseAction { void run(DataSource dataSource); }
}
