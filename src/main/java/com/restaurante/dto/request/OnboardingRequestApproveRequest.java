package com.restaurante.dto.request;

import com.restaurante.model.enums.OnboardingPaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnboardingRequestApproveRequest {

    private Long businessAccountId;
    private Boolean criarBusinessAccountSeAusente;
    private String businessAccountSlug;
    private Long tenantId;
    private OnboardingPaymentStatus statusPagamento;
    private String observacao;
}
