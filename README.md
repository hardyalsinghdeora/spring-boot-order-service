# spring-boot-order-service

A small order-to-cash REST service in Java 21 and Spring Boot 3.5. It covers one slice,
creating orders and moving them through their lifecycle, and spends its effort on the
parts that decide whether such a service is safe to call: idempotent creation, lost-update
protection, an explicit state machine and errors a client can act on.

It is a reference implementation built as a portfolio sample, not a production system.

## What it demonstrates

- **Idempotent `POST`.** An `Idempotency-Key` header makes retries safe. The same key with the
  same payload replays the original order; the same key with a different payload is a `422`.
- **Optimistic locking over HTTP.** The JPA `@Version` is exposed as an `ETag`. Edits need
  `If-Match`; a stale version is a `412`, a lost race at commit is a `409`.
- **An explicit state machine.** `DRAFT → CONFIRMED → FULFILLED`, and `DRAFT | CONFIRMED → CANCELLED`.
  Anything else is a `409` that names the current status and the allowed moves.
- **Server-side money.** Totals are derived from the lines with `BigDecimal` at scale 2,
  `HALF_EVEN`. The client never sends a total.
- **RFC 9457 problem details** for every error, with no stack traces in responses.
- **Flyway** owns the schema; Hibernate only validates the mapping.
- **Request-id tracing.** `X-Request-Id` is echoed or generated, returned on every response and
  written on every log line.
- **Tests at three levels:** domain unit tests, a `@WebMvcTest` slice, and `@SpringBootTest`
  against the real migration.

## Architecture

```
             HTTP
              │
  ┌───────────▼────────────┐   RequestIdFilter, OrderController, DTOs + Bean Validation,
  │ api                    │   ApiExceptionHandler (RFC 9457), ETag / If-Match handling
  └───────────┬────────────┘
  ┌───────────▼────────────┐   OrderService: one transaction per use case,
  │ application            │   idempotency check, version check
  └─────┬─────────────┬────┘   ports: OrderRepository, IdempotencyStore
  ┌─────▼──────┐ ┌────▼─────────────────┐
  │ domain     │ │ infrastructure        │   JPA entities kept separate from the domain,
  │ Order,     │ │ JpaOrderRepository,   │   Spring Data repositories, Flyway SQL
  │ OrderLine, │ │ JpaIdempotencyStore   │
  │ Money,     │ └───────────────────────┘
  │ OrderStatus│   no Spring or JPA imports
  └────────────┘
```

The domain package has no framework annotations. JPA entities live in `infrastructure` and map
to and from the domain, so persistence concerns do not leak into the business rules.

## Quick start

Needs only JDK 21; the Maven wrapper downloads Maven.

```bash
./mvnw spring-boot:run
```

The service starts on port 8080 with an in-memory H2 database. Then open
<http://localhost:8080/swagger-ui.html> for the OpenAPI UI, or
<http://localhost:8080/actuator/health>.

Run the tests:

```bash
./mvnw verify
```

Run against PostgreSQL 16 with Docker:

```bash
cp .env.example .env    # then set POSTGRES_PASSWORD
docker compose up --build
```

## API

| Method and path | Purpose | Notable responses |
|---|---|---|
| `POST /orders` | Create a draft order. Needs `Idempotency-Key`. | `201` + `Location` + `ETag`; `200` + `Idempotent-Replayed: true` on replay; `400` without a key; `422` key reused with a different payload |
| `GET /orders/{id}` | Read one order. | `200` + `ETag`; `404` |
| `GET /orders?customerId=&status=&page=&size=` | List, newest first. | `200` with `content`, `page`, `size`, `totalElements` |
| `PUT /orders/{id}/lines` | Replace the lines of a draft. Needs `If-Match`. | `200` + new `ETag`; `412` stale version; `409` lost race or not a draft; `428` no `If-Match` |
| `POST /orders/{id}/confirm` | `DRAFT → CONFIRMED` | `409` from any other status |
| `POST /orders/{id}/fulfil` | `CONFIRMED → FULFILLED` | `409` from any other status |
| `POST /orders/{id}/cancel` | `DRAFT` or `CONFIRMED → CANCELLED` | `409` from a terminal status |

Every error type is described in [docs/problem-types.md](docs/problem-types.md), and
[docs/requests.http](docs/requests.http) has the calls below ready to run from an IDE.

### Examples (real output)

Create an order. The total is computed by the service:

