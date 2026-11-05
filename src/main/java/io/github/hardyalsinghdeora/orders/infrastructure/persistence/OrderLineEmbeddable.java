package io.github.hardyalsinghdeora.orders.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.Objects;

import io.github.hardyalsinghdeora.orders.domain.OrderLine;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class OrderLineEmbeddable {

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    protected OrderLineEmbeddable() {
        // for JPA
    }

    static OrderLineEmbeddable fromDomain(OrderLine line) {
        OrderLineEmbeddable embeddable = new OrderLineEmbeddable();
        embeddable.sku = line.sku();
        embeddable.description = line.description();
        embeddable.quantity = line.quantity();
        embeddable.unitPrice = line.unitPrice();
        return embeddable;
    }

    OrderLine toDomain() {
        return new OrderLine(sku, description, quantity, unitPrice);
    }

    /** Value equality lets Hibernate see that an unchanged line needs no UPDATE. */
    @Override
    public boolean equals(Object other) {
        return other instanceof OrderLineEmbeddable that
                && quantity == that.quantity
                && sku.equals(that.sku)
                && description.equals(that.description)
                && unitPrice.compareTo(that.unitPrice) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sku, description, quantity, unitPrice.stripTrailingZeros());
    }
}
