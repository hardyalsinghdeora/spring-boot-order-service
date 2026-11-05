package io.github.hardyalsinghdeora.orders.application;

public class IdempotencyKeyPayloadMismatchException extends RuntimeException {

    public IdempotencyKeyPayloadMismatchException(String key) {
        super("Idempotency-Key '%s' was already used with a different payload".formatted(key));
    }
}
