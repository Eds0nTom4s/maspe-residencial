package com.restaurante.dto.request;

import com.restaurante.model.enums.TenantTipo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnboardingRequestCreateRequest {

    @NotBlank
    private String nomeSolicitante;

    @NotBlank
    private String telefone;

    @Email
    private String email;

    @NotBlank
    private String nomeNegocio;

    private String nif;

    @NotNull
    private TenantTipo tipoNegocio;

    @NotBlank
    private String planoCodigo;

    private BigDecimal valor;
    private String moeda;
    private String observacao;
}
