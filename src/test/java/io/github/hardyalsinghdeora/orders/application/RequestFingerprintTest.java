package io.github.hardyalsinghdeora.orders.application;

import static io.github.hardyalsinghdeora.orders.Fixtures.line;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.hardyalsinghdeora.orders.domain.OrderLine;

class RequestFingerprintTest {

    private static final CreateOrderCommand COMMAND = new CreateOrderCommand("cust-1",
            List.of(line("SKU-1", 3, "19.99"), line("SKU-2", 1, "10.50")));

    @Test
    void isStableAcrossProcessesAndVersions() {
        // Pinned on purpose: changing the canonical form would silently break replay for stored keys.
        assertThat(RequestFingerprint.of(COMMAND))
                .isEqualTo("e4fb41137838c5e11a9d3e858a0b2c445df4b6f202490a4c96ce9c4697c6bf49");
    }

    @Test
    void equalCommandsHashEqually() {
        CreateOrderCommand same = new CreateOrderCommand("cust-1", List.of(line("SKU-1", 3, "19.99"), line("SKU-2", 1, "10.50")));

        assertThat(RequestFingerprint.of(same)).isEqualTo(RequestFingerprint.of(COMMAND));
    }

    @Test
    void numberFormattingDoesNotChangeTheHash() {
        CreateOrderCommand differentScale = new CreateOrderCommand("cust-1", List.of(line("SKU-1", 3, "19.990"), line("SKU-2", 1, "10.5")));

        assertThat(RequestFingerprint.of(differentScale)).isEqualTo(RequestFingerprint.of(COMMAND));
    }

    @Test
    void anyMeaningfulDifferenceChangesTheHash() {
        String original = RequestFingerprint.of(COMMAND);

        assertThat(RequestFingerprint.of(new CreateOrderCommand("cust-2", COMMAND.lines()))).isNotEqualTo(original);
        assertThat(RequestFingerprint.of(new CreateOrderCommand("cust-1", List.of(line("SKU-1", 4, "19.99"), line("SKU-2", 1, "10.50")))))
                .isNotEqualTo(original);
        assertThat(RequestFingerprint.of(new CreateOrderCommand("cust-1", List.of(line("SKU-1", 3, "19.98"), line("SKU-2", 1, "10.50")))))
                .isNotEqualTo(original);
        assertThat(RequestFingerprint.of(new CreateOrderCommand("cust-1", List.of(line("SKU-2", 1, "10.50"), line("SKU-1", 3, "19.99")))))
                .as("line order is part of the payload")
                .isNotEqualTo(original);
    }

    @Test
    void valuesCannotBleedIntoEachOther() {
        CreateOrderCommand a = new CreateOrderCommand("cust-1", List.of(new OrderLine("ab", "c", 1, BigDecimal.ONE)));
        CreateOrderCommand b = new CreateOrderCommand("cust-1", List.of(new OrderLine("a", "bc", 1, BigDecimal.ONE)));

        assertThat(RequestFingerprint.of(a)).isNotEqualTo(RequestFingerprint.of(b));
    }
}
