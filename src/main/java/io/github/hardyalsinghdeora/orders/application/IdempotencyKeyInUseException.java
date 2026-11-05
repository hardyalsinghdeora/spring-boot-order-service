package io.github.hardyalsinghdeora.orders.application;

/** Another request with the same key is being processed concurrently; the client can retry. */
public class IdempotencyKeyInUseException extends RuntimeException {

    public IdempotencyKeyInUseException(String key, Throwable cause) {
        super("A request with Idempotency-Key '%s' is already in progress".formatted(key), cause);
    }
}
