package com.restaurante.dto.response;

import com.restaurante.model.enums.FiscalAuthority;
import com.restaurante.model.enums.OfficialFiscalEnvironment;
import com.restaurante.model.enums.OfficialFiscalSubmissionMode;
import com.restaurante.model.enums.TenantOfficialFiscalProfileStatus;
import lombok.Data;

@Data
public class TenantOfficialFiscalProfileResponse {
    private Long id;
    private Long tenantId;
    private TenantOfficialFiscalProfileStatus status;
    private String countryCode;
    private FiscalAuthority authority;
    private Boolean officialEnabled;
    private OfficialFiscalEnvironment environment;
    private OfficialFiscalSubmissionMode submissionMode;

    private String taxpayerNumber;
    private String softwareCertificateId;
    private String softwareName;
    private String softwareVersion;
    private String producerRegistrationId;
    private String publicKeyId;
    private String taxpayerKeyId;
    private Long signingProfileId;
    private String callbackUrl;
}

