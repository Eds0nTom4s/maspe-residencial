package com.restaurante.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveSecretsValidatorTest {

    private final ApplicationContextRunner base = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(DeviceProperties.class, SensitiveSecretsValidator.class);

    @Test
    void prod_profile_blocks_default_or_missing_secrets() {
        base.withPropertyValues(
                        "spring.profiles.active=prod",
                        "consuma.device.token-hash-secret=dev-secret-change-me",
                        "consuma.sync.cursor.hmac-secret=dev-sync-cursor-secret-change-me"
                )
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void prod_profile_blocks_short_secret() {
        base.withPropertyValues(
                        "spring.profiles.active=prod",
                        "consuma.device.token-hash-secret=short",
                        "consuma.sync.cursor.hmac-secret=short"
                )
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void prod_profile_accepts_strong_secrets() {
        base.withPropertyValues(
                        "spring.profiles.active=prod",
                        "consuma.device.token-hash-secret=abcdefghijklmnopqrstuvwxyz012345",
                        "consuma.sync.cursor.hmac-secret=abcdefghijklmnopqrstuvwxyz012345"
                )
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void dev_profile_allows_default_secrets() {
        base.withPropertyValues(
                        "spring.profiles.active=dev",
                        "consuma.device.token-hash-secret=dev-secret-change-me",
                        "consuma.sync.cursor.hmac-secret=dev-sync-cursor-secret-change-me"
                )
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }
}

