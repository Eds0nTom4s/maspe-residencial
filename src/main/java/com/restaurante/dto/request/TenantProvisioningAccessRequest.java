package com.restaurante.dto.request;

import com.restaurante.model.enums.TenantTipo;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantProvisioningAccessRequest {

    private Long businessAccountId;
    private Boolean createBusinessAccount;
    private String businessAccountNome;
    private String businessAccountNif;
    private String businessAccountEmail;
    private String businessAccountTelefone;
    private Long planoId;
    private Integer maxTenants;

    @NotBlank
    private String tenantNome;
    private String tenantSlug;
    @NotNull
    private TenantTipo tenantTipo;
    private String tenantNif;
    private String tenantEmail;
    @NotBlank
    private String tenantTelefone;

    @NotBlank
    private String ownerNome;
    private String ownerUsername;
    private String ownerEmail;
    @NotBlank
    private String ownerTelefone;
    private String ownerPassword;
    @Builder.Default
    private Boolean gerarSenhaTemporaria = true;
    @Builder.Default
    private Boolean ativarTenant = true;
    private String observacao;

    @AssertTrue(message = "Informe businessAccountId ou createBusinessAccount=true.")
    public boolean isBusinessAccountSelectionValid() {
        return businessAccountId != null || Boolean.TRUE.equals(createBusinessAccount);
    }
}
