package io.github.hardyalsinghdeora.orders.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Order lifecycle. The transition table below is the whole state machine:
 * nothing else moves an order from one status to another.
 */
public enum OrderStatus {
    DRAFT,
    CONFIRMED,
    FULFILLED,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            DRAFT, EnumSet.of(CONFIRMED, CANCELLED),
            CONFIRMED, EnumSet.of(FULFILLED, CANCELLED),
            FULFILLED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class));

    public Set<OrderStatus> allowedTransitions() {
        return Collections.unmodifiableSet(TRANSITIONS.get(this));
    }

    public boolean canTransitionTo(OrderStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }
}
