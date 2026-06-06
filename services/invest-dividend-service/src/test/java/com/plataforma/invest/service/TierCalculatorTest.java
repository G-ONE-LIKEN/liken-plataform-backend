package com.plataforma.invest.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class TierCalculatorTest {

    private final TierCalculator calc = new TierCalculator();

    @Test
    void cero_devuelveBronze() {
        assertThat(calc.tierFor(BigDecimal.ZERO)).isEqualTo("BRONZE");
    }

    @Test
    void null_devuelveBronze() {
        assertThat(calc.tierFor(null)).isEqualTo("BRONZE");
    }

    @Test
    void justoEnUmbralSilver_devuelveSilver() {
        // El threshold SILVER es ≥ 1000 (inclusivo).
        assertThat(calc.tierFor(new BigDecimal("1000"))).isEqualTo("SILVER");
    }

    @Test
    void unCentavoBajoSilver_devuelveBronze() {
        assertThat(calc.tierFor(new BigDecimal("999.99"))).isEqualTo("BRONZE");
    }

    @Test
    void entreSilverYGold_devuelveSilver() {
        assertThat(calc.tierFor(new BigDecimal("3500"))).isEqualTo("SILVER");
    }

    @Test
    void justoEnUmbralGold_devuelveGold() {
        // El threshold GOLD es ≥ 5000 (inclusivo).
        assertThat(calc.tierFor(new BigDecimal("5000"))).isEqualTo("GOLD");
    }

    @Test
    void mucho_devuelveGold() {
        assertThat(calc.tierFor(new BigDecimal("999999.99"))).isEqualTo("GOLD");
    }
}
