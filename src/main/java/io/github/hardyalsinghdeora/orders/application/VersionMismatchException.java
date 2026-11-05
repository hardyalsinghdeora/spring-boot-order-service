package io.github.hardyalsinghdeora.orders.application;

import java.util.UUID;

/** The client's If-Match version is not the current one: someone else changed the order first. */
public class VersionMismatchException extends RuntimeException {

    private final long expectedVersion;
    private final long currentVersion;

    public VersionMismatchException(UUID orderId, long expectedVersion, long currentVersion) {
        super("Order %s is at version %d, not %d".formatted(orderId, currentVersion, expectedVersion));
        this.expectedVersion = expectedVersion;
        this.currentVersion = currentVersion;
    }

    public long expectedVersion() {
        return expectedVersion;
    }

    public long currentVersion() {
        return currentVersion;
    }
}
