package io.github.hardyalsinghdeora.orders.domain;

import static io.github.hardyalsinghdeora.orders.Fixtures.T0;
import static io.github.hardyalsinghdeora.orders.Fixtures.line;
import static io.github.hardyalsinghdeora.orders.Fixtures.orderIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class OrderStateMachineTest {

    private static final Instant LATER = T0.plusSeconds(60);

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"DRAFT, CONFIRMED", "DRAFT, CANCELLED", "CONFIRMED, FULFILLED", "CONFIRMED, CANCELLED"})
    void validTransitionsChangeStatusAndTouchUpdatedAt(OrderStatus from, OrderStatus to) {
        Order order = orderIn(from);

        apply(order, to);

        assertThat(order.status()).isEqualTo(to);
        assertThat(order.updatedAt()).isEqualTo(LATER);
        assertThat(order.createdAt()).isEqualTo(T0);
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @MethodSource("invalidTransitions")
    void invalidTransitionsAreRejectedWithCurrentStatusAndAllowedTargets(OrderStatus from, OrderStatus to) {
        Order order = orderIn(from);

        assertThatThrownBy(() -> apply(order, to))
                .isInstanceOf(InvalidStateTransitionException.class)
                .satisfies(e -> {
                    InvalidStateTransitionException transition = (InvalidStateTransitionException) e;
                    assertThat(transition.orderId()).isEqualTo(order.id());
                    assertThat(transition.currentStatus()).isEqualTo(from);
                    assertThat(transition.requestedStatus()).isEqualTo(to);
                    assertThat(transition.allowedTransitions()).isEqualTo(from.allowedTransitions());
                })
                .hasMessageContaining("is " + from)
                .hasMessageContaining("cannot move to " + to);
        assertThat(order.status()).as("status is unchanged after a rejected transition").isEqualTo(from);
        assertThat(order.updatedAt()).isEqualTo(T0);
    }

    static Stream<Arguments> invalidTransitions() {
        Set<OrderStatus> reachableByMethod = EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.FULFILLED, OrderStatus.CANCELLED);
        return Stream.of(OrderStatus.values())
                .flatMap(from -> reachableByMethod.stream()
                        .filter(to -> !from.canTransitionTo(to))
                        .map(to -> Arguments.of(from, to)));
    }

    @Test
    void transitionTableIsExactlyTheDocumentedStateMachine() {
        assertThat(OrderStatus.DRAFT.allowedTransitions()).containsExactly(OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
        assertThat(OrderStatus.CONFIRMED.allowedTransitions()).containsExactly(OrderStatus.FULFILLED, OrderStatus.CANCELLED);
        assertThat(OrderStatus.FULFILLED.allowedTransitions()).isEmpty();
        assertThat(OrderStatus.CANCELLED.allowedTransitions()).isEmpty();
        assertThat(OrderStatus.DRAFT.canTransitionTo(OrderStatus.DRAFT)).as("no self transitions").isFalse();
    }

    @Test
    void newOrdersStartAsDraftAtVersionZero() {
        Order order = Order.create(java.util.UUID.randomUUID(), "cust-9", List.of(line("SKU-1", 1, "1.00")), T0);

        assertThat(order.status()).isEqualTo(OrderStatus.DRAFT);
        assertThat(order.version()).isZero();
        assertThat(order.currency()).isEqualTo("INR");
        assertThat(order.createdAt()).isEqualTo(T0);
        assertThat(order.updatedAt()).isEqualTo(T0);
    }

    @Test
    void linesCanBeReplacedWhileDraft() {
        Order order = orderIn(OrderStatus.DRAFT);

        order.replaceLines(List.of(line("SKU-2", 1, "5.00"), line("SKU-3", 3, "1.00")), LATER);

        assertThat(order.lines()).extracting(OrderLine::sku).containsExactly("SKU-2", "SKU-3");
        assertThat(order.total()).isEqualByComparingTo("8.00");
        assertThat(order.updatedAt()).isEqualTo(LATER);
    }

    @ParameterizedTest
    @CsvSource({"CONFIRMED", "FULFILLED", "CANCELLED"})
    void linesCannotBeReplacedOnceTheOrderLeftDraft(OrderStatus status) {
        Order order = orderIn(status);
        List<OrderLine> newLines = List.of(line("SKU-2", 1, "5.00"));

        assertThatThrownBy(() -> order.replaceLines(newLines, LATER))
                .isInstanceOf(OrderNotEditableException.class)
                .hasMessageContaining("current status is " + status);
        assertThat(order.lines()).extracting(OrderLine::sku).containsExactly("SKU-1");
    }

    @Test
    void anOrderNeedsAtLeastOneLine() {
        Order order = orderIn(OrderStatus.DRAFT);

        assertThatThrownBy(() -> order.replaceLines(List.of(), LATER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Order.create(java.util.UUID.randomUUID(), "cust-1", List.of(), T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void apply(Order order, OrderStatus target) {
        switch (target) {
            case CONFIRMED -> order.confirm(LATER);
            case FULFILLED -> order.fulfil(LATER);
            case CANCELLED -> order.cancel(LATER);
            case DRAFT -> throw new IllegalArgumentException("no transition leads back to DRAFT");
        }
    }
}
