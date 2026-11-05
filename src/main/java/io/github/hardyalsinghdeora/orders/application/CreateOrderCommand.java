package io.github.hardyalsinghdeora.orders.application;

import java.util.List;

import io.github.hardyalsinghdeora.orders.domain.OrderLine;

public record CreateOrderCommand(String customerId, List<OrderLine> lines) {

    public CreateOrderCommand {
        lines = List.copyOf(lines);
    }
}
