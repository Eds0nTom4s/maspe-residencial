package com.restaurante.businesstemplate;

import com.restaurante.businesstemplate.dto.BusinessTemplatePreviewResponse;
import com.restaurante.businesstemplate.dto.BusinessTemplateProvisionRequest;
import com.restaurante.businesstemplate.dto.BusinessTemplateProvisionResponse;

public interface BusinessTemplate {

    String code();

    int version();

    default boolean supports(BusinessTemplateKey key) {
        if (key == null || key.code() == null) return false;
        if (!code().equalsIgnoreCase(key.code())) return false;
        return key.version() == null || key.version() == version();
    }

    BusinessTemplatePreviewResponse preview(BusinessTemplateKey key, BusinessTemplateProvisionRequest request);

    BusinessTemplateProvisionResponse provision(BusinessTemplateKey key, BusinessTemplateProvisionRequest request);
}

