package io.github.hardyalsinghdeora.orders.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.hardyalsinghdeora.orders.domain.Order;
import io.github.hardyalsinghdeora.orders.domain.OrderLine;
import io.github.hardyalsinghdeora.orders.domain.OrderStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** JPA mapping of the order aggregate; converts to and from the plain domain {@link Order}. */
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    /** Null until the row exists: that is how Spring Data tells an insert from an update. */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "order_lines", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "line_no")
    private List<OrderLineEmbeddable> lines = new ArrayList<>();

    protected OrderEntity() {
        // for JPA
    }

    static OrderEntity fromDomain(Order order, Long version) {
        OrderEntity entity = new OrderEntity();
        entity.id = order.id();
        entity.customerId = order.customerId();
        entity.currency = order.currency();
        entity.status = order.status();
        entity.total = order.total();
        entity.version = version;
        entity.createdAt = order.createdAt();
        entity.updatedAt = order.updatedAt();
        entity.lines = new ArrayList<>(order.lines().stream().map(OrderLineEmbeddable::fromDomain).toList());
        return entity;
    }

    Order toDomain() {
        List<OrderLine> domainLines = lines.stream().map(OrderLineEmbeddable::toDomain).toList();
        return Order.rehydrate(id, customerId, domainLines, status, version, createdAt, updatedAt);
    }
}
