package com.example.orderservice;

import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderStatus;
import com.example.orderservice.event.PaymentResponseEvent;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho OrderService.
 *
 * Kiem tra toan bo State Machine:
 *   Test 1: PENDING + SUCCESS  -> PAID
 *   Test 2: PENDING + REJECTED -> CANCELED
 *   Test 3: PENDING + FAILED   -> FAILED
 *   Test 4: PENDING qua 5 phut -> FAILED (timeout)
 *   Test 5: PAID + SUCCESS     -> PAID (khong xu ly lai)
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceApplicationTests {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    private Order pendingOrder;
    private Order paidOrder;

    @BeforeEach
    void setUp() {
        // Tao Order PENDING mau
        pendingOrder = new Order();
        pendingOrder.setId(1L);
        pendingOrder.setCustomerName("Nguyen Van A");
        pendingOrder.setStatus(OrderStatus.PENDING);
        pendingOrder.setCreatedAt(LocalDateTime.now());
        pendingOrder.setUpdatedAt(LocalDateTime.now());

        // Tao Order PAID mau
        paidOrder = new Order();
        paidOrder.setId(2L);
        paidOrder.setCustomerName("Tran Thi B");
        paidOrder.setStatus(OrderStatus.PAID);
        paidOrder.setCreatedAt(LocalDateTime.now());
        paidOrder.setUpdatedAt(LocalDateTime.now());
    }

    // =========================================================
    // TEST 1: PENDING + SUCCESS -> PAID
    // =========================================================

    @Test
    @DisplayName("Test 1: PENDING + SUCCESS -> PAID")
    void whenPendingAndSuccessPayment_thenStatusBecomePaid() {
        // Given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseEvent event = new PaymentResponseEvent(1L, PaymentResponseEvent.PaymentStatus.SUCCESS);

        // When
        orderService.handlePaymentResponse(event);

        // Then
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository, times(1)).save(pendingOrder);
    }

    // =========================================================
    // TEST 2: PENDING + REJECTED -> CANCELED
    // =========================================================

    @Test
    @DisplayName("Test 2: PENDING + REJECTED -> CANCELED")
    void whenPendingAndRejectedPayment_thenStatusBecomeCanceled() {
        // Given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseEvent event = new PaymentResponseEvent(1L, PaymentResponseEvent.PaymentStatus.REJECTED);

        // When
        orderService.handlePaymentResponse(event);

        // Then
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
        verify(orderRepository, times(1)).save(pendingOrder);
    }

    // =========================================================
    // TEST 3: PENDING + FAILED -> FAILED
    // =========================================================

    @Test
    @DisplayName("Test 3: PENDING + FAILED -> FAILED")
    void whenPendingAndFailedPayment_thenStatusBecomeFailed() {
        // Given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseEvent event = new PaymentResponseEvent(1L, PaymentResponseEvent.PaymentStatus.FAILED);

        // When
        orderService.handlePaymentResponse(event);

        // Then
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(orderRepository, times(1)).save(pendingOrder);
    }

    // =========================================================
    // TEST 4: PENDING qua 5 phut -> FAILED (timeout)
    // =========================================================

    @Test
    @DisplayName("Test 4: PENDING qua 5 phut -> FAILED (timeout)")
    void whenPendingOrderExceedsTimeout_thenStatusBecomeFailed() {
        // Given: Order PENDING duoc tao 10 phut truoc
        Order timeoutOrder = new Order();
        timeoutOrder.setId(3L);
        timeoutOrder.setCustomerName("Le Van C");
        timeoutOrder.setStatus(OrderStatus.PENDING);
        timeoutOrder.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        timeoutOrder.setUpdatedAt(LocalDateTime.now().minusMinutes(10));

        when(orderRepository.findByStatusAndCreatedAtBefore(
                eq(OrderStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(timeoutOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        orderService.handleTimeoutOrders();

        // Then
        assertThat(timeoutOrder.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(orderRepository, times(1)).save(timeoutOrder);
    }

    // =========================================================
    // TEST 5: PAID + SUCCESS -> PAID (khong xu ly lai)
    // =========================================================

    @Test
    @DisplayName("Test 5: PAID + SUCCESS -> PAID (khong xu ly lai, giu nguyen)")
    void whenPaidAndSuccessPayment_thenStatusRemaindPaid() {
        // Given: Order da PAID
        when(orderRepository.findById(2L)).thenReturn(Optional.of(paidOrder));

        PaymentResponseEvent event = new PaymentResponseEvent(2L, PaymentResponseEvent.PaymentStatus.SUCCESS);

        // When
        orderService.handlePaymentResponse(event);

        // Then: trang thai van la PAID, KHONG goi save
        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository, never()).save(any(Order.class));
    }

    // =========================================================
    // TEST THEM: Throw OrderNotFoundException khi khong tim thay Order
    // =========================================================

    @Test
    @DisplayName("Throw OrderNotFoundException khi khong tim thay Order")
    void whenOrderNotFound_thenThrowOrderNotFoundException() {
        // Given
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        PaymentResponseEvent event = new PaymentResponseEvent(999L, PaymentResponseEvent.PaymentStatus.SUCCESS);

        // Then
        assertThatThrownBy(() -> orderService.handlePaymentResponse(event))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("999");
    }
}
