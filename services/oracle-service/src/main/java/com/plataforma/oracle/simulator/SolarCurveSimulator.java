package com.plataforma.oracle.simulator;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simula la generacion de energia de un parque solar/eolico en funcion de su
 * capacidad instalada y la hora del dia.
 *
 * <p>
 * Modelo: curva senoidal simple acotada a horas de luz (6:00–18:00), con pico
 * al mediodia y cero fuera de ese rango. Se aplica una variacion aleatoria de
 * ±10% para simular ruido real de un medidor IoT (nubosidad, viento, etc.).
 *
 * <p>
 * No contempla estacionalidad ni huso horario por proyecto — es una
 * aproximacion deliberada para fines de demo/testing del flujo end-to-end.
 */
@Component
public class SolarCurveSimulator {

    // TEMPORAL para demo: curva 24h (siempre genera) para no depender del horario.
    // Para volver al realismo poner SUNRISE=6, SUNSET=18.
    private static final int SUNRISE_HOUR = 0;
    private static final int SUNSET_HOUR = 24;
    private static final int DAYLIGHT_HOURS = SUNSET_HOUR - SUNRISE_HOUR;

    private static final BigDecimal VARIATION_RANGE = BigDecimal.valueOf(0.10); // ±10%
    private static final BigDecimal KW_PER_MW = BigDecimal.valueOf(1000);
    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    /**
     * Calcula la energia generada (en kWh) durante un intervalo de tiempo,
     * para un proyecto con una capacidad instalada dada.
     *
     * @param installedCapacityMW capacidad instalada del parque, en MW
     * @param momento             instante de la lectura (se usa su hora del dia)
     * @param intervalMinutes     duracion del intervalo simulado, en minutos
     * @return energia generada en el intervalo, en kWh (nunca negativa)
     */
    public BigDecimal simulateEnergyKWh(BigDecimal installedCapacityMW, LocalDateTime momento, int intervalMinutes) {
        BigDecimal solarFactor = solarFactor(momento);
        if (solarFactor.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal instantPowerMW = installedCapacityMW
                .multiply(solarFactor)
                .multiply(randomVariationFactor());

        BigDecimal intervalHours = BigDecimal.valueOf(intervalMinutes)
                .divide(MINUTES_PER_HOUR, 8, RoundingMode.HALF_UP);

        BigDecimal energyKWh = instantPowerMW
                .multiply(KW_PER_MW)
                .multiply(intervalHours);

        return energyKWh.max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Factor solar entre 0 y 1 segun la hora del dia.
     * sin(π × (hora - amanecer) / horasDeLuz) — 0 al amanecer/atardecer, 1 al
     * mediodia.
     */
    private BigDecimal solarFactor(LocalDateTime momento) {
        double hourOfDay = momento.getHour() + (momento.getMinute() / 60.0);

        if (hourOfDay < SUNRISE_HOUR || hourOfDay > SUNSET_HOUR) {
            return BigDecimal.ZERO;
        }

        double angle = Math.PI * (hourOfDay - SUNRISE_HOUR) / DAYLIGHT_HOURS;
        double factor = Math.sin(angle);

        return BigDecimal.valueOf(Math.max(factor, 0));
    }

    /**
     * Variacion aleatoria uniforme en el rango [0.90, 1.10] para simular
     * ruido de un medidor real (nubosidad pasajera, rafagas de viento, etc.).
     */
    private BigDecimal randomVariationFactor() {
        double delta = ThreadLocalRandom.current().nextDouble(
                -VARIATION_RANGE.doubleValue(),
                VARIATION_RANGE.doubleValue());
        return BigDecimal.ONE.add(BigDecimal.valueOf(delta));
    }
}