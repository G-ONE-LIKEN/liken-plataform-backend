package com.plataforma.invest.service;

import com.plataforma.invest.model.DividendClaim;
import com.plataforma.invest.model.ProcessedEvent;
import com.plataforma.invest.repository.DividendClaimRepository;
import com.plataforma.invest.repository.ProcessedEventRepository;
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
public class DividendService {

    private final DividendClaimRepository claims;
    private final ProcessedEventRepository processed;

    @Transactional
    public void recordClaim(String eventId,
                            Long userId,
                            String walletAddress,
                            BigDecimal amount,
                            String txHash,
                            Long blockNumber) {
        if (eventId != null && processed.existsByEventId(eventId)) {
            log.debug("Evento {} ya procesado, ignorando (idempotencia)", eventId);
            return;
        }
        if (userId == null) {
            log.warn("recordClaim: userId null para wallet={} txHash={}. " +
                    "Se descarta hasta que se vincule la wallet.", walletAddress, txHash);
            return;
        }

        claims.save(DividendClaim.builder()
                .userId(userId)
                .walletAddress(walletAddress)
                .amount(amount)
                .txHash(txHash)
                .blockNumber(blockNumber)
                .createdAt(LocalDateTime.now())
                .build());

        if (eventId != null) {
            processed.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .topic("dividends.claimed")
                    .processedAt(LocalDateTime.now())
                    .build());
        }

        log.info("Dividendo registrado: userId={} amount={} txHash={}", userId, amount, txHash);
    }

    @Transactional(readOnly = true)
    public Page<DividendClaim> listForUser(Long userId, Pageable pageable) {
        return claims.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
}
