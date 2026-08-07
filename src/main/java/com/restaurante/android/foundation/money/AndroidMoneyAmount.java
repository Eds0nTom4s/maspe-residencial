package com.restaurante.android.foundation.money;

/** Wire-safe money shape for the Android facade. */
public record AndroidMoneyAmount(long amountMinor, String currencyCode) {

    public AndroidMoneyAmount {
        currencyCode = AndroidCurrency.normalize(currencyCode).name();
    }
}
