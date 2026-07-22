package com.restaurante.platform.discovery.repository.projection;

import com.restaurante.model.enums.TenantDeliveryPolicyStatus;
import com.restaurante.model.enums.TenantTipo;

/** Minimal persisted read projection used by Discovery listings. */
public interface MerchantDiscoveryProjection {

    String getMerchantId();

    String getName();

    TenantTipo getTenantType();

    String getTemplateCode();

    String getBannerUrl();

    String getProvince();

    String getMunicipality();

    Boolean getDeliveryEnabled();

    Boolean getCustomerPickupAllowed();

    TenantDeliveryPolicyStatus getDeliveryPolicyStatus();

    Boolean getOperationPickupAllowed();

    Integer getPreparationTimeMinutes();

    Boolean getCatalogPublished();

    Long getActiveCatalogItemCount();
}
