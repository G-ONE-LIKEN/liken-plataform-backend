package com.plataforma.invest.service;

import com.plataforma.invest.client.HoldingsClient;
import com.plataforma.invest.event.DividendPayoutBatchPublisher;
import com.plataforma.invest.event.DividendPayoutBatchPublisher.PayoutItem;
import com.plataforma.invest.model.DividendBatch;
import com.plataforma.invest.model.ProjectEnergyAccumulator;
import com.plataforma.invest.repository.DividendBatchRepository;
import com.plataforma.invest.repository.ProjectEnergyAccumulatorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Arma y publica un batch de payouts cuando el acumulador de un proyecto
 * cruza el umbral.
 *
 * <p>Flujo:
 * <ol>
 *   <li>Consulta holders del proyecto via {@link HoldingsClient}.</li>
 *   <li>Calcula la fraccion proporcional de cada holder con redondeo
 *       HALF_DOWN a 6 decimales (escala USDC).</li>
 *   <li>Filtra montos &lt; {@link #DUST_THRESHOLD_USDC} (no gasta gas en polvo).</li>
 *   <li>Si no quedan payouts validos, retorna sin tocar el acumulador (el
 *       pending sigue ahi para reintento).</li>
 *   <li>Mueve el monto efectivo de {@code pending_usdc} a {@code in_flight_usdc}.</li>
 *   <li>Persiste el {@link DividendBatch} y publica el evento Kafka via
 *       {@link DividendPayoutBatchPublisher} (sendAfterCommit).</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DividendBatchBuilder {

    /** Skip payouts mas chicos que esto para no quemar gas en polvo. */
    public static final BigDecimal DUST_THRESHOLD_USDC = new BigDecimal("0.01");

    private final HoldingsClient holdingsClient;
    private final ProjectEnergyAccumulatorRepository accumulatorRepo;
    private final DividendBatchRepository batchRepo;
    private final DividendPayoutBatchPublisher publisher;

    /**
     * Intenta armar y publicar un batch para el proyecto con su saldo pending
     * actual. Llamado desde {@link EnergyAccrualService} cuando se cruza el
     * umbral. Idempotente respecto a in-flight: el caller ya valido que no hay
     * otro batch en vuelo.
     *
     * <p>Esta operacion participa de la tx del caller (mismo @Transactional).
     */
    public void flush(ProjectEnergyAccumulator acc) {
        Long projectId = acc.getProjectId();
        BigDecimal pending = acc.getPendingUsdc().setScale(6, RoundingMode.DOWN);

        List<HoldingsClient.Holder> holders = holdingsClient.fetchHolders(projectId);
        if (holders.isEmpty()) {
            log.warn("Project {} cruzo umbral pero no tiene holders validos. " +
                    "Pending=${} queda intacto hasta que aparezca un holder.",
                    projectId, pending);
            return;
        }

        BigDecimal sumTokens = holders.stream()
                .map(HoldingsClient.Holder::tokensAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sumTokens.signum() <= 0) {
            log.warn("Project {} holders con sumTokens=0. Skip batch.", projectId);
            return;
        }

        String batchId = "batch-" + projectId + "-" + UUID.randomUUID();
        List<PayoutItem> payouts = new ArrayList<>();
        BigDecimal totalPayoutAmount = BigDecimal.ZERO;
        for (HoldingsClient.Holder h : holders) {
            BigDecimal share = h.tokensAmount().divide(sumTokens, 18, RoundingMode.HALF_DOWN);
            BigDecimal amount = pending.multiply(share).setScale(6, RoundingMode.DOWN);
            if (amount.compareTo(DUST_THRESHOLD_USDC) < 0) {
                log.debug("Skip payout polvo: project={} wallet={} amount={}",
                        projectId, h.walletAddress(), amount);
                continue;
            }
            payouts.add(new PayoutItem(
                    h.userId(),
                    h.walletAddress().toLowerCase(),
                    amount,
                    batchId + ":" + h.walletAddress().toLowerCase()));
            totalPayoutAmount = totalPayoutAmount.add(amount);
        }

        if (payouts.isEmpty()) {
            log.warn("Project {} todos los payouts quedaron debajo del umbral de polvo. " +
                    "Pending=${} queda intacto.", projectId, pending);
            return;
        }

        // Movemos pending → in_flight. Solo el monto efectivamente repartido,
        // el polvo restante queda en pending para la proxima.
        acc.setInFlightUsdc(acc.getInFlightUsdc().add(totalPayoutAmount));
        acc.setPendingUsdc(pending.subtract(totalPayoutAmount));
        acc.setPendingKwh(BigDecimal.ZERO);
        acc.setLastFlushedAt(LocalDateTime.now());
        accumulatorRepo.save(acc);

        // Tracker del batch.
        DividendBatch batch = DividendBatch.builder()
                .batchId(batchId)
                .projectId(projectId)
                .totalAmountUsdc(totalPayoutAmount)
                .totalPayouts(payouts.size())
                .build();
        batchRepo.save(batch);

        publisher.publish(batchId, projectId, payouts);
        log.info("Batch armado project={} batchId={} payouts={} total=${}",
                projectId, batchId, payouts.size(), totalPayoutAmount);
    }
}
