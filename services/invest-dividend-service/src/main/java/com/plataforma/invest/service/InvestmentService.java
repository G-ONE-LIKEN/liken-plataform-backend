package com.plataforma.invest.service;

import com.plataforma.invest.event.UserTierChangedPublisher;
import com.plataforma.invest.model.Investment;
import com.plataforma.invest.model.ProcessedEvent;
import com.plataforma.invest.model.UserInvestmentTotal;
import com.plataforma.invest.repository.InvestmentRepository;
import com.plataforma.invest.repository.ProcessedEventRepository;
import com.plataforma.invest.repository.UserInvestmentTotalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Lógica central de inversiones:
 * <ul>
 *   <li>{@link #recordPurchase} se invoca desde el consumer de {@code investment.token_purchased}
 *       cuando el Blockchain Service publica una compra. Es idempotente.</li>
 *   <li>{@link #listForUser} lista las compras del usuario logueado.</li>
 * </ul>
 *
 * <p>Cuando una compra cambia el tier del usuario, publica {@code user.tier_changed}
 * para que el {@code user-service} actualice {@code users.tier}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentService {

    private final InvestmentRepository investments;
    private final UserInvestmentTotalRepository totals;
    private final ProcessedEventRepository processed;
    private final TierCalculator tierCalculator;
    private final UserTierChangedPublisher tierPublisher;

    @Transactional
    public void recordPurchase(String eventId,
                               Long userId,
                               String walletAddress,
                               Long projectId,
                               String offeringContractAddress,
                               BigDecimal usdcAmount,
                               BigDecimal lknAmount,
                               String txHash,
                               Long blockNumber) {
        if (eventId != null && processed.existsByEventId(eventId)) {
            log.debug("Evento {} ya procesado, ignorando (idempotencia)", eventId);
            return;
        }
        if (userId == null) {
            log.warn("recordPurchase: userId null para wallet={} txHash={}. " +
                    "Registrando compra por wallet para reconciliación posterior.", walletAddress, txHash);
        }

        // 1. Guardar la compra.
        investments.save(Investment.builder()
                .userId(userId)
                .walletAddress(walletAddress)
                .projectId(projectId)
                .offeringContractAddress(offeringContractAddress)
                .usdcAmount(usdcAmount)
                .lknAmount(lknAmount)
                .txHash(txHash)
                .blockNumber(blockNumber)
                .createdAt(LocalDateTime.now())
                .build());

        // 2. Acumular total + recalcular tier (con lock pesimista) solo si hay usuario vinculado.
        if (userId != null) {
            UserInvestmentTotal total = totals.findByUserIdForUpdate(userId)
                    .orElseGet(() -> UserInvestmentTotal.builder()
                            .userId(userId)
                            .totalUsdcInvested(BigDecimal.ZERO)
                            .currentTier("BRONZE")
                            .updatedAt(LocalDateTime.now())
                            .build());

            BigDecimal newTotal = total.getTotalUsdcInvested().add(usdcAmount);
            String oldTier = total.getCurrentTier();
            String newTier = tierCalculator.tierFor(newTotal);

            total.setTotalUsdcInvested(newTotal);
            total.setCurrentTier(newTier);
            total.setUpdatedAt(LocalDateTime.now());
            totals.save(total);

            log.info("Compra registrada: userId={} projectId={} usdc={} lkn={} total={} tier={}",
                    userId, projectId, usdcAmount, lknAmount, newTotal, newTier);

            // 3. Publicar cambio de tier si cruzó umbral.
            if (!oldTier.equals(newTier)) {
                tierPublisher.publish(userId, oldTier, newTier);
            }
        } else {
            log.info("Compra registrada (sin usuario vinculado): wallet={} projectId={} usdc={} lkn={}",
                    walletAddress, projectId, usdcAmount, lknAmount);
        }

        // 4. Marcar evento como procesado (idempotencia).
        if (eventId != null) {
            processed.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .topic("investment.token_purchased")
                    .processedAt(LocalDateTime.now())
                    .build());
        }
    }

    @Transactional(readOnly = true)
    public Page<Investment> listForUser(Long userId, Pageable pageable) {
        return investments.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Investment> listForProject(Long projectId, Pageable pageable) {
        return investments.findByProjectIdOrderByCreatedAtDesc(projectId, pageable);
    }

    @Transactional(readOnly = true)
    public UserInvestmentTotal getTotalForUser(Long userId) {
        return totals.findById(userId)
                .orElseGet(() -> UserInvestmentTotal.builder()
                        .userId(userId)
                        .totalUsdcInvested(BigDecimal.ZERO)
                        .currentTier("BRONZE")
                        .updatedAt(LocalDateTime.now())
                        .build());
    }

    @Transactional
    public int reconcileWalletLinked(Long userId, String walletAddress) {
        List<Investment> orphanInvestments = investments.findByUserIdIsNullAndWalletAddressIgnoreCase(walletAddress);
        for (Investment investment : orphanInvestments) {
            investment.setUserId(userId);
        }
        investments.saveAll(orphanInvestments);
        recomputeUserTotal(userId);
        return orphanInvestments.size();
    }

    @Transactional
    public void recomputeUserTotal(Long userId) {
        UserInvestmentTotal total = totals.findByUserIdForUpdate(userId)
                .orElseGet(() -> UserInvestmentTotal.builder()
                        .userId(userId)
                        .totalUsdcInvested(BigDecimal.ZERO)
                        .currentTier("BRONZE")
                        .updatedAt(LocalDateTime.now())
                        .build());

        BigDecimal recomputedTotal = investments.sumUsdcAmountByUserId(userId);
        String oldTier = total.getCurrentTier();
        String newTier = tierCalculator.tierFor(recomputedTotal);

        total.setTotalUsdcInvested(recomputedTotal);
        total.setCurrentTier(newTier);
        total.setUpdatedAt(LocalDateTime.now());
        totals.save(total);

        if (!oldTier.equals(newTier)) {
            tierPublisher.publish(userId, oldTier, newTier);
        }
    }
}
