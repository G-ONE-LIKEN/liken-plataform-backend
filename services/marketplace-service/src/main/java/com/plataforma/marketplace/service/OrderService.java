package com.plataforma.marketplace.service;

import com.plataforma.marketplace.dto.CreateOrderRequest;
import com.plataforma.marketplace.event.OrderMatchedPublisher;
import com.plataforma.marketplace.event.TradeSettledPublisher;
import com.plataforma.marketplace.model.Order;
import com.plataforma.marketplace.model.OrderStatus;
import com.plataforma.marketplace.model.Trade;
import com.plataforma.marketplace.repository.OrderRepository;
import com.plataforma.marketplace.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Motor central del marketplace P2P.
 *
 * <p>Implementa un matching FIFO con price-time priority, sin matching parcial
 * (una orden se ejecuta completa o queda OPEN). Decision documentada en
 * <a href="../../docs/adr/ADR-0014-Matching-engine-de-marketplace-service">ADR-0014</a>.
 *
 * <p>Flujo de una transaccion:
 * <ol>
 *   <li>El vendedor crea una orden de venta (valida holdings contra project-service).</li>
 *   <li>Un comprador ejecuta la orden (lock pesimista → match completo).</li>
 *   <li>Se crea un {@link Trade} y se publica {@code marketplace.order_matched}.</li>
 *   <li>Los consumidores en wallet-service y project-service actualizan holdings y movimientos.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final ProjectClient projectClient;
    private final WalletClient walletClient;
    private final OrderMatchedPublisher orderMatchedPublisher;
    private final TradeSettledPublisher tradeSettledPublisher;

    @Value("${marketplace.fee-percent:1.0}")
    private double feePercent;

    @Value("${marketplace.order-ttl-days:30}")
    private int orderTtlDays;

    // ─────────────────────── QUERIES ──────────────────────

    /**
     * Lista las ordenes OPEN, opcionalmente filtradas por proyecto.
     */
    @Transactional(readOnly = true)
    public Page<Order> listOpenOrders(Long projectId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        if (projectId != null) {
            return orderRepository.findByStatusAndProjectIdOrderByPricePerTokenAsc(
                    OrderStatus.OPEN, projectId, pageable);
        }
        return orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.OPEN, pageable);
    }

    /**
     * Lista todas las ordenes del vendedor (cualquier estado).
     */
    @Transactional(readOnly = true)
    public Page<Order> listMyOrders(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return orderRepository.findBySellerIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Historial de transacciones donde el usuario participo como comprador o vendedor.
     */
    @Transactional(readOnly = true)
    public Page<Trade> listMyTrades(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return tradeRepository.findBySellerIdOrBuyerIdOrderByCreatedAtDesc(userId, userId, pageable);
    }

    // ─────────────────────── COMMANDS ──────────────────────

    /**
     * Crea una orden de venta. Valida:
     * <ul>
     *   <li>Que el proyecto sea tradeable (ronda finalizada exitosamente).</li>
     *   <li>Que el vendedor tenga holdings suficientes en ese proyecto.</li>
     * </ul>
     */
    @Transactional
    public Order createSellOrder(Long sellerId, CreateOrderRequest request) {
        Long projectId = request.getProjectId();

        // 1. Validar que el proyecto permite trading.
        if (!projectClient.isProjectTradeable(projectId)) {
            throw new IllegalStateException(
                    "El proyecto " + projectId + " no esta disponible para trading. " +
                    "Solo se pueden negociar tokens de proyectos con ronda finalizada exitosamente.");
        }

        // 2. Validar holdings del vendedor.
        BigDecimal currentHoldings = projectClient.getUserHoldings(sellerId, projectId);
        BigDecimal lockedTokens = orderRepository.sumTokensAmountBySellerIdAndProjectIdAndStatusIn(
                sellerId, projectId, List.of(OrderStatus.OPEN, OrderStatus.PENDING_SETTLEMENT));
        BigDecimal availableHoldings = currentHoldings.subtract(lockedTokens);
        if (availableHoldings.compareTo(request.getTokensAmount()) < 0) {
            throw new IllegalStateException(
                    "Holdings insuficientes: tenés " + currentHoldings +
                    " tokens (" + lockedTokens + " reservados en órdenes abiertas) pero intentas vender " + request.getTokensAmount());
        }

        // 3. Crear la orden.
        Order order = Order.builder()
                .sellerId(sellerId)
                .projectId(projectId)
                .tokensAmount(request.getTokensAmount())
                .pricePerToken(request.getPricePerToken())
                .status(OrderStatus.OPEN)
                .expiresAt(LocalDateTime.now().plusDays(orderTtlDays))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        order = orderRepository.save(order);
        log.info("Orden de venta creada: id={} sellerId={} projectId={} tokens={} price={}",
                order.getId(), sellerId, projectId, request.getTokensAmount(), request.getPricePerToken());

        return order;
    }

    /**
     * Ejecuta una compra contra una orden OPEN.
     *
     * <p>Matching sincrono con {@code SELECT FOR UPDATE} (lock pesimista) para
     * garantizar que la orden no se ejecute dos veces. Sin matching parcial.
     */
    @Transactional
    public Trade buyOrder(Long orderId, Long buyerId) {
        // 1. Lock pesimista: obtener la orden OPEN o fallar.
        Order order = orderRepository.findByIdAndStatusForUpdate(orderId, OrderStatus.OPEN)
                .orElseThrow(() -> new IllegalStateException(
                        "La orden " + orderId + " no existe o ya no esta disponible."));

        // 2. No puede comprar sus propios tokens.
        if (order.getSellerId().equals(buyerId)) {
            throw new IllegalStateException("No podés comprar tu propia orden.");
        }

        // 3. Re-validar holdings del vendedor (ventana de carrera minima, ADR-0014).
        BigDecimal sellerHoldings = projectClient.getUserHoldings(order.getSellerId(), order.getProjectId());
        if (sellerHoldings.compareTo(order.getTokensAmount()) < 0) {
            log.warn("Holdings insuficientes del vendedor al momento del match: " +
                    "sellerId={} projectId={} needed={} available={}",
                    order.getSellerId(), order.getProjectId(),
                    order.getTokensAmount(), sellerHoldings);
            throw new IllegalStateException(
                    "El vendedor ya no tiene suficientes tokens para completar esta orden.");
        }

        // 4. Calcular precio total y fee.
        BigDecimal totalPrice = order.getTokensAmount()
                .multiply(order.getPricePerToken())
                .setScale(6, RoundingMode.HALF_UP);
        BigDecimal fee = totalPrice
                .multiply(BigDecimal.valueOf(feePercent))
                .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);

        // 5. Marcar la orden como PENDING_SETTLEMENT.
        order.setStatus(OrderStatus.PENDING_SETTLEMENT);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        log.info("Orden {} bloqueada en PENDING_SETTLEMENT para comprador: {}", order.getId(), buyerId);

        // 6. Publicar evento para iniciar liquidación en blockchain-service.
        orderMatchedPublisher.publish(
                order.getSellerId(), buyerId, order.getProjectId(),
                order.getTokensAmount(), totalPrice, order.getId());

        // 7. Retornar objeto Trade transitorio (sin guardar en base de datos aún)
        return Trade.builder()
                .id(0L) // Id temporal
                .orderId(order.getId())
                .sellerId(order.getSellerId())
                .buyerId(buyerId)
                .projectId(order.getProjectId())
                .tokensAmount(order.getTokensAmount())
                .pricePerToken(order.getPricePerToken())
                .totalPrice(totalPrice)
                .feeAmount(fee)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Cancela una orden OPEN. Solo el dueño puede cancelarla.
     */
    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("La orden " + orderId + " no existe."));

        if (!order.getSellerId().equals(userId)) {
            throw new IllegalStateException("Solo el dueño puede cancelar su orden.");
        }
        if (order.getStatus() != OrderStatus.OPEN) {
            throw new IllegalStateException(
                    "Solo se pueden cancelar ordenes OPEN. Estado actual: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        log.info("Orden cancelada: id={} sellerId={}", orderId, userId);
    }

    /**
     * Cancela todas las ordenes OPEN de un proyecto (por cambio de estado).
     * Invocado desde el consumer de {@code projects.state_changed}.
     */
    @Transactional
    public int cancelOrdersForProject(Long projectId, String reason) {
        List<Order> openOrders = orderRepository.findByStatusAndProjectId(
                OrderStatus.OPEN, projectId);

        for (Order order : openOrders) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setUpdatedAt(LocalDateTime.now());
        }
        orderRepository.saveAll(openOrders);

        if (!openOrders.isEmpty()) {
            log.info("Canceladas {} ordenes del proyecto {} por: {}",
                    openOrders.size(), projectId, reason);
        }
        return openOrders.size();
    }

    // ─────────────────────── SCHEDULED ──────────────────────

    /**
     * Marca como EXPIRED las ordenes OPEN cuyo TTL vencio.
     * Se ejecuta cada hora.
     */
    @Scheduled(fixedRate = 3600_000) // 1 hora
    @Transactional
    public void expireOrders() {
        List<Order> expired = orderRepository.findByStatusAndExpiresAtBefore(
                OrderStatus.OPEN, LocalDateTime.now());

        for (Order order : expired) {
            order.setStatus(OrderStatus.EXPIRED);
            order.setUpdatedAt(LocalDateTime.now());
        }
        orderRepository.saveAll(expired);

        if (!expired.isEmpty()) {
            log.info("Expiradas {} ordenes vencidas", expired.size());
        }
    }

    /**
     * Procesa la confirmación on-chain de un trade y emite el evento definitivo.
     */
    @Transactional
    public void processTradeSettled(
            Long orderId,
            Long buyerId,
            BigDecimal tokenCount,
            BigDecimal totalPrice,
            BigDecimal feeAmount,
            String txHash,
            String eventId
    ) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("La orden " + orderId + " no existe."));

        if (order.getStatus() == OrderStatus.MATCHED) {
            log.info("La orden {} ya esta en estado MATCHED, ignorando.", orderId);
            return;
        }

        // Marcar la orden como MATCHED.
        order.setStatus(OrderStatus.MATCHED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        // Crear y guardar el Trade definitivo.
        Trade trade = Trade.builder()
                .orderId(order.getId())
                .sellerId(order.getSellerId())
                .buyerId(buyerId != null ? buyerId : 0L)
                .projectId(order.getProjectId())
                .tokensAmount(tokenCount)
                .pricePerToken(order.getPricePerToken())
                .totalPrice(totalPrice)
                .feeAmount(feeAmount)
                .createdAt(LocalDateTime.now())
                .build();
        trade = tradeRepository.save(trade);

        log.info("Trade persistido tras settlement on-chain: tradeId={} orderId={} txHash={}",
                trade.getId(), order.getId(), txHash);

        // Publicar evento final para que wallet-service y project-service actualicen sus holdings/movements
        tradeSettledPublisher.publish(
                order.getSellerId(),
                buyerId != null ? buyerId : 0L,
                order.getProjectId(),
                tokenCount,
                totalPrice,
                order.getId(),
                txHash
        );
    }

    /**
     * Revierte una orden PENDING_SETTLEMENT a OPEN si falló la liquidación on-chain.
     */
    @Transactional
    public void handleTradeFailed(Long orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.PENDING_SETTLEMENT) {
                order.setStatus(OrderStatus.OPEN);
                order.setUpdatedAt(LocalDateTime.now());
                orderRepository.save(order);
                log.info("Orden {} devuelta a OPEN tras fallo en liquidación on-chain", orderId);
            } else {
                log.warn("Se intentó revertir la orden {} pero no está en PENDING_SETTLEMENT. Estado actual: {}", orderId, order.getStatus());
            }
        });
    }
}
