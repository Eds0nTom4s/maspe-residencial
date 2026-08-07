package com.restaurante.android.foundation.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Exact BigDecimal/minor-unit conversion. Floating point and silent rounding are forbidden. */
public final class AndroidMoneyConverter {

    private AndroidMoneyConverter() {
    }

    public static long toMinor(BigDecimal value, AndroidCurrency currency) {
        BigDecimal required = Objects.requireNonNull(value, "Money value is required.");
        AndroidCurrency requiredCurrency = Objects.requireNonNull(currency, "Currency is required.");
        return required
                .setScale(requiredCurrency.scale(), RoundingMode.UNNECESSARY)
                .movePointRight(requiredCurrency.scale())
                .longValueExact();
    }

    public static long toNonNegativeMinor(BigDecimal value, AndroidCurrency currency) {
        long amountMinor = toMinor(value, currency);
        if (amountMinor < 0) {
            throw new IllegalArgumentException("Negative amount is not allowed in this domain.");
        }
        return amountMinor;
    }

    public static BigDecimal fromMinor(long amountMinor, AndroidCurrency currency) {
        AndroidCurrency requiredCurrency = Objects.requireNonNull(currency, "Currency is required.");
        return BigDecimal.valueOf(amountMinor, requiredCurrency.scale());
    }

    public static AndroidMoneyAmount toContract(BigDecimal value, String currencyCode) {
        AndroidCurrency currency = AndroidCurrency.normalize(currencyCode);
        return new AndroidMoneyAmount(toMinor(value, currency), currency.name());
    }
}
