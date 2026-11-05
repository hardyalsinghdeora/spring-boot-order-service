package io.github.hardyalsinghdeora.orders.domain;

import static io.github.hardyalsinghdeora.orders.Fixtures.T0;
import static io.github.hardyalsinghdeora.orders.Fixtures.line;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @ParameterizedTest(name = "{0} rounds to {1}")
    @CsvSource({
            "10.5, 10.50",
            "10, 10.00",
            "0.005, 0.00",
            "0.015, 0.02",
            "0.025, 0.02",
            "1.005, 1.00",
            "2.675, 2.68",
            "19.999, 20.00"
    })
    void roundsHalfEvenToScaleTwo(String input, String expected) {
        BigDecimal rounded = Money.round(new BigDecimal(input));

        assertThat(rounded).isEqualTo(new BigDecimal(expected));
        assertThat(rounded.scale()).isEqualTo(2);
    }

    @Test
    void multipliesExactlyAtScaleTwo() {
        assertThat(Money.multiply(new BigDecimal("19.99"), 3)).isEqualTo(new BigDecimal("59.97"));
        assertThat(Money.multiply(new BigDecimal("0.10"), 3)).isEqualTo(new BigDecimal("0.30"));
        assertThat(Money.multiply(new BigDecimal("1.00"), 1_000_000)).isEqualTo(new BigDecimal("1000000.00"));
    }

    @Test
    void sumsToScaleTwoAndAnEmptySumIsZero() {
        assertThat(Money.sum(List.of(new BigDecimal("1.1"), new BigDecimal("2.25")))).isEqualTo(new BigDecimal("3.35"));
        assertThat(Money.sum(List.of())).isEqualTo(Money.ZERO).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void orderLinesNormaliseTheirUnitPrice() {
        OrderLine fromScaleOne = line("SKU-1", 2, "10.5");
        OrderLine fromScaleThree = line("SKU-1", 2, "10.500");

        assertThat(fromScaleOne.unitPrice()).isEqualTo(new BigDecimal("10.50"));
        assertThat(fromScaleOne).as("lines are value objects: equal content, equal lines").isEqualTo(fromScaleThree);
        assertThat(fromScaleOne.lineTotal()).isEqualTo(new BigDecimal("21.00"));
    }

    @Test
    void orderLinesRejectInvalidQuantityAndPrice() {
        assertThatThrownBy(() -> line("SKU-1", 0, "1.00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> line("SKU-1", 1, "-0.01")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderLine(" ", "d", 1, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orderTotalIsTheSumOfLineTotals() {
        Order order = Order.create(UUID.randomUUID(), "cust-1",
                List.of(line("SKU-1", 3, "19.99"), line("SKU-2", 1, "10.5"), line("SKU-3", 2, "0.00")), T0);

        assertThat(order.total()).isEqualTo(new BigDecimal("70.47"));
    }
}
