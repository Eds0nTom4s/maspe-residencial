package com.restaurante.platform.discovery.domain;

import java.time.LocalTime;

public record MerchantAvailability(Status status, Integer minutesRemaining, LocalTime opensAt) {

    public enum Status {
        OPEN,
        CLOSING_SOON,
        OPENS_AT,
        CLOSED,
        UNKNOWN
    }

    public static MerchantAvailability open() {
        return new MerchantAvailability(Status.OPEN, null, null);
    }

    public static MerchantAvailability closingSoon(int minutesRemaining) {
        return new MerchantAvailability(Status.CLOSING_SOON, minutesRemaining, null);
    }

    public static MerchantAvailability opensAt(LocalTime opensAt) {
        return new MerchantAvailability(Status.OPENS_AT, null, opensAt);
    }

    public static MerchantAvailability closed() {
        return new MerchantAvailability(Status.CLOSED, null, null);
    }

    public static MerchantAvailability unknown() {
        return new MerchantAvailability(Status.UNKNOWN, null, null);
    }
}
