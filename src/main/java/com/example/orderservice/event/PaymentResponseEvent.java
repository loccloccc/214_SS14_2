package com.example.orderservice.event;


public class PaymentResponseEvent {

    private final Long orderId;
    private final PaymentStatus status;

    public PaymentResponseEvent(Long orderId, PaymentStatus status) {
        this.orderId = orderId;
        this.status = status;
    }

    public Long getOrderId() {
        return orderId;
    }

    public PaymentStatus getStatus() {
        return status;
    }


    public enum PaymentStatus {
        SUCCESS,
        REJECTED,
        FAILED
    }
}
