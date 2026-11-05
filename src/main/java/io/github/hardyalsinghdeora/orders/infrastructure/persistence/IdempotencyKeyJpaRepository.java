package io.github.hardyalsinghdeora.orders.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyEntity, String> {
}
