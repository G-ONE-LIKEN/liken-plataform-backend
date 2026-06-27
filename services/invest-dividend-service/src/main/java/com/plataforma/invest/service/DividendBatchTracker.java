package com.plataforma.invest.service;

import com.plataforma.invest.model.DividendBatch;
import com.plataforma.invest.model.DividendBatch.Status;
import com.plataforma.invest.model.DividendPayout;
import com.plataforma.invest.repository.DividendBatchRepository;
import com.plataforma.invest.repository.DividendPayoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cerebro del lifecycle de un {@link DividendBatch}: procesa confirmaciones y
 * fallos individuales, actualiza contadores bajo lock pesimista, y cierra el
 * batch cuando todos los payouts terminaron.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DividendBatchTracker {

    private final DividendBatchRepository batchRepo;
    private final DividendPayoutRepository payoutRepo;
    private final EnergyAccrualService accrualService;

    @Transactional
    public void recordPaid(String batchId, String payoutEventId, Long projectId, Long userId,
                           String wallet, BigDecimal amount, String txHash, Long blockNumber) {
        if (payoutRepo.existsByPayoutEventId(payoutEventId)) {
            log.debug("Payout duplicado, ignorando: {}", payoutEventId);
            return;
        }

        payoutRepo.save(DividendPayout.builder()
                .batchId(batchId)
                .projectId(projectId)
                .userId(userId)
                .walletAddress(wallet)
                .amount(amount)
                .txHash(txHash)
                .blockNumber(blockNumber)
                .paidAt(LocalDateTime.now())
                .payoutEventId(payoutEventId)
                .build());

        accrualService.subtractFromInFlight(projectId, amount);

        DividendBatch batch = batchRepo.findByIdForUpdate(batchId).orElse(null);
        if (batch == null) {
            log.warn("dividends.paid recibido para batch desconocido: {}", batchId);
            return;
        }
        batch.setConfirmedCount(batch.getConfirmedCount() + 1);
        closeIfDone(batch);
        batchRepo.save(batch);
        log.info("Payout confirmado batch={} amount=${} tx={} ({}/{} confirmed, {} failed)",
                batchId, amount, txHash, batch.getConfirmedCount(),
                batch.getTotalPayouts(), batch.getFailedCount());
    }

    @Transactional
    public void recordFailed(String batchId, Long projectId, BigDecimal amount, String reason) {
        accrualService.returnToPending(projectId, amount);

        DividendBatch batch = batchRepo.findByIdForUpdate(batchId).orElse(null);
        if (batch == null) {
            log.warn("dividends.paid_failed recibido para batch desconocido: {}", batchId);
            return;
        }
        batch.setFailedCount(batch.getFailedCount() + 1);
        closeIfDone(batch);
        batchRepo.save(batch);
        log.warn("Payout fallido batch={} amount=${} reason={} ({}/{} confirmed, {} failed)",
                batchId, amount, reason, batch.getConfirmedCount(),
                batch.getTotalPayouts(), batch.getFailedCount());
    }

    @Transactional
    public void recordBatchFailed(String batchId, Long projectId, String reason) {
        DividendBatch batch = batchRepo.findByIdForUpdate(batchId).orElse(null);
        if (batch == null) {
            log.warn("dividends.payout_batch_failed recibido para batch desconocido: {}", batchId);
            return;
        }
        if (batch.getStatus() != Status.PENDING) {
            log.warn("Batch {} ya estaba en estado {}, ignoro batch_failed", batchId, batch.getStatus());
            return;
        }
        // Devolvemos todo el monto del batch a pending.
        accrualService.returnToPending(projectId, batch.getTotalAmountUsdc());
        batch.setStatus(Status.FAILED);
        batch.setClosedAt(LocalDateTime.now());
        batchRepo.save(batch);
        log.warn("Batch {} abortado completo. ${} devueltos a pending. Razon: {}",
                batchId, batch.getTotalAmountUsdc(), reason);
    }

    private void closeIfDone(DividendBatch batch) {
        if (batch.getConfirmedCount() + batch.getFailedCount() >= batch.getTotalPayouts()) {
            batch.setStatus(batch.getFailedCount() == 0 ? Status.COMPLETED : Status.PARTIAL);
            batch.setClosedAt(LocalDateTime.now());
            log.info("Batch {} cerrado: status={}", batch.getBatchId(), batch.getStatus());
        }
    }
}
