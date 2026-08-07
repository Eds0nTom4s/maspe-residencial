package com.restaurante.android.discovery.repository;

import java.util.UUID;

/** Minimal public read projection. No internal identifier is part of the projection. */
public interface AndroidDiscoveryMerchantProjection {

    UUID getMerchantId();

    String getName();

    Boolean getCatalogPublished();

    Long getActiveCatalogItemCount();
}
