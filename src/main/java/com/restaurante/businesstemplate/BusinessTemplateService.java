package com.restaurante.businesstemplate;

import com.restaurante.businesstemplate.dto.BusinessTemplatePreviewResponse;
import com.restaurante.businesstemplate.dto.BusinessTemplateProvisionRequest;
import com.restaurante.businesstemplate.dto.BusinessTemplateProvisionResponse;
import com.restaurante.exception.ProvisioningException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BusinessTemplateService {

    private final List<BusinessTemplate> templates;

    public BusinessTemplatePreviewResponse preview(String templateCodeRaw, BusinessTemplateProvisionRequest request) {
        BusinessTemplateKey key = BusinessTemplateKey.parse(templateCodeRaw);
        BusinessTemplate t = resolve(key, templateCodeRaw);
        return t.preview(key, request);
    }

    public BusinessTemplateProvisionResponse provision(String templateCodeRaw, BusinessTemplateProvisionRequest request) {
        BusinessTemplateKey key = BusinessTemplateKey.parse(templateCodeRaw);
        BusinessTemplate t = resolve(key, templateCodeRaw);
        return t.provision(key, request);
    }

    private BusinessTemplate resolve(BusinessTemplateKey key, String raw) {
        for (BusinessTemplate t : templates) {
            if (t.supports(key)) return t;
        }
        throw new ProvisioningException(
                HttpStatus.BAD_REQUEST,
                "BUSINESS_TEMPLATE_INEXISTENTE",
                "templateCode",
                "BusinessTemplate inválido/não suportado: " + raw,
                null,
                "Use um templateCode suportado (ex.: CONSUMA_PONTO_V1, CONSUMA_REST_V1).",
                null
        );
    }
}

