package io.github.hardyalsinghdeora.orders.infrastructure.persistence;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import io.github.hardyalsinghdeora.orders.application.IdempotencyKeyInUseException;
import io.github.hardyalsinghdeora.orders.application.IdempotencyRecord;
import io.github.hardyalsinghdeora.orders.application.IdempotencyStore;

@Component
public class JpaIdempotencyStore implements IdempotencyStore {

    private final IdempotencyKeyJpaRepository repository;

    public JpaIdempotencyStore(IdempotencyKeyJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<IdempotencyRecord> find(String key) {
        return repository.findById(key).map(IdempotencyKeyEntity::toRecord);
    }

    /** The primary key is the arbiter between concurrent first requests with the same key. */
    @Override
    public void save(IdempotencyRecord record) {
        try {
            repository.saveAndFlush(IdempotencyKeyEntity.fromRecord(record));
        } catch (DataIntegrityViolationException e) {
            throw new IdempotencyKeyInUseException(record.key(), e);
        }
    }

    /**
     * Flushed at once: Hibernate runs INSERTs before DELETEs within a flush, so a key
     * re-inserted later in the same transaction must see its old row gone first.
     */
    @Override
    public void delete(String key) {
        repository.deleteById(key);
        repository.flush();
    }
}
