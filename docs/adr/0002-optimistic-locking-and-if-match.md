# 0002: Optimistic locking, exposed as ETag and If-Match

## Context
Two people can edit the same draft order. Without a check, the second save
silently overwrites the first ("lost update").

## Decision
Every order has a JPA `@Version`. Responses carry it as a strong `ETag`.
`PUT /orders/{id}/lines` requires `If-Match` with that version:
- missing `If-Match` → `428 Precondition Required`;
- a version that is no longer current → `412 Precondition Failed`, nothing written;
- a write that passes the check but loses a race at commit → `409 Conflict`
  (Spring's `ObjectOptimisticLockingFailureException`, translated in one place).

## Consequences
- No database locks are held between a read and a write; contention costs a retry, not a wait.
- Clients must re-read after a 412 or 409. That is the right behaviour for a human editing an order.
- State transitions (`confirm`, `fulfil`, `cancel`) do not require `If-Match`; the version
  check still protects them from concurrent writers.
