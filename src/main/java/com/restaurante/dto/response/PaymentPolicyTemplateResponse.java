package com.restaurante.dto.response;

import com.restaurante.model.enums.OperationalDeviceType;
import com.restaurante.model.enums.PaymentMethodPolicyTemplateStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class PaymentPolicyTemplateResponse {
    private Long templateId;
    private String code;
    private String name;
    private String description;
    private OperationalDeviceType targetDeviceType;
    private PaymentMethodPolicyTemplateStatus status;
    private boolean systemDefault;
    private int version;
    private List<PaymentPolicyTemplateItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;
}

