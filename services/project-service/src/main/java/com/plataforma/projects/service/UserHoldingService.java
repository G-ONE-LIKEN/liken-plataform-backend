package com.plataforma.projects.service;

import com.plataforma.projects.dto.UserHoldingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface UserHoldingService {
    Page<UserHoldingResponse> listHolders(Long projectId, Pageable pageable);
    void updateHolding(Long userId, Long projectId, BigDecimal tokensDelta, String eventId);
    void processOrderMatched(Long sellerId, Long buyerId, Long projectId, BigDecimal amount, String eventId);
}
