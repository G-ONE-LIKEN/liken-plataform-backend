package com.plataforma.projects.service.impl;

import com.plataforma.projects.dto.UserHoldingResponse;
import com.plataforma.projects.exception.ProjectNotFoundException;
import com.plataforma.projects.model.ProcessedEvent;
import com.plataforma.projects.model.Project;
import com.plataforma.projects.model.UserHolding;
import com.plataforma.projects.repository.ProcessedEventRepository;
import com.plataforma.projects.repository.ProjectRepository;
import com.plataforma.projects.repository.UserHoldingRepository;
import com.plataforma.projects.service.UserHoldingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserHoldingServiceImpl implements UserHoldingService {

    private final UserHoldingRepository holdingRepository;
    private final ProjectRepository projectRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Override
    public Page<UserHoldingResponse> listHolders(Long projectId, Pageable pageable) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
        return holdingRepository.findByProjectId(projectId, pageable).map(UserHoldingResponse::from);
    }

    @Override
    @Transactional
    public void updateHolding(Long userId, Long projectId, BigDecimal tokensDelta, String eventId) {
        if (eventId != null && processedEventRepository.existsByEventId(eventId)) {
            log.info("Evento {} ya procesado, ignorando (idempotencia)", eventId);
            return;
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        UserHolding holding = holdingRepository
                .findByUserIdAndProjectId(userId, projectId)
                .orElseGet(() -> UserHolding.builder()
                        .userId(userId)
                        .project(project)
                        .tokensAmount(BigDecimal.ZERO)
                        .build());

        BigDecimal newAmount = holding.getTokensAmount().add(tokensDelta);
        if (newAmount.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Holding negativo ignorado: userId={} projectId={} delta={}", userId, projectId, tokensDelta);
            return;
        }

        holding.setTokensAmount(newAmount);
        holding.setLastUpdatedAt(LocalDateTime.now());
        holdingRepository.save(holding);

        if (eventId != null) {
            processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).build());
        }
    }

    /**
     * Registra una compra primaria proveniente del evento on-chain TokensPurchased.
     * Difiere de {@link #updateHolding} en que persiste {@code walletAddress} y
     * {@code usdcInvested}, además del delta de LKN.
     */
    @Override
    @Transactional
    public void recordTokenPurchase(String walletAddress, Long userId, Long projectId,
                                    BigDecimal lknAmount, BigDecimal usdcAmount, String eventId) {
        if (eventId != null && processedEventRepository.existsByEventId(eventId)) {
            log.info("Evento {} ya procesado, ignorando (idempotencia)", eventId);
            return;
        }
        if (userId == null) {
            log.warn("recordTokenPurchase recibido con userId=null wallet={} projectId={}; " +
                    "se descarta hasta que el lookup wallet→user devuelva un id válido",
                    walletAddress, projectId);
            return;
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        UserHolding holding = holdingRepository
                .findByUserIdAndProjectId(userId, projectId)
                .orElseGet(() -> UserHolding.builder()
                        .userId(userId)
                        .project(project)
                        .tokensAmount(BigDecimal.ZERO)
                        .usdcInvested(BigDecimal.ZERO)
                        .build());

        holding.setTokensAmount(holding.getTokensAmount().add(lknAmount));
        BigDecimal currentUsdc = holding.getUsdcInvested() != null ? holding.getUsdcInvested() : BigDecimal.ZERO;
        holding.setUsdcInvested(currentUsdc.add(usdcAmount));
        if (walletAddress != null) {
            holding.setWalletAddress(walletAddress);
        }
        holding.setLastUpdatedAt(LocalDateTime.now());
        holdingRepository.save(holding);

        if (eventId != null) {
            processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).build());
        }
    }

    @Override
    @Transactional
    public void processOrderMatched(Long sellerId, Long buyerId, Long projectId, BigDecimal amount, String eventId) {
        if (eventId != null && processedEventRepository.existsByEventId(eventId)) {
            log.info("Evento {} ya procesado, ignorando (idempotencia)", eventId);
            return;
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        UserHolding sellerHolding = holdingRepository
                .findByUserIdAndProjectId(sellerId, projectId)
                .orElseGet(() -> UserHolding.builder()
                        .userId(sellerId)
                        .project(project)
                        .tokensAmount(BigDecimal.ZERO)
                        .build());

        if (sellerHolding.getTokensAmount().compareTo(amount) < 0) {
            log.warn("Saldo insuficiente para order_matched: sellerId={} projectId={} required={} available={}",
                    sellerId, projectId, amount, sellerHolding.getTokensAmount());
            return;
        }

        UserHolding buyerHolding = holdingRepository
                .findByUserIdAndProjectId(buyerId, projectId)
                .orElseGet(() -> UserHolding.builder()
                        .userId(buyerId)
                        .project(project)
                        .tokensAmount(BigDecimal.ZERO)
                        .build());

        sellerHolding.setTokensAmount(sellerHolding.getTokensAmount().subtract(amount));
        sellerHolding.setLastUpdatedAt(LocalDateTime.now());
        holdingRepository.save(sellerHolding);

        buyerHolding.setTokensAmount(buyerHolding.getTokensAmount().add(amount));
        buyerHolding.setLastUpdatedAt(LocalDateTime.now());
        holdingRepository.save(buyerHolding);

        if (eventId != null) {
            processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).build());
        }
    }
}
