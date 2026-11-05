package io.github.hardyalsinghdeora.orders.domain;

import java.util.Set;
import java.util.UUID;

public class InvalidStateTransitionException extends RuntimeException {

    private final UUID orderId;
    private final OrderStatus currentStatus;
    private final OrderStatus requestedStatus;

    public InvalidStateTransitionException(UUID orderId, OrderStatus currentStatus, OrderStatus requestedStatus) {
        super("Order %s is %s and cannot move to %s; allowed transitions: %s".formatted(
                orderId, currentStatus, requestedStatus, describe(currentStatus.allowedTransitions())));
        this.orderId = orderId;
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
    }

    public UUID orderId() {
        return orderId;
    }

    public OrderStatus currentStatus() {
        return currentStatus;
    }

    public OrderStatus requestedStatus() {
        return requestedStatus;
    }

    public Set<OrderStatus> allowedTransitions() {
        return currentStatus.allowedTransitions();
    }

    private static String describe(Set<OrderStatus> transitions) {
        return transitions.isEmpty() ? "none (terminal status)" : transitions.toString();
    }
}
