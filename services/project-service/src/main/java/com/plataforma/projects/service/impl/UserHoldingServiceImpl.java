package com.plataforma.projects.service.impl;

import com.plataforma.projects.dto.UserHoldingResponse;
import com.plataforma.projects.exception.ProjectNotFoundException;
import com.plataforma.projects.model.ProcessedEvent;
import com.plataforma.projects.model.Project;
import com.plataforma.projects.model.UserHolding;
import com.plataforma.projects.repository.ProcessedEventRepository;
import com.plataforma.projects.repository.ProjectRepository;
import com.plataforma.projects.repository.UserHoldingRepository;
import com.plataforma.projects.service.ProjectService;
import com.plataforma.projects.service.UserHoldingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserHoldingServiceImpl implements UserHoldingService {

    private final UserHoldingRepository holdingRepository;
    private final ProjectRepository projectRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ProjectService projectService;

    @Override
    public Page<UserHoldingResponse> listHolders(Long projectId, Pageable pageable) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
        return holdingRepository.findByProjectId(projectId, pageable).map(UserHoldingResponse::from);
    }

    @Override
    public UserHoldingResponse getUserHolding(Long projectId, Long userId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
        return holdingRepository.findByUserIdAndProjectId(userId, projectId)
                .map(UserHoldingResponse::from)
                .orElseGet(() -> UserHoldingResponse.builder()
                        .userId(userId)
                        .projectId(projectId)
                        .tokensAmount(BigDecimal.ZERO)
                        .usdcInvested(BigDecimal.ZERO)
                        .build());
    }

    @Override
    public List<UserHoldingResponse> listMyHoldings(Long userId) {
        return holdingRepository.findByUserId(userId)
                .stream()
                .map(UserHoldingResponse::from)
                .toList();
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

    @Override
    @Transactional
    public void recordTokenPurchase(String walletAddress, Long userId, Long projectId,
                                    BigDecimal lknAmount, BigDecimal usdcAmount, String eventId) {
        if (eventId != null && processedEventRepository.existsByEventId(eventId)) {
            log.info("Evento {} ya procesado, ignorando (idempotencia)", eventId);
            return;
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        BigDecimal currentRaised = project.getRaisedAmount() != null ? project.getRaisedAmount() : BigDecimal.ZERO;
        BigDecimal currentSold = project.getTotalTokensSold() != null ? project.getTotalTokensSold() : BigDecimal.ZERO;
        project.setRaisedAmount(currentRaised.add(usdcAmount));
        project.setTotalTokensSold(currentSold.add(lknAmount));
        projectRepository.save(project);

        // Si esta compra hizo cruzar el softCap, transicionar PRE_OPEN → OPEN
        // para que aparezca en marketplace. La ronda primaria on-chain sigue activa.
        projectService.applyPurchaseAccrual(projectId, usdcAmount, lknAmount);

        UserHolding holding;
        if (userId != null) {
            holding = holdingRepository
                    .findByUserIdAndProjectId(userId, projectId)
                    .or(() -> walletAddress != null
                            ? holdingRepository.findByWalletAddressIgnoreCaseAndProjectId(walletAddress, projectId)
                            : java.util.Optional.empty())
                    .orElseGet(() -> UserHolding.builder()
                            .userId(userId)
                            .project(project)
                            .tokensAmount(BigDecimal.ZERO)
                            .usdcInvested(BigDecimal.ZERO)
                            .build());
        } else if (walletAddress != null) {
            holding = holdingRepository
                    .findByWalletAddressIgnoreCaseAndProjectId(walletAddress, projectId)
                    .orElseGet(() -> UserHolding.builder()
                            .walletAddress(walletAddress)
                            .project(project)
                            .tokensAmount(BigDecimal.ZERO)
                            .usdcInvested(BigDecimal.ZERO)
                            .build());
        } else {
            log.warn("Compra registrada en proyecto sin userId ni walletAddress: projectId={} lkn={} usdc={}",
                    projectId, lknAmount, usdcAmount);
            if (eventId != null) {
                processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).build());
            }
            return;
        }

        if (userId != null && holding.getUserId() == null) {
            holding.setUserId(userId);
        }
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
    public int reconcileWalletLinked(Long userId, String walletAddress) {
        List<UserHolding> orphanHoldings = holdingRepository.findByUserIdIsNullAndWalletAddressIgnoreCase(walletAddress);
        int reconciled = 0;

        for (UserHolding orphan : orphanHoldings) {
            Long projectId = orphan.getProject().getId();
            UserHolding target = holdingRepository.findByUserIdAndProjectId(userId, projectId).orElse(null);

            if (target == null) {
                orphan.setUserId(userId);
                orphan.setWalletAddress(walletAddress);
                orphan.setLastUpdatedAt(LocalDateTime.now());
                holdingRepository.save(orphan);
            } else if (!target.getId().equals(orphan.getId())) {
                target.setTokensAmount(target.getTokensAmount().add(orphan.getTokensAmount()));
                BigDecimal currentUsdc = target.getUsdcInvested() != null ? target.getUsdcInvested() : BigDecimal.ZERO;
                BigDecimal orphanUsdc = orphan.getUsdcInvested() != null ? orphan.getUsdcInvested() : BigDecimal.ZERO;
                target.setUsdcInvested(currentUsdc.add(orphanUsdc));
                target.setWalletAddress(walletAddress);
                target.setLastUpdatedAt(LocalDateTime.now());
                holdingRepository.save(target);
                holdingRepository.delete(orphan);
            }

            reconciled++;
        }

        return reconciled;
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
