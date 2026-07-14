package com.restaurante.financeiro.reconciliation;

import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest @ActiveProfiles("it-postgres")
class ReconciliationAdministrativeHardeningPostgresIT extends PostgresTestcontainersConfig {
 @Autowired JdbcTemplate jdbc;
 @Test void migration81PersisteFingerprintEFksSemCascade(){
   Integer fingerprint=jdbc.queryForObject("select count(*) from information_schema.columns where table_name='pagamento_reconciliation_case_events' and column_name='command_fingerprint'",Integer.class);
   Integer fks=jdbc.queryForObject("select count(*) from information_schema.table_constraints where table_name='pagamento_reconciliation_case_events' and constraint_type='FOREIGN KEY' and constraint_name in ('fk_reconciliation_event_tenant','fk_reconciliation_event_payment','fk_reconciliation_event_pedido','fk_reconciliation_event_actor')",Integer.class);
   Integer cascades=jdbc.queryForObject("select count(*) from information_schema.referential_constraints where constraint_name like 'fk_reconciliation_event_%' and delete_rule='CASCADE'",Integer.class);
   Integer audit=jdbc.queryForObject("select count(*) from information_schema.tables where table_name='reconciliation_materialization_audits'",Integer.class);
   assertThat(fingerprint).isEqualTo(1);assertThat(fks).isEqualTo(4);assertThat(cascades).isZero();assertThat(audit).isEqualTo(1);
 }
 @Test void casoActivoContinuaProtegidoPorIndiceUnicoParcial(){
   String definition=jdbc.queryForObject("select indexdef from pg_indexes where indexname='ux_reconciliation_case_active_payment'",String.class);
   assertThat(definition).contains("UNIQUE").contains("WHERE active");
 }
}
