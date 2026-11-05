package io.github.hardyalsinghdeora.orders.application;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** What the service remembers about a completed POST /orders for a given Idempotency-Key. */
public record IdempotencyRecord(String key, String requestHash, UUID orderId, Instant createdAt, Instant expiresAt) {

    public static IdempotencyRecord create(String key, String requestHash, UUID orderId, Instant now, Duration ttl) {
        return new IdempotencyRecord(key, requestHash, orderId, now, now.plus(ttl));
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
