package io.github.hardyalsinghdeora.orders.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

/**
 * Monetary arithmetic rules shared by the domain: amounts are {@link BigDecimal}
 * at scale 2 and rounded HALF_EVEN (banker's rounding), never floating point.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private Money() {
    }

    public static BigDecimal round(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        return amount.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal multiply(BigDecimal unitPrice, int quantity) {
        return round(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    public static BigDecimal sum(Collection<BigDecimal> amounts) {
        return round(amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
