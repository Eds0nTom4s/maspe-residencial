package com.restaurante.dto.response;

import com.restaurante.model.enums.OnboardingPaymentStatus;
import com.restaurante.model.enums.OnboardingRequestStatus;
import com.restaurante.model.enums.TenantTipo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnboardingRequestResponse {

    private Long id;
    private String nomeSolicitante;
    private String telefone;
    private String email;
    private String nomeNegocio;
    private String nif;
    private TenantTipo tipoNegocio;
    private String planoCodigo;
    private String planoNome;
    private Long businessAccountId;
    private String businessAccountNome;
    private Long tenantId;
    private String tenantNome;
    private OnboardingRequestStatus status;
    private OnboardingPaymentStatus statusPagamento;
    private BigDecimal valor;
    private String moeda;
    private String observacao;
    private String motivoRejeicao;
    private String notificationStatus;
    private String notificationMessage;
    private LocalDateTime approvedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime activatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
