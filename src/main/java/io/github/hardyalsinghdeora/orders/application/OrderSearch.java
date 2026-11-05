package io.github.hardyalsinghdeora.orders.application;

import io.github.hardyalsinghdeora.orders.domain.OrderStatus;

/** Search criteria for listing orders; a null customerId or status means "any". */
public record OrderSearch(String customerId, OrderStatus status, int page, int size) {
}
