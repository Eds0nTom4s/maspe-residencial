package com.restaurante.android.foundation.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class AndroidMoneyConverterTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "0.01, 1",
            "1.00, 100",
            "2500.00, 250000",
            "999999999999.99, 99999999999999"
    })
    void convertsAoaExactlyToMinorUnits(String major, long minor) {
        assertThat(AndroidMoneyConverter.toMinor(new BigDecimal(major), AndroidCurrency.AOA))
                .isEqualTo(minor);
        assertThat(AndroidMoneyConverter.fromMinor(minor, AndroidCurrency.AOA))
                .isEqualByComparingTo(new BigDecimal(major));
    }

    @Test
    void acceptsScaleZeroAndTwoButNeverRoundsExcessScale() {
        assertThat(AndroidMoneyConverter.toMinor(new BigDecimal("12"), AndroidCurrency.AOA)).isEqualTo(1200);
        assertThat(AndroidMoneyConverter.toMinor(new BigDecimal("12.00"), AndroidCurrency.AOA)).isEqualTo(1200);
        assertThatThrownBy(() -> AndroidMoneyConverter.toMinor(new BigDecimal("12.001"), AndroidCurrency.AOA))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void protectsLongBoundariesAndOverflow() {
        BigDecimal max = AndroidMoneyConverter.fromMinor(Long.MAX_VALUE, AndroidCurrency.AOA);
        BigDecimal min = AndroidMoneyConverter.fromMinor(Long.MIN_VALUE, AndroidCurrency.AOA);

        assertThat(AndroidMoneyConverter.toMinor(max, AndroidCurrency.AOA)).isEqualTo(Long.MAX_VALUE);
        assertThat(AndroidMoneyConverter.toMinor(min, AndroidCurrency.AOA)).isEqualTo(Long.MIN_VALUE);
        assertThatThrownBy(() -> AndroidMoneyConverter.toMinor(max.add(new BigDecimal("0.01")), AndroidCurrency.AOA))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> AndroidMoneyConverter.toMinor(min.subtract(new BigDecimal("0.01")), AndroidCurrency.AOA))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void separatesExactConversionFromNonNegativeDomainPolicy() {
        assertThat(AndroidMoneyConverter.toMinor(new BigDecimal("-0.01"), AndroidCurrency.AOA)).isEqualTo(-1);
        assertThatThrownBy(() -> AndroidMoneyConverter.toNonNegativeMinor(
                new BigDecimal("-0.01"), AndroidCurrency.AOA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesOnlyAoaAndRejectsMissingOrUnsupportedCurrency() {
        assertThat(AndroidCurrency.normalize("aoa")).isEqualTo(AndroidCurrency.AOA);
        assertThat(AndroidMoneyConverter.toContract(new BigDecimal("1.00"), " aoa "))
                .isEqualTo(new AndroidMoneyAmount(100, "AOA"));
        assertThatThrownBy(() -> AndroidCurrency.normalize(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AndroidCurrency.normalize("USD")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AndroidMoneyConverter.toMinor(BigDecimal.ONE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @CsvSource({"0", "1", "100", "-1", "9223372036854775807", "-9223372036854775808"})
    void roundTripPreservesEveryMinorUnit(long amountMinor) {
        assertThat(AndroidMoneyConverter.toMinor(
                AndroidMoneyConverter.fromMinor(amountMinor, AndroidCurrency.AOA), AndroidCurrency.AOA))
                .isEqualTo(amountMinor);
    }
}
