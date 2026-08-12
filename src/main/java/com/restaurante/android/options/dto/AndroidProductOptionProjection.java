package com.restaurante.android.options.dto;

import com.restaurante.android.foundation.money.AndroidMoneyAmount;
import java.util.UUID;

/** Internal Android-facade projection; its fields intentionally match OpenAPI Option. */
public record AndroidProductOptionProjection(
        UUID optionId,
        String name,
        AndroidMoneyAmount additionalPrice,
        boolean available,
        boolean defaultSelected,
        int sortOrder) {
}
