package io.github.hardyalsinghdeora.orders.domain;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {

    private final UUID orderId;

    public OrderNotFoundException(UUID orderId) {
        super("Order %s not found".formatted(orderId));
        this.orderId = orderId;
    }

    public UUID orderId() {
        return orderId;
    }
}
