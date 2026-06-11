package com.plataforma.marketplace.controller;

import com.plataforma.marketplace.dto.CreateOrderRequest;
import com.plataforma.marketplace.dto.OrderResponse;
import com.plataforma.marketplace.dto.TradeResponse;
import com.plataforma.marketplace.model.Trade;
import com.plataforma.marketplace.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * API REST del marketplace P2P de tokens.
 *
 * <p>Endpoints públicos (via gateway):
 * <ul>
 *   <li>{@code GET /api/marketplace/orders} — listar órdenes activas (público).</li>
 *   <li>{@code GET /api/marketplace/orders/me} — órdenes propias (autenticado).</li>
 *   <li>{@code POST /api/marketplace/orders} — crear orden de venta (autenticado).</li>
 *   <li>{@code DELETE /api/marketplace/orders/{id}} — cancelar orden propia (owner).</li>
 *   <li>{@code POST /api/marketplace/orders/{id}/buy} — comprar orden (autenticado).</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/marketplace")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Lista órdenes OPEN del marketplace. Público (visible sin autenticación).
     * Opcionalmente filtrable por projectId.
     */
    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> listOpenOrders(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<OrderResponse> orders = orderService.listOpenOrders(projectId, page, size)
                .map(OrderResponse::from);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Órdenes activas obtenidas",
                "data", orders.getContent(),
                "totalPages", orders.getTotalPages(),
                "totalElements", orders.getTotalElements(),
                "page", orders.getNumber()
        ));
    }

    /**
     * Lista todas las órdenes del usuario autenticado (cualquier estado).
     */
    @GetMapping("/orders/me")
    public ResponseEntity<Map<String, Object>> listMyOrders(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = (Long) auth.getPrincipal();
        Page<OrderResponse> orders = orderService.listMyOrders(userId, page, size)
                .map(OrderResponse::from);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Tus órdenes obtenidas",
                "data", orders.getContent(),
                "totalPages", orders.getTotalPages(),
                "totalElements", orders.getTotalElements(),
                "page", orders.getNumber()
        ));
    }

    /**
     * Crea una orden de venta de tokens.
     */
    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> createOrder(
            Authentication auth,
            @Valid @RequestBody CreateOrderRequest request) {

        Long userId = (Long) auth.getPrincipal();
        var order = orderService.createSellOrder(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "message", "Orden de venta creada",
                "data", OrderResponse.from(order)
        ));
    }

    /**
     * Cancela una orden propia que esté OPEN.
     */
    @DeleteMapping("/orders/{id}")
    public ResponseEntity<Map<String, Object>> cancelOrder(
            @PathVariable Long id,
            Authentication auth) {

        Long userId = (Long) auth.getPrincipal();
        orderService.cancelOrder(id, userId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Orden cancelada"
        ));
    }

    /**
     * Compra los tokens de una orden OPEN. El matching es completo (sin partial fills).
     */
    @PostMapping("/orders/{id}/buy")
    public ResponseEntity<Map<String, Object>> buyOrder(
            @PathVariable Long id,
            Authentication auth) {

        Long buyerId = (Long) auth.getPrincipal();
        Trade trade = orderService.buyOrder(id, buyerId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Compra completada",
                "data", TradeResponse.from(trade)
        ));
    }

    /**
     * Historial de transacciones del usuario (como comprador o vendedor).
     */
    @GetMapping("/transactions")
    public ResponseEntity<Map<String, Object>> listMyTransactions(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = (Long) auth.getPrincipal();
        Page<TradeResponse> trades = orderService.listMyTrades(userId, page, size)
                .map(TradeResponse::from);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Historial de transacciones obtenido",
                "data", trades.getContent(),
                "totalPages", trades.getTotalPages(),
                "totalElements", trades.getTotalElements(),
                "page", trades.getNumber()
        ));
    }

    // ─── Exception Handlers ──────────────────────────────────

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "success", false,
                "message", ex.getMessage()
        ));
    }
}
