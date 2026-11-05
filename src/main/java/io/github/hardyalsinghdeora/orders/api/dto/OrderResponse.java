package io.github.hardyalsinghdeora.orders.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.hardyalsinghdeora.orders.domain.Order;
import io.github.hardyalsinghdeora.orders.domain.OrderStatus;

public record OrderResponse(
        UUID id,
        String customerId,
        String currency,
        OrderStatus status,
        List<OrderLineResponse> lines,
        BigDecimal total,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.customerId(),
                order.currency(),
                order.status(),
                order.lines().stream().map(OrderLineResponse::from).toList(),
                order.total(),
                order.version(),
                order.createdAt(),
                order.updatedAt());
    }
}
