package com.plataforma.blockchain.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class UnitConverterTest {

    private final UnitConverter converter = new UnitConverter();

    @Test
    void usdc_diezDolares_conviertenAEscala6() {
        // 10_000_000 (6 dec) → $10.000000
        BigDecimal result = converter.usdcFromOnchain(new BigInteger("10000000"));
        assertThat(result).isEqualByComparingTo("10.000000");
        assertThat(result.scale()).isEqualTo(6);
    }

    @Test
    void usdc_centavos_conservaPrecision() {
        // 12345 (6 dec) → $0.012345
        assertThat(converter.usdcFromOnchain(new BigInteger("12345")))
                .isEqualByComparingTo("0.012345");
    }

    @Test
    void usdc_null_devuelveCero() {
        assertThat(converter.usdcFromOnchain(null))
                .isEqualByComparingTo("0");
    }

    @Test
    void lkn_unTokenEntero_seExpresaConEscala8() {
        // 1 LKN = 1e18 wei → 1.00000000 (escala 8)
        BigDecimal result = converter.lknFromOnchain(new BigInteger("1000000000000000000"));
        assertThat(result).isEqualByComparingTo("1.00000000");
        assertThat(result.scale()).isEqualTo(8);
    }

    @Test
    void lkn_supplyParqueTipico_5000Tokens() {
        // 5000 LKN = 5000 * 1e18
        BigDecimal expected = new BigDecimal("5000.00000000");
        BigInteger raw = new BigInteger("5000000000000000000000");
        assertThat(converter.lknFromOnchain(raw)).isEqualByComparingTo(expected);
    }
}
