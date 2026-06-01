package com.restaurante.consumo;

import com.restaurante.consumo.identificacao.service.TelefoneOtpService;
import com.restaurante.model.entity.Tenant;
import com.restaurante.model.enums.OtpPurpose;
import com.restaurante.model.enums.TenantEstado;
import com.restaurante.model.enums.TenantTipo;
import com.restaurante.consumo.identificacao.repository.TelefoneOtpChallengeRepository;
import com.restaurante.repository.TenantRepository;
import com.restaurante.testsupport.PostgresTestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.web-application-type=servlet",
                "consuma.otp.enabled=true",
                "consuma.otp.mock-enabled=true",
                "consuma.otp.hash-pepper=testpepper",
                "consuma.otp.ttl-minutes=5",
                "consuma.otp.max-attempts=5"
        }
)
@ActiveProfiles("it-postgres")
class TelefoneOtpServiceIT extends PostgresTestcontainersConfig {

    @Autowired TelefoneOtpService otpService;
    @Autowired TenantRepository tenantRepository;
    @Autowired TelefoneOtpChallengeRepository challengeRepository;

    @Test
    void requestOtp_persists_hash_not_plaintext_and_returns_debugOtp_in_mock() {
        Tenant tenant = criarTenant("Tenant OTP", "otp-tenant", "OTP");

        TelefoneOtpService.OtpRequestResult result = otpService.requestOtp(
                tenant,
                "923000000",
                OtpPurpose.IDENTIFICAR_SESSAO,
                null,
                "127.0.0.1",
                "junit"
        );

        assertThat(result.getChallenge().getId()).isNotNull();
        assertThat(result.getDebugOtp()).isNotBlank();

        var persisted = challengeRepository.findByIdAndTenant_Id(result.getChallenge().getId(), tenant.getId()).orElseThrow();
        assertThat(persisted.getOtpHash()).isNotBlank();
        assertThat(persisted.getOtpHash()).doesNotContain(result.getDebugOtp());
    }

    private Tenant criarTenant(String nome, String slug, String tenantCode) {
        Tenant t = new Tenant();
        t.setNome(nome);
        t.setSlug(slug);
        t.setTenantCode(tenantCode);
        t.setTipo(TenantTipo.RESTAURANTE);
        t.setEstado(TenantEstado.ATIVO);
        return tenantRepository.saveAndFlush(t);
    }
}
