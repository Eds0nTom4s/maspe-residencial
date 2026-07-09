package com.restaurante.dto.request;

import com.restaurante.model.enums.OperationalDeviceType;
import com.restaurante.model.enums.PaymentMethodPolicyTemplateStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdatePaymentPolicyTemplateRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    @Size(max = 255)
    private String description;

    private OperationalDeviceType targetDeviceType;

    @NotNull
    private PaymentMethodPolicyTemplateStatus status;

    @Valid
    private List<PaymentPolicyTemplateItemRequest> items;
}

