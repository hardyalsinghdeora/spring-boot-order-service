package io.github.hardyalsinghdeora.orders.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import io.github.hardyalsinghdeora.orders.application.IdempotencyRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyEntity implements Persistable<String> {

    @Id
    @Column(name = "idempotency_key", length = 128)
    private String key;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** The id is assigned by the application, so Spring Data cannot infer "new" from it. */
    @Transient
    private boolean isNew = true;

    protected IdempotencyKeyEntity() {
        // for JPA
    }

    static IdempotencyKeyEntity fromRecord(IdempotencyRecord record) {
        IdempotencyKeyEntity entity = new IdempotencyKeyEntity();
        entity.key = record.key();
        entity.requestHash = record.requestHash();
        entity.orderId = record.orderId();
        entity.createdAt = record.createdAt();
        entity.expiresAt = record.expiresAt();
        return entity;
    }

    IdempotencyRecord toRecord() {
        return new IdempotencyRecord(key, requestHash, orderId, createdAt, expiresAt);
    }

    @PostPersist
    @PostLoad
    void markPersisted() {
        isNew = false;
    }

    @Override
    public String getId() {
        return key;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }
}
