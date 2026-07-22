package com.restaurante.platform.discovery.repository.projection;

import com.restaurante.model.enums.TipoUnidadeAtendimento;

/** Public-safe details from the first active operational location of a merchant. */
public interface MerchantLocationProjection {

    String getLogoUrl();

    String getDescription();

    TipoUnidadeAtendimento getUnitType();
}
