package io.github.hardyalsinghdeora.orders.application;

import java.util.Optional;

/** Persistence port for idempotency keys; implemented in the infrastructure layer. */
public interface IdempotencyStore {

    Optional<IdempotencyRecord> find(String key);

    /** @throws IdempotencyKeyInUseException if the key was stored concurrently by another request */
    void save(IdempotencyRecord record);

    void delete(String key);
}
