package io.github.hardyalsinghdeora.orders.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import io.github.hardyalsinghdeora.orders.domain.Order;
import io.github.hardyalsinghdeora.orders.domain.OrderLine;

/**
 * SHA-256 over a canonical rendering of a create command. Hashing the command rather
 * than the raw JSON means two requests that mean the same thing hash the same, whatever
 * their whitespace, key order or number formatting ("10.5" and "10.50").
 */
public final class RequestFingerprint {

    private RequestFingerprint() {
    }

    public static String of(CreateOrderCommand command) {
        StringBuilder canonical = new StringBuilder();
        field(canonical, "customerId", command.customerId());
        field(canonical, "currency", Order.CURRENCY);
        for (OrderLine line : command.lines()) {
            field(canonical, "sku", line.sku());
            field(canonical, "description", line.description());
            field(canonical, "quantity", Integer.toString(line.quantity()));
            field(canonical, "unitPrice", line.unitPrice().toPlainString());
        }
        return HexFormat.of().formatHex(sha256(canonical.toString()));
    }

    /** Length-prefixed values keep the rendering unambiguous whatever characters a value contains. */
    private static void field(StringBuilder canonical, String name, String value) {
        canonical.append(name).append('=').append(value.length()).append(':').append(value).append('\n');
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory in every Java runtime", e);
        }
    }
}
