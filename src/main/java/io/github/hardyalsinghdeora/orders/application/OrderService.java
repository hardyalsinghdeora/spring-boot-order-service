package io.github.hardyalsinghdeora.orders.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.hardyalsinghdeora.orders.domain.Order;
import io.github.hardyalsinghdeora.orders.domain.OrderLine;
import io.github.hardyalsinghdeora.orders.domain.OrderNotFoundException;
import io.github.hardyalsinghdeora.orders.domain.OrderStatus;

/** Use cases of the order slice. Every public method is one transaction. */
@Service
@Transactional
public class OrderService {

    /** How long a key protects against duplicate creation. Expired rows are not purged here. */
    public static final Duration IDEMPOTENCY_KEY_TTL = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final IdempotencyStore idempotencyKeys;
    private final Clock clock;

    public OrderService(OrderRepository orders, IdempotencyStore idempotencyKeys, Clock clock) {
        this.orders = orders;
        this.idempotencyKeys = idempotencyKeys;
        this.clock = clock;
    }

    /**
     * Creates an order exactly once per idempotency key. A repeat of the same request
     * returns the original order; the same key with a different payload is rejected.
     */
    public CreateOrderResult create(String idempotencyKey, CreateOrderCommand command) {
        String fingerprint = RequestFingerprint.of(command);
        Instant now = clock.instant();

        Optional<IdempotencyRecord> previous = idempotencyKeys.find(idempotencyKey);
        if (previous.isPresent() && !previous.get().isExpired(now)) {
            IdempotencyRecord record = previous.get();
            if (!record.requestHash().equals(fingerprint)) {
                throw new IdempotencyKeyPayloadMismatchException(idempotencyKey);
            }
            Order original = get(record.orderId());
            log.info("order {} replayed for idempotency key", original.id());
            return new CreateOrderResult(original, true);
        }
        previous.ifPresent(expired -> idempotencyKeys.delete(expired.key()));

        Order order = orders.insert(Order.create(UUID.randomUUID(), command.customerId(), command.lines(), now));
        idempotencyKeys.save(IdempotencyRecord.create(idempotencyKey, fingerprint, order.id(), now, IDEMPOTENCY_KEY_TTL));
        log.info("order {} created for customer {} with {} line(s)", order.id(), order.customerId(), order.lines().size());
        return new CreateOrderResult(order, false);
    }

    @Transactional(readOnly = true)
    public Order get(UUID id) {
        return orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public PageSlice<Order> search(OrderSearch search) {
        return orders.search(search);
    }

    public Order confirm(UUID id) {
        return transition(id, Order::confirm);
    }

    public Order fulfil(UUID id) {
        return transition(id, Order::fulfil);
    }

    public Order cancel(UUID id) {
        return transition(id, Order::cancel);
    }

    /** Replaces the lines of a DRAFT order, provided the caller saw the current version. */
    public Order replaceLines(UUID id, long expectedVersion, List<OrderLine> lines) {
        Order order = get(id);
        if (order.version() != expectedVersion) {
            throw new VersionMismatchException(id, expectedVersion, order.version());
        }
        order.replaceLines(lines, clock.instant());
        Order saved = orders.update(order);
        log.info("order {} lines replaced, now version {}", id, saved.version());
        return saved;
    }

    private Order transition(UUID id, BiConsumer<Order, Instant> transition) {
        Order order = get(id);
        OrderStatus before = order.status();
        transition.accept(order, clock.instant());
        Order saved = orders.update(order);
        log.info("order {} moved from {} to {}", id, before, saved.status());
        return saved;
    }
}
