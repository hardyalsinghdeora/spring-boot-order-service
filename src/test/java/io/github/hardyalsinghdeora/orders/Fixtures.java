package io.github.hardyalsinghdeora.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.hardyalsinghdeora.orders.domain.Order;
import io.github.hardyalsinghdeora.orders.domain.OrderLine;
import io.github.hardyalsinghdeora.orders.domain.OrderStatus;

/** Builders shared by the unit tests. */
public final class Fixtures {

    public static final Instant T0 = Instant.parse("2026-01-15T10:00:00Z");

    private Fixtures() {
    }

    public static OrderLine line(String sku, int quantity, String unitPrice) {
        return new OrderLine(sku, "Description of " + sku, quantity, new BigDecimal(unitPrice));
    }

    public static Order draftOrder() {
        return Order.create(UUID.randomUUID(), "cust-1", List.of(line("SKU-1", 2, "10.00")), T0);
    }

    public static Order orderIn(OrderStatus status) {
        return Order.rehydrate(UUID.randomUUID(), "cust-1", List.of(line("SKU-1", 2, "10.00")), status, 3L, T0, T0);
    }
}
