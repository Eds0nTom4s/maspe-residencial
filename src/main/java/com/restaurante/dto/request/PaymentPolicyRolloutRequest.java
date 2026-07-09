package com.restaurante.dto.request;

import com.restaurante.model.enums.OperationalDeviceType;
import com.restaurante.model.enums.PaymentMethodPolicyOverwriteMode;
import com.restaurante.model.enums.PaymentMethodPolicyRolloutMode;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PaymentPolicyRolloutRequest {

    @NotNull
    private Long unidadeId;

    @NotNull
    private PaymentMethodPolicyRolloutMode rolloutMode;

    private OperationalDeviceType targetDeviceType;

    private List<Long> selectedDeviceIds;

    @NotNull
    private PaymentMethodPolicyOverwriteMode overwriteMode;
}

