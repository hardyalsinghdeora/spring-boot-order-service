package io.github.hardyalsinghdeora.orders.api.dto;

import java.math.BigDecimal;

import io.github.hardyalsinghdeora.orders.domain.OrderLine;

public record OrderLineResponse(String sku, String description, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {

    static OrderLineResponse from(OrderLine line) {
        return new OrderLineResponse(line.sku(), line.description(), line.quantity(), line.unitPrice(), line.lineTotal());
    }
}
