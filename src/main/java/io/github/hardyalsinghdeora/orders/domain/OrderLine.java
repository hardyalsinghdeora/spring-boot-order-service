package io.github.hardyalsinghdeora.orders.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** A value object: two lines with the same content are interchangeable. */
public record OrderLine(String sku, String description, int quantity, BigDecimal unitPrice) {

    public OrderLine {
        requireText(sku, "sku");
        requireText(description, "description");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative");
        }
        unitPrice = Money.round(unitPrice);
    }

    public BigDecimal lineTotal() {
        return Money.multiply(unitPrice, quantity);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
