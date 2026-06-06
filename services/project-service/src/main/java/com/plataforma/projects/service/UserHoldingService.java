package com.plataforma.projects.service;

import com.plataforma.projects.dto.UserHoldingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface UserHoldingService {
    Page<UserHoldingResponse> listHolders(Long projectId, Pageable pageable);

    /**
     * Versión legacy del update (sin info on-chain). Conservada por compatibilidad
     * con tests y flujos sin wallet vinculada. La versión nueva con datos on-chain
     * es {@link #recordTokenPurchase}.
     */
    void updateHolding(Long userId, Long projectId, BigDecimal tokensDelta, String eventId);

    /**
     * Registra una compra primaria proveniente del evento on-chain {@code TokensPurchased}.
     * Trae la wallet del comprador y el USDC pagado, en unidades ya convertidas a BigDecimal.
     *
     * @param walletAddress dirección on-chain (EIP-55) del comprador.
     * @param userId        userId resuelto del walletAddress por el Blockchain Service. Puede
     *                      ser {@code null} si el comprador no estaba registrado al momento
     *                      del evento (en ese caso queda en orfanato hasta vinculación).
     * @param projectId     id local del proyecto en la base.
     * @param lknAmount     cantidad de LKN comprados (18 decimales → BigDecimal escala 8).
     * @param usdcAmount    cantidad de USDC pagada (6 decimales → BigDecimal escala 6).
     * @param eventId       id idempotente del evento Kafka.
     */
    void recordTokenPurchase(String walletAddress, Long userId, Long projectId,
                             BigDecimal lknAmount, BigDecimal usdcAmount, String eventId);

    void processOrderMatched(Long sellerId, Long buyerId, Long projectId, BigDecimal amount, String eventId);
}
