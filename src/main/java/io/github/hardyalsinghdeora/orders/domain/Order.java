package io.github.hardyalsinghdeora.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The order aggregate. All state changes go through its methods so the invariants
 * (state machine, lines only editable while DRAFT, derived total) hold everywhere.
 * The version is assigned by persistence and used for optimistic locking.
 */
public final class Order {

    public static final String CURRENCY = "INR";

    private final UUID id;
    private final String customerId;
    private final long version;
    private final Instant createdAt;
    private List<OrderLine> lines;
    private OrderStatus status;
    private Instant updatedAt;

    private Order(UUID id, String customerId, List<OrderLine> lines, OrderStatus status,
                  long version, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.customerId = requireText(customerId);
        this.lines = requireLines(lines);
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Order create(UUID id, String customerId, List<OrderLine> lines, Instant now) {
        return new Order(id, customerId, lines, OrderStatus.DRAFT, 0L, now, now);
    }

    /** Rebuilds an order from stored state; used by persistence only. */
    public static Order rehydrate(UUID id, String customerId, List<OrderLine> lines, OrderStatus status,
                                  long version, Instant createdAt, Instant updatedAt) {
        return new Order(id, customerId, lines, status, version, createdAt, updatedAt);
    }

    public void confirm(Instant now) {
        transitionTo(OrderStatus.CONFIRMED, now);
    }

    public void fulfil(Instant now) {
        transitionTo(OrderStatus.FULFILLED, now);
    }

    public void cancel(Instant now) {
        transitionTo(OrderStatus.CANCELLED, now);
    }

    public void replaceLines(List<OrderLine> newLines, Instant now) {
        if (status != OrderStatus.DRAFT) {
            throw new OrderNotEditableException(id, status);
        }
        this.lines = requireLines(newLines);
        this.updatedAt = now;
    }

    private void transitionTo(OrderStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(id, status, target);
        }
        this.status = target;
        this.updatedAt = now;
    }

    /** Derived from the lines on every call: there is no stored total to drift. */
    public BigDecimal total() {
        return Money.sum(lines.stream().map(OrderLine::lineTotal).toList());
    }

    public UUID id() {
        return id;
    }

    public String customerId() {
        return customerId;
    }

    public String currency() {
        return CURRENCY;
    }

    public List<OrderLine> lines() {
        return lines;
    }

    public OrderStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private static List<OrderLine> requireLines(List<OrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("an order needs at least one line");
        }
        return List.copyOf(lines);
    }

    private static String requireText(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be blank");
        }
        return customerId;
    }
}
