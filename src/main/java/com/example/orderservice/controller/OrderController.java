package com.example.orderservice.controller;

import com.example.orderservice.entity.Order;
import com.example.orderservice.event.PaymentResponseEvent;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.service.OrderService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;

    public OrderController(OrderService orderService, ApplicationEventPublisher eventPublisher) {
        this.orderService = orderService;
        this.eventPublisher = eventPublisher;
    }


    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Map<String, String> body) {
        String customerName = body.get("customerName");
        if (customerName == null || customerName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Order order = orderService.createOrder(customerName);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }


    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable Long id) {
        Order order = orderService.getOrderById(id);
        return ResponseEntity.ok(order);
    }


    @PostMapping("/{orderId}/payment-response")
    public ResponseEntity<?> handlePaymentResponse(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> body) {

        String statusStr = body.get("status");
        if (statusStr == null || statusStr.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing 'status' field. Valid values: SUCCESS, REJECTED, FAILED"));
        }

        PaymentResponseEvent.PaymentStatus paymentStatus;
        try {
            paymentStatus = PaymentResponseEvent.PaymentStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid status: " + statusStr + ". Valid values: SUCCESS, REJECTED, FAILED"));
        }

        // Publish event - OrderService se lang nghe va xu ly
        PaymentResponseEvent event = new PaymentResponseEvent(orderId, paymentStatus);
        eventPublisher.publishEvent(event);

        // Tra ve Order sau khi xu ly
        Order order = orderService.getOrderById(orderId);
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{id}/simulate-timeout")
    public ResponseEntity<?> simulateTimeout(@PathVariable Long id) {
        Order order = orderService.simulateTimeout(id);
        return ResponseEntity.ok(Map.of(
                "message", "createdAt set to 10 minutes ago. Scheduled job will mark this order as FAILED within 60 seconds.",
                "orderId", order.getId(),
                "currentStatus", order.getStatus(),
                "createdAt", order.getCreatedAt().toString()
        ));
    }


    @PostMapping("/{id}/ship")
    public ResponseEntity<?> shipOrder(@PathVariable Long id) {
        Order order = orderService.shipOrder(id);
        return ResponseEntity.ok(order);
    }


    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleOrderNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }
}
