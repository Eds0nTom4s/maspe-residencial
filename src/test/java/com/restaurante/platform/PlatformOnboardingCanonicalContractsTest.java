package com.restaurante.platform;

import com.restaurante.dto.business.BusinessProvisioningContracts.BusinessVertical;
import com.restaurante.dto.request.OnboardingRequestApproveRequest;
import com.restaurante.dto.request.OnboardingRequestCreateRequest;
import com.restaurante.dto.request.OnboardingRequestRejectRequest;
import com.restaurante.model.enums.OnboardingAccountChoice;
import com.restaurante.model.enums.OnboardingRequestStatus;
import com.restaurante.model.enums.TenantTipo;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformOnboardingCanonicalContractsTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void canonicalAndLegacyStatesRemainExplicit() {
        assertThat(EnumSet.allOf(OnboardingRequestStatus.class)).contains(
                OnboardingRequestStatus.PENDENTE, OnboardingRequestStatus.APROVADO,
                OnboardingRequestStatus.CONCLUIDO, OnboardingRequestStatus.REJEITADO,
                OnboardingRequestStatus.CANCELADO, OnboardingRequestStatus.ATIVADO,
                OnboardingRequestStatus.AGUARDANDO_APROVACAO,
                OnboardingRequestStatus.AGUARDANDO_PAGAMENTO);
    }

    @Test
    void createRejectsNegativeMoneyAndMalformedCurrency() {
        OnboardingRequestCreateRequest request = OnboardingRequestCreateRequest.builder()
                .nomeSolicitante("Applicant").telefone("+244900000000").nomeNegocio("Business")
                .tipoNegocio(TenantTipo.RESTAURANTE).planoCodigo("PILOTO")
                .valor(new BigDecimal("-0.01")).moeda("A0A").build();
        assertThat(validator.validate(request)).extracting(v -> v.getPropertyPath().toString())
                .contains("valor", "moeda");
    }

    @Test
    void mutableCommandsRequireExplicitVersionsAndDecisions() {
        OnboardingRequestApproveRequest approve = OnboardingRequestApproveRequest.builder()
                .accountChoice(OnboardingAccountChoice.CREATE_NEW)
                .confirmedPlanCode("PILOTO").vertical(BusinessVertical.CONSUMA_PONTO)
                .reason("approval").build();
        OnboardingRequestRejectRequest reject = OnboardingRequestRejectRequest.builder()
                .reason("reject").build();
        assertThat(validator.validate(approve)).anyMatch(v -> v.getPropertyPath().toString().equals("onboardingVersion"));
        assertThat(validator.validate(reject)).anyMatch(v -> v.getPropertyPath().toString().equals("onboardingVersion"));
    }

    @Test
    void verticalIsIndependentFromTenantType() {
        assertThat(BusinessVertical.values()).containsExactly(
                BusinessVertical.CONSUMA_PONTO, BusinessVertical.CONSUMA_REST);
        assertThat(EnumSet.allOf(TenantTipo.class)).contains(TenantTipo.RESTAURANTE, TenantTipo.LOJA);
    }
}
