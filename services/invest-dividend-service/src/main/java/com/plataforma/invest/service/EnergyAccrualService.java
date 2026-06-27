package com.plataforma.invest.service;

import com.plataforma.invest.model.EnergyReadingLog;
import com.plataforma.invest.model.ProjectEnergyAccumulator;
import com.plataforma.invest.repository.EnergyReadingLogRepository;
import com.plataforma.invest.repository.ProjectEnergyAccumulatorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Logica del acumulado oracle → dividendos.
 *
 * <p>Por cada lectura kWh aplicamos la tarifa PPA y sumamos al saldo del
 * proyecto. Cuando el pendiente supera {@link #DEPOSIT_THRESHOLD_USDC} y no hay
 * un deposito previo "in-flight" (sin confirmar), movemos todo el pendiente a
 * {@code inFlightUsdc} y publicamos {@code dividends.deposit_requested}. El
 * consumer de {@code dividends.deposited} (otro listener) limpia el in-flight
 * al recibir el txHash.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnergyAccrualService {

    /**
     * Tarifa PPA fija: USD/kWh.
     *
     * <p>El Modelo_Token.md sugiere 0.066 (CAMMESA tipico). En demo de testnet
     * usamos 0.0066 (10x menor) para que cada deposit on-chain sea mas chico y
     * el saldo USDC del signer dure mas tiempo sin necesitar refill. Cuando
     * se separe el TREASURY del SIGNER en produccion, este valor puede volver
     * al 0.066 realista.
     */
    public static final BigDecimal PPA_TARIFF_USDC_PER_KWH = new BigDecimal("0.0066");

    /** Umbral de USDC acumulado a partir del cual se dispara un deposit on-chain. */
    public static final BigDecimal DEPOSIT_THRESHOLD_USDC = new BigDecimal("1.00");

    private final EnergyReadingLogRepository readingRepo;
    private final ProjectEnergyAccumulatorRepository accumulatorRepo;
    private final DividendBatchBuilder batchBuilder;

    @Transactional
    public void accrueReading(Long projectId, BigDecimal kwh, LocalDateTime recordedAt) {
        String eventId = "energy:" + projectId + ":" + recordedAt.toString();
        if (readingRepo.existsByEventId(eventId)) {
            log.debug("Lectura duplicada ignorada: {}", eventId);
            return;
        }

        BigDecimal revenueUsdc = kwh.multiply(PPA_TARIFF_USDC_PER_KWH)
                .setScale(6, RoundingMode.HALF_UP);

        readingRepo.save(EnergyReadingLog.builder()
                .projectId(projectId)
                .kwh(kwh)
                .revenueUsdc(revenueUsdc)
                .recordedAt(recordedAt)
                .eventId(eventId)
                .build());

        accumulatorRepo.ensureExists(projectId);
        ProjectEnergyAccumulator acc = accumulatorRepo.findByIdForUpdate(projectId)
                .orElseThrow(() -> new IllegalStateException(
                        "Accumulator missing after ensureExists for projectId=" + projectId));

        acc.setPendingKwh(acc.getPendingKwh().add(kwh));
        acc.setPendingUsdc(acc.getPendingUsdc().add(revenueUsdc));

        log.info("EnergyReading persistida: projectId={} kWh={} revenueUsdc={} (pending=${})",
                projectId, kwh, revenueUsdc, acc.getPendingUsdc());

        if (acc.getPendingUsdc().compareTo(DEPOSIT_THRESHOLD_USDC) >= 0
                && acc.getInFlightUsdc().compareTo(BigDecimal.ZERO) == 0) {
            // Persistimos primero el incremento del pending (sin tocar in-flight).
            accumulatorRepo.save(acc);
            // Delegamos al builder, que maneja: holders → calcular payouts →
            // mover pending a in_flight → crear DividendBatch → publicar.
            batchBuilder.flush(acc);
        } else {
            accumulatorRepo.save(acc);
            if (acc.getInFlightUsdc().compareTo(BigDecimal.ZERO) > 0) {
                log.debug("Hay batch in-flight para projectId={} (${}). Se acumula sin disparar.",
                        projectId, acc.getInFlightUsdc());
            }
        }
    }

    @Transactional
    public void clearInFlight(Long projectId) {
        accumulatorRepo.findByIdForUpdate(projectId).ifPresent(acc -> {
            log.info("Acumulador projectId={} reseteado tras deposit on-chain (era in-flight=${})",
                    projectId, acc.getInFlightUsdc());
            acc.setInFlightUsdc(BigDecimal.ZERO);
            accumulatorRepo.save(acc);
        });
    }

    /**
     * Resta un monto especifico del {@code in_flight_usdc}. Llamado cuando un
     * payout individual del batch confirma on-chain. A diferencia de
     * {@link #clearInFlight}, no resetea a cero — sustrae el monto exacto del
     * payout. Cuando todos los payouts del batch terminan, in-flight queda en 0.
     */
    @Transactional
    public void subtractFromInFlight(Long projectId, BigDecimal amount) {
        accumulatorRepo.findByIdForUpdate(projectId).ifPresent(acc -> {
            BigDecimal newInFlight = acc.getInFlightUsdc().subtract(amount);
            if (newInFlight.signum() < 0) {
                log.warn("subtractFromInFlight projectId={} amount={} resultaria negativo " +
                        "(in_flight={}). Se clamp a 0.",
                        projectId, amount, acc.getInFlightUsdc());
                newInFlight = BigDecimal.ZERO;
            }
            acc.setInFlightUsdc(newInFlight);
            accumulatorRepo.save(acc);
        });
    }

    /**
     * Devuelve un monto especifico de in_flight a pending (cuando un payout
     * individual falla). El monto vuelve al pool de espera para reintento en
     * el proximo batch.
     */
    @Transactional
    public void returnToPending(Long projectId, BigDecimal amount) {
        accumulatorRepo.findByIdForUpdate(projectId).ifPresent(acc -> {
            BigDecimal newInFlight = acc.getInFlightUsdc().subtract(amount);
            if (newInFlight.signum() < 0) newInFlight = BigDecimal.ZERO;
            acc.setInFlightUsdc(newInFlight);
            acc.setPendingUsdc(acc.getPendingUsdc().add(amount));
            accumulatorRepo.save(acc);
            log.warn("Payout fallido projectId={}: ${} devueltos a pending", projectId, amount);
        });
    }

    /**
     * Rollback de un deposit fallido: devuelve {@code inFlightUsdc} a
     * {@code pendingUsdc} para que el flujo normal vuelva a intentar en la
     * proxima acumulacion. Sin esto, un deposit fallido dejaria el saldo
     * congelado en in-flight y el proyecto no volveria a depositar nunca.
     */
    @Transactional
    public void rollbackInFlight(Long projectId) {
        accumulatorRepo.findByIdForUpdate(projectId).ifPresent(acc -> {
            BigDecimal lost = acc.getInFlightUsdc();
            if (lost.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("rollbackInFlight projectId={} pero in-flight ya era 0", projectId);
                return;
            }
            acc.setPendingUsdc(acc.getPendingUsdc().add(lost));
            acc.setInFlightUsdc(BigDecimal.ZERO);
            accumulatorRepo.save(acc);
            log.warn("Deposit fallido projectId={}: ${} devueltos de in-flight → pending",
                    projectId, lost);
        });
    }
}
