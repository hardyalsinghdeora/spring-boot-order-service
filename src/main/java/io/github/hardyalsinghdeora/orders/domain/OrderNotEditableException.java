package io.github.hardyalsinghdeora.orders.domain;

import java.util.UUID;

public class OrderNotEditableException extends RuntimeException {

    private final UUID orderId;
    private final OrderStatus currentStatus;

    public OrderNotEditableException(UUID orderId, OrderStatus currentStatus) {
        super("Order %s lines can only be changed while DRAFT; current status is %s".formatted(orderId, currentStatus));
        this.orderId = orderId;
        this.currentStatus = currentStatus;
    }

    public UUID orderId() {
        return orderId;
    }

    public OrderStatus currentStatus() {
        return currentStatus;
    }
}
