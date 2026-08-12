package com.restaurante.android.options.dto;

import java.util.List;
import java.util.UUID;

/** Internal Android-facade projection; its fields intentionally match OpenAPI OptionGroup. */
public record AndroidProductOptionGroupProjection(
        UUID optionGroupId,
        String name,
        int minSelections,
        int maxSelections,
        boolean required,
        boolean singleChoice,
        int sortOrder,
        List<AndroidProductOptionProjection> options) {
}