```bash
curl -i -X POST localhost:8080/orders \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-1' \
  -d '{"customerId":"cust-42","lines":[
        {"sku":"BOOK-1","description":"Exercise book","quantity":2,"unitPrice":"10.00"},
        {"sku":"PEN-1","description":"Pen","quantity":3,"unitPrice":"4.50"}]}'
```
```
HTTP/1.1 201
X-Request-Id: 0de5b8cd-6994-4915-a2a2-aab95552b443
Location: http://localhost:8080/orders/9088b20b-9702-4959-83f8-f50a1eeb96ee
ETag: "0"
{"id":"9088b20b-…","customerId":"cust-42","currency":"INR","status":"DRAFT",
 "lines":[{"sku":"BOOK-1",…,"lineTotal":20.00},{"sku":"PEN-1",…,"lineTotal":13.50}],
 "total":33.50,"version":0,…}
```

Send exactly the same request again:

```
HTTP/1.1 200
Idempotent-Replayed: true
```

Same key, different quantity:

```json
{"type":"…/docs/problem-types.md#idempotency-key-payload-mismatch","title":"Unprocessable Entity",
 "status":422,"detail":"Idempotency-Key 'demo-1' was already used with a different payload",
 "instance":"/orders","requestId":"9bac1755-…"}
```

Replace the lines with `If-Match: "0"` (returns `200` and `ETag: "1"`), then try again with the
now-stale `"0"`:

```json
{"type":"…/docs/problem-types.md#version-mismatch","title":"Precondition Failed","status":412,
 "detail":"Order 9088b20b-… is at version 1, not 0","expectedVersion":0,"currentVersion":1,…}
```

Confirm, fulfil, then try to cancel:

```json
{"type":"…/docs/problem-types.md#invalid-state-transition","title":"Conflict","status":409,
 "detail":"Order 9088b20b-… is FULFILLED and cannot move to CANCELLED; allowed transitions: none (terminal status)",
 "currentStatus":"FULFILLED","requestedStatus":"CANCELLED","allowedTransitions":[],…}
```

## Design decisions

Short versions; the full records are in [docs/adr](docs/adr).

- **Idempotency keys are stored with the order, in one transaction**, with a fingerprint of the
  normalised request. A key can never point at an order that was rolled back, and a replay is a
  primary-key lookup. Trade-off: a table that grows until a purge job runs.
  ([ADR 0001](docs/adr/0001-idempotency-keys.md))
- **Optimistic locking, surfaced as `ETag`/`If-Match`.** No locks are held while a person edits.
  The version check runs twice: against `If-Match` before writing (`412`), and by Hibernate at
  flush (`409`). Trade-off: clients must handle a retry. ([ADR 0002](docs/adr/0002-optimistic-locking-and-if-match.md))
- **The state machine lives in the domain**, as a table of allowed transitions on `OrderStatus`,
  so the rule is tested without Spring and reported precisely in the `409`.
- **Domain and JPA entities are separate classes.** It costs a mapper, and buys a domain with no
  framework imports and no lazy-loading surprises in business code.
- **H2 for development and tests, PostgreSQL for deployment**, with Flyway as the single source
  of the schema. Trade-off: H2 is not PostgreSQL; Testcontainers would be the next step.
  ([ADR 0003](docs/adr/0003-h2-for-dev-postgres-for-prod.md))

## Deliberately out of scope

- Authentication and authorisation.
- Payments, invoicing and tax.
- Publishing events (an outbox; see
  [event-outbox-worker](https://github.com/hardyalsinghdeora/event-outbox-worker) for that pattern).
- A scheduled job that purges expired idempotency keys.
- Multiple currencies; the currency is fixed to `INR`.

## Project layout

```
src/main/java/io/github/hardyalsinghdeora/orders/
  domain/           Order, OrderLine, OrderStatus, Money, domain exceptions
  application/      OrderService, ports, idempotency and version checks
  api/              controller, DTOs, problem details, request-id filter
  infrastructure/   JPA entities and repositories
src/main/resources/db/migration/   Flyway SQL
docs/adr/                          decision records
```

## About

Built by Hardyal Singh Deora as a portfolio sample of how I approach a service where duplicate
or lost writes cost money. More of my work: <https://hardyalsinghdeora.pages.dev> ·
<https://github.com/hardyalsinghdeora>.

Licensed under the [MIT License](LICENSE).
