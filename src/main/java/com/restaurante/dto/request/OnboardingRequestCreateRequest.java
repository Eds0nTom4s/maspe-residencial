package com.restaurante.dto.request;

import com.restaurante.model.enums.TenantTipo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    @NotBlank @Size(max = 160)
    private String nomeSolicitante;

    @NotBlank @Size(max = 30)
    private String telefone;

    @Email @Size(max = 120)
    private String email;

    @NotBlank @Size(max = 160)
    private String nomeNegocio;

    @Size(max = 30)
    private String nif;

    @NotNull
    private TenantTipo tipoNegocio;

    @NotBlank @Size(max = 30)
    private String planoCodigo;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal valor;
    @Pattern(regexp = "(?i)[A-Z]{3}")
    private String moeda;
    @Size(max = 500)
    private String observacao;
}
