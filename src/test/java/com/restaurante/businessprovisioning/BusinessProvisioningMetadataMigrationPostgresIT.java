package com.restaurante.businessprovisioning;

import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("it-postgres")
class BusinessProvisioningMetadataMigrationPostgresIT extends PostgresTestcontainersConfig {

    @Autowired DataSource dataSource;
    @Autowired DataSourceProperties dataSourceProperties;

    @Test
    void migratesV82OperationMetadataAndDoesNotReapplyOnRestart() {
        String databaseName = "migration_v84_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate database = new JdbcTemplate(dataSource);
        database.execute("create database " + databaseName);
        DriverManagerDataSource migrationDataSource = new DriverManagerDataSource();
        String sourceUrl = dataSourceProperties.determineUrl();
        migrationDataSource.setUrl(sourceUrl.substring(0, sourceUrl.lastIndexOf('/') + 1) + databaseName);
        migrationDataSource.setUsername(dataSourceProperties.determineUsername());
        migrationDataSource.setPassword(dataSourceProperties.determinePassword());
        migrationDataSource.setDriverClassName(dataSourceProperties.determineDriverClassName());
        try {
            Flyway toV82 = flyway(migrationDataSource, MigrationVersion.fromVersion("82"));
            assertThat(toV82.migrate().migrationsExecuted).isPositive();
            JdbcTemplate jdbc = new JdbcTemplate(migrationDataSource);
            long accountId = jdbc.queryForObject("""
                    insert into business_accounts (version, created_at, nome, slug, max_tenants, estado)
                    values (0, current_timestamp, 'Migration account', ?, 1, 'RASCUNHO')
                    returning id
                    """, Long.class, "migration-account-" + databaseName);
            String previewId = UUID.randomUUID().toString();
            jdbc.update("""
                    insert into business_provisioning_previews
                        (version, created_at, preview_id, business_account_id, idempotency_key,
                         request_fingerprint, contract_version, template_code, template_version,
                         plano_codigo, payload_json, result_json, status, expires_at,
                         correlation_id, actor_roles)
                    values (0, current_timestamp, ?, ?, 'migration-preview', ?, 'ACCOUNT_V1',
                            'CONSUMA_PONTO_V1', 1, 'PILOTO', '{}', '{}', 'READY',
                            current_timestamp + interval '1 hour', 'migration-correlation', 'ROLE_ADMIN')
                    """, previewId, accountId, "a".repeat(64));
            insertOperation(jdbc, accountId, previewId, "PENDING", "pending", null, null);
            insertOperation(jdbc, accountId, previewId, "RUNNING", "running", null, null);
            insertOperation(jdbc, accountId, previewId, "SUCCEEDED", "succeeded", null, "{\"ok\":true}");
            insertOperation(jdbc, accountId, previewId, "FAILED_FINAL", "failed-final", "FINAL", null);
            insertOperation(jdbc, accountId, previewId, "FAILED_RETRYABLE", "failed-retryable", "RETRY", null);

            var migration = flyway(migrationDataSource, null).migrate();
            assertThat(migration.migrationsExecuted).isEqualTo(3);
            assertMetadata(jdbc, "PENDING", 0, false);
            assertMetadata(jdbc, "RUNNING", 1, false);
            assertMetadata(jdbc, "SUCCEEDED", 1, true);
            assertMetadata(jdbc, "FAILED_FINAL", 1, false);
            assertMetadata(jdbc, "FAILED_RETRYABLE", 1, false);
            assertThat(jdbc.queryForObject("""
                    select count(*) from business_provisioning_operations
                    where operation_id like 'migration-%'
                      and tenant_id is null
                      and request_fingerprint = ?
                    """, Long.class, "b".repeat(64))).isEqualTo(5);
            assertThat(jdbc.queryForObject("""
                    select result_json from business_provisioning_operations
                    where operation_id = 'migration-succeeded'
                    """, String.class)).isEqualTo("{\"ok\":true}");
            assertThat(jdbc.queryForObject("""
                    select count(*) from business_provisioning_operations
                    where operation_id in ('migration-pending', 'migration-running')
                      and lease_until is null and next_retry_at is null
                      and effects_committed = false
                    """, Long.class)).isEqualTo(2);

            var restart = flyway(migrationDataSource, null).migrate();
            assertThat(restart.migrationsExecuted).isZero();
            assertThat(jdbc.queryForObject("""
                    select count(*) from flyway_schema_history
                    where version in ('83', '84', '85') and success = true
                    """, Long.class)).isEqualTo(3);
        } finally {
            database.execute("drop database " + databaseName + " with (force)");
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

    private void insertOperation(JdbcTemplate jdbc, long accountId, String previewId, String status,
                                 String suffix, String errorCode, String resultJson) {
        jdbc.update("""
                insert into business_provisioning_operations
                    (version, created_at, operation_id, business_account_id, idempotency_key,
                     request_fingerprint, preview_id, status, started_at, completed_at,
                     error_code, error_message, result_json, correlation_id, actor_roles)
                values (0, current_timestamp, ?, ?, ?, ?, ?, ?, current_timestamp,
                        case when ? in ('SUCCEEDED', 'FAILED_FINAL', 'FAILED_RETRYABLE')
                             then current_timestamp else null end,
                        ?, ?, ?, 'migration-correlation', 'ROLE_ADMIN')
                """, "migration-" + suffix, accountId, "migration-" + suffix, "b".repeat(64), previewId,
                status, status, errorCode, errorCode, resultJson);
    }

    private void assertMetadata(JdbcTemplate jdbc, String status, int attemptCount, boolean effectsCommitted) {
        assertThat(jdbc.queryForObject("""
                select attempt_count from business_provisioning_operations
                where operation_id = ?
                """, Integer.class, "migration-" + status.toLowerCase().replace('_', '-')))
                .isEqualTo(attemptCount);
        assertThat(jdbc.queryForObject("""
                select effects_committed from business_provisioning_operations
                where operation_id = ?
                """, Boolean.class, "migration-" + status.toLowerCase().replace('_', '-')))
                .isEqualTo(effectsCommitted);
    }
}
