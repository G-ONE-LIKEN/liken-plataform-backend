package com.plataforma.marketplace.service;

import com.plataforma.marketplace.dto.CreateOrderRequest;
import com.plataforma.marketplace.event.OrderMatchedPublisher;
import com.plataforma.marketplace.model.Order;
import com.plataforma.marketplace.model.OrderStatus;
import com.plataforma.marketplace.model.Trade;
import com.plataforma.marketplace.repository.OrderRepository;
import com.plataforma.marketplace.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private TradeRepository tradeRepository;
    @Mock private ProjectClient projectClient;
    @Mock private WalletClient walletClient;
    @Mock private OrderMatchedPublisher orderMatchedPublisher;

    @InjectMocks private OrderService orderService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "feePercent", 1.0);
        ReflectionTestUtils.setField(orderService, "orderTtlDays", 30);
    }

    // ─── createSellOrder ─────────────────────────────────────

    @Test
    void createSellOrder_success() {
        when(projectClient.isProjectTradeable(1L)).thenReturn(true);
        when(projectClient.getUserHoldings(10L, 1L)).thenReturn(new BigDecimal("100"));
        when(orderRepository.sumTokensAmountBySellerIdAndProjectIdAndStatusIn(10L, 1L, List.of(OrderStatus.OPEN, OrderStatus.PENDING_SETTLEMENT)))
                .thenReturn(BigDecimal.ZERO);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(42L);
            return o;
        });

        var request = new CreateOrderRequest(1L, new BigDecimal("50"), new BigDecimal("2.50"));
        Order result = orderService.createSellOrder(10L, request);

        assertEquals(42L, result.getId());
        assertEquals(OrderStatus.OPEN, result.getStatus());
        assertEquals(10L, result.getSellerId());
        assertEquals(1L, result.getProjectId());
        assertNotNull(result.getExpiresAt());
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createSellOrder_projectNotTradeable_throwsException() {
        when(projectClient.isProjectTradeable(1L)).thenReturn(false);

        var request = new CreateOrderRequest(1L, new BigDecimal("50"), new BigDecimal("2.50"));
        assertThrows(IllegalStateException.class,
                () -> orderService.createSellOrder(10L, request));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createSellOrder_insufficientHoldings_throwsException() {
        when(projectClient.isProjectTradeable(1L)).thenReturn(true);
        when(projectClient.getUserHoldings(10L, 1L)).thenReturn(new BigDecimal("10"));
        when(orderRepository.sumTokensAmountBySellerIdAndProjectIdAndStatusIn(10L, 1L, List.of(OrderStatus.OPEN, OrderStatus.PENDING_SETTLEMENT)))
                .thenReturn(BigDecimal.ZERO);

        var request = new CreateOrderRequest(1L, new BigDecimal("50"), new BigDecimal("2.50"));
        assertThrows(IllegalStateException.class,
                () -> orderService.createSellOrder(10L, request));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createSellOrder_insufficientAvailableHoldingsDueToLockedTokens_throwsException() {
        when(projectClient.isProjectTradeable(1L)).thenReturn(true);
        when(projectClient.getUserHoldings(10L, 1L)).thenReturn(new BigDecimal("100"));
        when(orderRepository.sumTokensAmountBySellerIdAndProjectIdAndStatusIn(10L, 1L, List.of(OrderStatus.OPEN, OrderStatus.PENDING_SETTLEMENT)))
                .thenReturn(new BigDecimal("60")); // 100 - 60 = 40 available

        var request = new CreateOrderRequest(1L, new BigDecimal("50"), new BigDecimal("2.50")); // Needs 50
        assertThrows(IllegalStateException.class,
                () -> orderService.createSellOrder(10L, request));
        verify(orderRepository, never()).save(any());
    }

    // ─── buyOrder ────────────────────────────────────────────

    @Test
    void buyOrder_success() {
        Order openOrder = Order.builder()
                .id(1L)
                .sellerId(10L)
                .projectId(5L)
                .tokensAmount(new BigDecimal("100"))
                .pricePerToken(new BigDecimal("2.00"))
                .status(OrderStatus.OPEN)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        when(orderRepository.findByIdAndStatusForUpdate(1L, OrderStatus.OPEN))
                .thenReturn(Optional.of(openOrder));
        when(projectClient.getUserHoldings(10L, 5L))
                .thenReturn(new BigDecimal("100"));
        when(walletClient.getUserBalance(20L))
                .thenReturn(new BigDecimal("500"));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(inv -> {
            Trade t = inv.getArgument(0);
            t.setId(99L);
            return t;
        });

        Trade trade = orderService.buyOrder(1L, 20L);

        assertEquals(99L, trade.getId());
        assertEquals(10L, trade.getSellerId());
        assertEquals(20L, trade.getBuyerId());
        assertEquals(5L, trade.getProjectId());
        assertEquals(0, new BigDecimal("200.000000").compareTo(trade.getTotalPrice()));
        // Fee = 200 * 1% = 2.00
        assertEquals(0, new BigDecimal("2.000000").compareTo(trade.getFeeAmount()));

        // Verify order is now MATCHED
        assertEquals(OrderStatus.MATCHED, openOrder.getStatus());

        // Verify Kafka event published
        verify(orderMatchedPublisher).publish(
                eq(10L), eq(20L), eq(5L),
                eq(new BigDecimal("100")),
                eq(new BigDecimal("200.000000")),
                eq(1L));
    }

    @Test
    void buyOrder_ownOrder_throwsException() {
        Order order = Order.builder()
                .id(1L)
                .sellerId(10L)
                .status(OrderStatus.OPEN)
                .build();
        when(orderRepository.findByIdAndStatusForUpdate(1L, OrderStatus.OPEN))
                .thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class,
                () -> orderService.buyOrder(1L, 10L));
        verify(tradeRepository, never()).save(any());
    }

    @Test
    void buyOrder_orderNotOpen_throwsException() {
        when(orderRepository.findByIdAndStatusForUpdate(1L, OrderStatus.OPEN))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> orderService.buyOrder(1L, 20L));
    }

    @Test
    void buyOrder_sellerInsufficientHoldings_throwsException() {
        Order order = Order.builder()
                .id(1L)
                .sellerId(10L)
                .projectId(5L)
                .tokensAmount(new BigDecimal("100"))
                .pricePerToken(new BigDecimal("2.00"))
                .status(OrderStatus.OPEN)
                .build();
        when(orderRepository.findByIdAndStatusForUpdate(1L, OrderStatus.OPEN))
                .thenReturn(Optional.of(order));
        when(projectClient.getUserHoldings(10L, 5L))
                .thenReturn(new BigDecimal("50")); // Not enough

        assertThrows(IllegalStateException.class,
                () -> orderService.buyOrder(1L, 20L));
        verify(tradeRepository, never()).save(any());
    }

    @Test
    void buyOrder_insufficientBuyerBalance_throwsException() {
        Order order = Order.builder()
                .id(1L)
                .sellerId(10L)
                .projectId(5L)
                .tokensAmount(new BigDecimal("100"))
                .pricePerToken(new BigDecimal("2.00"))
                .status(OrderStatus.OPEN)
                .build();
        when(orderRepository.findByIdAndStatusForUpdate(1L, OrderStatus.OPEN))
                .thenReturn(Optional.of(order));
        when(projectClient.getUserHoldings(10L, 5L))
                .thenReturn(new BigDecimal("100"));
        when(walletClient.getUserBalance(20L))
                .thenReturn(new BigDecimal("50")); // Not enough: 50 < 200

        assertThrows(IllegalStateException.class,
                () -> orderService.buyOrder(1L, 20L));
        verify(tradeRepository, never()).save(any());
    }

    // ─── cancelOrder ─────────────────────────────────────────

    @Test
    void cancelOrder_success() {
        Order order = Order.builder()
                .id(1L)
                .sellerId(10L)
                .status(OrderStatus.OPEN)
                .build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.cancelOrder(1L, 10L);

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void cancelOrder_notOwner_throwsException() {
        Order order = Order.builder()
                .id(1L)
                .sellerId(10L)
                .status(OrderStatus.OPEN)
                .build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class,
                () -> orderService.cancelOrder(1L, 99L));
    }

    @Test
    void cancelOrder_notOpen_throwsException() {
        Order order = Order.builder()
                .id(1L)
                .sellerId(10L)
                .status(OrderStatus.MATCHED)
                .build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class,
                () -> orderService.cancelOrder(1L, 10L));
    }

    // ─── expireOrders ────────────────────────────────────────

    @Test
    void expireOrders_marksExpiredOrders() {
        Order expired1 = Order.builder().id(1L).status(OrderStatus.OPEN)
                .expiresAt(LocalDateTime.now().minusDays(1)).build();
        Order expired2 = Order.builder().id(2L).status(OrderStatus.OPEN)
                .expiresAt(LocalDateTime.now().minusHours(2)).build();

        when(orderRepository.findByStatusAndExpiresAtBefore(eq(OrderStatus.OPEN), any()))
                .thenReturn(List.of(expired1, expired2));

        orderService.expireOrders();

        assertEquals(OrderStatus.EXPIRED, expired1.getStatus());
        assertEquals(OrderStatus.EXPIRED, expired2.getStatus());
        verify(orderRepository).saveAll(List.of(expired1, expired2));
    }

    // ─── cancelOrdersForProject ──────────────────────────────

    @Test
    void cancelOrdersForProject_cancelsOpenOrders() {
        Order o1 = Order.builder().id(1L).projectId(5L).status(OrderStatus.OPEN).build();
        Order o2 = Order.builder().id(2L).projectId(5L).status(OrderStatus.OPEN).build();

        when(orderRepository.findByStatusAndProjectId(OrderStatus.OPEN, 5L))
                .thenReturn(List.of(o1, o2));

        int cancelled = orderService.cancelOrdersForProject(5L, "Proyecto cancelado");

        assertEquals(2, cancelled);
        assertEquals(OrderStatus.CANCELLED, o1.getStatus());
        assertEquals(OrderStatus.CANCELLED, o2.getStatus());
    }
}
