package io.github.hardyalsinghdeora.orders.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import io.github.hardyalsinghdeora.orders.application.OrderRepository;
import io.github.hardyalsinghdeora.orders.application.OrderSearch;
import io.github.hardyalsinghdeora.orders.application.PageSlice;
import io.github.hardyalsinghdeora.orders.domain.Order;
import jakarta.persistence.criteria.Predicate;

@Component
public class JpaOrderRepository implements OrderRepository {

    /** Newest first; the id tie-breaker keeps pages stable when timestamps collide. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"));

    private final OrderJpaRepository repository;

    public JpaOrderRepository(OrderJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return repository.findById(id).map(OrderEntity::toDomain);
    }

    @Override
    public Order insert(Order order) {
        return repository.saveAndFlush(OrderEntity.fromDomain(order, null)).toDomain();
    }

    /**
     * Saves through a merge of a detached copy carrying the domain object's version, so
     * Hibernate's version check compares against the version the caller actually read.
     * Flushing here surfaces an optimistic-locking failure in this call, not at commit.
     */
    @Override
    public Order update(Order order) {
        return repository.saveAndFlush(OrderEntity.fromDomain(order, order.version())).toDomain();
    }

    @Override
    public PageSlice<Order> search(OrderSearch search) {
        Page<OrderEntity> page = repository.findAll(matching(search), PageRequest.of(search.page(), search.size(), NEWEST_FIRST));
        return new PageSlice<>(page.map(OrderEntity::toDomain).getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    private static Specification<OrderEntity> matching(OrderSearch search) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search.customerId() != null) {
                predicates.add(builder.equal(root.get("customerId"), search.customerId()));
            }
            if (search.status() != null) {
                predicates.add(builder.equal(root.get("status"), search.status()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
