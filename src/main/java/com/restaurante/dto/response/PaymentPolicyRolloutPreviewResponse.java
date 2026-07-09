package com.restaurante.dto.response;

import com.restaurante.model.enums.PaymentMethodPolicyOverwriteMode;
import com.restaurante.model.enums.PaymentMethodPolicyRolloutMode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PaymentPolicyRolloutPreviewResponse {
    private Long templateId;
    private Long unidadeId;
    private PaymentMethodPolicyRolloutMode rolloutMode;
    private PaymentMethodPolicyOverwriteMode overwriteMode;
    private int totalDevicesTargeted;
    private int totalPoliciesToCreate;
    private int totalPoliciesToUpdate;
    private int totalPoliciesToSkip;
    private List<String> warnings;
    private List<DevicePolicyRolloutResultResponse> deviceResults;
}

