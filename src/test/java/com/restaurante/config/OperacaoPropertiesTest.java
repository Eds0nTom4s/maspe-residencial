package com.restaurante.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class OperacaoPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(OperacaoProperties.class);

    @Test
    void turnoObrigatorioDefaultsToTrueAndCurrentShiftScope() {
        contextRunner.run(ctx -> {
            OperacaoProperties properties = ctx.getBean(OperacaoProperties.class);
            assertThat(properties.isTurnoObrigatorio()).isTrue();
            assertThat(properties.getPedidosEscopo()).isEqualTo("TURNO_ATUAL");
            assertThat(properties.isExtratoTurnoEnabled()).isTrue();
        });
    }

    @Test
    void turnoObrigatorioCanBeDisabledForLegacyMode() {
        contextRunner
                .withPropertyValues(
                        "consuma.operacao.turno-obrigatorio=false",
                        "consuma.operacao.pedidos-escopo=historico",
                        "consuma.operacao.extrato-turno-enabled=false"
                )
                .run(ctx -> {
                    OperacaoProperties properties = ctx.getBean(OperacaoProperties.class);
                    assertThat(properties.isTurnoObrigatorio()).isFalse();
                    assertThat(properties.getPedidosEscopo()).isEqualTo("HISTORICO");
                    assertThat(properties.isExtratoTurnoEnabled()).isFalse();
                });
    }
}
