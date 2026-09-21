package com.example.orderservice.service;

import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderStatus;
import com.example.orderservice.event.PaymentResponseEvent;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final long TIMEOUT_MINUTES = 5;

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }


    @Transactional
    public Order createOrder(String customerName) {
        Order order = new Order();
        order.setCustomerName(customerName);
        order.setStatus(OrderStatus.PENDING);

        Order saved = orderRepository.save(order);
        log.info("Created new Order id={}, customerName={}, status=PENDING", saved.getId(), saved.getCustomerName());
        return saved;
    }

    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @EventListener
    @Transactional
    public void handlePaymentResponse(PaymentResponseEvent event) {
        log.info("Received PaymentResponseEvent: orderId={}, paymentStatus={}", event.getOrderId(), event.getStatus());

        Order order = orderRepository.findById(event.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException(event.getOrderId()));

        // Neu Order khong con PENDING thi bo qua, khong xu ly lai
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("Order id={} is already in status={}. Ignoring payment event.", order.getId(), order.getStatus());
            return;
        }

        // Ap dung State Machine
        switch (event.getStatus()) {
            case SUCCESS -> {
                order.setStatus(OrderStatus.PAID);
                log.info("Order id={} PENDING + SUCCESS -> PAID", order.getId());
            }
            case REJECTED -> {
                order.setStatus(OrderStatus.CANCELED);
                log.info("Order id={} PENDING + REJECTED -> CANCELED", order.getId());
            }
            case FAILED -> {
                order.setStatus(OrderStatus.FAILED);
                log.info("Order id={} PENDING + FAILED -> FAILED", order.getId());
            }
        }

        orderRepository.save(order);
    }

    @Transactional
    public Order simulateTimeout(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // Dat createdAt ve 10 phut truoc (lon hon nguong 5 phut)
        order.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        Order saved = orderRepository.save(order);

        log.info("Simulated timeout for Order id={}: createdAt set to {} (10 minutes ago)", saved.getId(), saved.getCreatedAt());
        return saved;
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void handleTimeoutOrders() {
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);

        List<Order> pendingOrders = orderRepository.findByStatusAndCreatedAtBefore(
                OrderStatus.PENDING, timeoutThreshold);

        if (pendingOrders.isEmpty()) {
            log.debug("Timeout check: no PENDING orders exceeded {} minutes.", TIMEOUT_MINUTES);
            return;
        }

        log.info("Timeout check: found {} PENDING order(s) exceeding {} minutes.", pendingOrders.size(), TIMEOUT_MINUTES);

        for (Order order : pendingOrders) {
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            log.info("Order {} timeout after {} minutes -> FAILED", order.getId(), TIMEOUT_MINUTES);
        }
    }

    @Transactional
    public Order shipOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.PAID) {
            throw new IllegalStateException(
                    "Cannot ship Order id=" + orderId + ". Current status: " + order.getStatus() + ". Only PAID orders can be shipped.");
        }

        order.setStatus(OrderStatus.SHIPPED);
        Order saved = orderRepository.save(order);
        log.info("Order id={} PAID -> SHIPPED", saved.getId());
        return saved;
    }
}
