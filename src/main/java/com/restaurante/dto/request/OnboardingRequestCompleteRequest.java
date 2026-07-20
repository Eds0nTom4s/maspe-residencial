package com.restaurante.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OnboardingRequestCompleteRequest {
    @NotNull @PositiveOrZero private Long onboardingVersion;
    @Size(max = 36) private String operationId;
    @NotBlank @Size(max = 500) private String reason;
}
