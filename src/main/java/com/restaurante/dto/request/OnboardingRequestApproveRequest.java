package com.restaurante.dto.request;

import com.restaurante.dto.business.BusinessProvisioningContracts.BusinessVertical;
import com.restaurante.dto.business.BusinessProvisioningContracts.PrincipalOwner;
import com.restaurante.model.enums.OnboardingAccountChoice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnboardingRequestApproveRequest {
    @NotNull @PositiveOrZero
    private Long onboardingVersion;

    @NotNull
    private OnboardingAccountChoice accountChoice;

    private Long businessAccountId;
    @PositiveOrZero
    private Long accountVersion;
    private Boolean confirmExistingAccount;

    @Size(max = 80)
    private String businessAccountSlug;
    @Positive
    private Integer maxTenants;

    @Valid
    private PrincipalOwner ownerChoice;

    @NotBlank @Size(max = 30)
    private String confirmedPlanCode;

    @NotNull
    private BusinessVertical vertical;

    @NotBlank @Size(max = 500)
    private String reason;
}
