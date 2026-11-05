package io.github.hardyalsinghdeora.orders.application;

import java.util.Optional;
import java.util.UUID;

import io.github.hardyalsinghdeora.orders.domain.Order;

/** Persistence port for the order aggregate; implemented in the infrastructure layer. */
public interface OrderRepository {

    Optional<Order> findById(UUID id);

    Order insert(Order order);

    /**
     * Stores changes to an existing order. The order's version must match the stored
     * one; a lost race surfaces as Spring's OptimisticLockingFailureException.
     */
    Order update(Order order);

    PageSlice<Order> search(OrderSearch search);
}
