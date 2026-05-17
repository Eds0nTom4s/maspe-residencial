package com.restaurante.tools;

import com.restaurante.SistemaRestauracaoApplication;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;

/**
 * Geração manual do baseline Flyway (V1) a partir do metadata JPA/Hibernate.
 *
 * Como rodar (manual):
 * - mvn -q -Dtest=GenerateFlywayBaselineSchemaTest test
 *
 * Observação:
 * - Este teste é marcado como {@code manual} e não deve rodar no pipeline normal de `mvn test`.
 * - A geração via PostgreSQL + pg_dump é preferível quando Docker está disponível.
 *   Este gerador existe como fallback reproduzível quando Docker/pg_dump não estão acessíveis no ambiente.
 */
@Tag("manual")
class GenerateFlywayBaselineSchemaTest {

    @Test
    void generateBaselineSqlToFlywayV1() throws IOException {
        Assumptions.assumeTrue(
                Boolean.getBoolean("consuma.baseline.generate"),
                "Geração de baseline é manual. Rode com -Dconsuma.baseline.generate=true"
        );

        Path migrationFile = Path.of("src/main/resources/db/migration/V1__baseline_schema.sql");
        Path tmpFile = Path.of("target/baseline/V1__baseline_schema.generated.sql");

        Files.createDirectories(tmpFile.getParent());
        Files.deleteIfExists(tmpFile);

        SpringApplication app = new SpringApplication(SistemaRestauracaoApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);

        try (ConfigurableApplicationContext ignored = app.run(
                "--spring.main.banner-mode=off",
                "--spring.profiles.active=test",
                "--spring.flyway.enabled=false",

                // Evita falhas de bootstrap por beans que consultam repositórios no startup.
                // O schema é criado em H2 apenas para permitir inicialização do contexto.
                "--spring.datasource.url=jdbc:h2:mem:baselinegen;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--spring.jpa.hibernate.ddl-auto=create-drop",

                // Gera script a partir do metadata e cria schema no H2 para permitir bootstrap.
                "--spring.jpa.properties.jakarta.persistence.schema-generation.database.action=create",
                "--spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create",
                "--spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-source=metadata",
                "--spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=" + tmpFile.toAbsolutePath(),

                // Gera DDL "PostgreSQL-like" (mesmo que o datasource real do profile test seja H2).
                "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
                "--spring.jpa.show-sql=false",
                "--spring.sql.init.mode=never",
                "--spring.task.scheduling.enabled=false"
        )) {
            // O bootstrap do JPA dispara a geração do script no tmpFile.
        }

        String ddl = Files.readString(tmpFile, StandardCharsets.UTF_8).trim();
        if (ddl.isBlank()) {
            throw new IllegalStateException("DDL gerado está vazio. Verifique propriedades de schema-generation.");
        }

        String header = String.join("\n",
                "-- CONSUMA baseline schema",
                "-- Generated from current JPA/Hibernate metadata before Tenant Core refactor",
                "-- Date: " + LocalDate.now(),
                "-- Do not edit manually unless reviewed",
                ""
        );

        // Limpesa mínima: remover linhas SET/COMMENT/privileges caso apareçam (varia por provider).
        String cleaned = ddl
                .replaceAll("(?m)^\\s*--.*$", "")
                .replaceAll("(?m)^\\s*set\\s+.*;$", "")
                .replaceAll("(?m)^\\s*SET\\s+.*;$", "")
                .replaceAll("(?m)^\\s*comment\\s+on\\s+.*;$", "")
                .replaceAll("(?m)^\\s*COMMENT\\s+ON\\s+.*;$", "")
                .replaceAll("(?m)^\\s*$\\n", "")
                .trim();

        Files.deleteIfExists(migrationFile);
        Files.createDirectories(migrationFile.getParent());
        Files.writeString(
                migrationFile,
                header + cleaned + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }
}
