# 0001: Idempotency keys on order creation

## Context
`POST /orders` is not naturally idempotent. A client that times out and retries
can create a second order, and in order-to-cash a duplicate order becomes a
duplicate invoice.

## Decision
`POST /orders` requires an `Idempotency-Key` header. The service stores the key,
a SHA-256 fingerprint of the normalised request and the created order id, in the
same transaction as the order. A repeat with the same key and the same payload
returns the original order with `200` and `Idempotent-Replayed: true`. The same
key with a different payload is rejected with `422`. Keys expire after 24 hours;
an expired key is replaced on its next use.

## Consequences
- Retries are safe without the client having to read before it writes.
- The fingerprint normalises money to scale 2, so `10.5` and `10.50` are the same request.
- Expired rows stay in the table until a housekeeping job purges them; that job is out
  of scope here, and `ix_idempotency_keys_expires_at` exists for it.
- Two truly concurrent first requests with the same key race on the primary key; the
  loser fails with a conflict rather than creating a duplicate.
