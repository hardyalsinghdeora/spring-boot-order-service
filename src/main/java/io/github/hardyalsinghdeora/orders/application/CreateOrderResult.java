package io.github.hardyalsinghdeora.orders.application;

import io.github.hardyalsinghdeora.orders.domain.Order;

/** {@code replayed} is true when the order was created by an earlier request with the same key. */
public record CreateOrderResult(Order order, boolean replayed) {
}
