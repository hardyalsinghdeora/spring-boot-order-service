-- Order aggregate: orders own their lines; the total is derived in code, stored for listing.
CREATE TABLE orders (
    id          UUID                     NOT NULL,
    customer_id VARCHAR(64)              NOT NULL,
    currency    VARCHAR(3)               NOT NULL,
    status      VARCHAR(16)              NOT NULL,
    total       NUMERIC(14, 2)           NOT NULL,
    version     BIGINT                   NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT ck_orders_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'FULFILLED', 'CANCELLED')),
    CONSTRAINT ck_orders_total CHECK (total >= 0)
);

CREATE INDEX ix_orders_customer_created ON orders (customer_id, created_at DESC);
CREATE INDEX ix_orders_status_created ON orders (status, created_at DESC);

-- Lines are value objects: identified by their position within the order, no id of their own.
CREATE TABLE order_lines (
    order_id    UUID           NOT NULL,
    line_no     INTEGER        NOT NULL,
    sku         VARCHAR(64)    NOT NULL,
    description VARCHAR(255)   NOT NULL,
    quantity    INTEGER        NOT NULL,
    unit_price  NUMERIC(14, 2) NOT NULL,
    CONSTRAINT pk_order_lines PRIMARY KEY (order_id, line_no),
    CONSTRAINT fk_order_lines_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT ck_order_lines_quantity CHECK (quantity >= 1),
    CONSTRAINT ck_order_lines_unit_price CHECK (unit_price >= 0)
);

-- One row per completed POST /orders. Rows past expires_at are ignored; purging them is a
-- separate housekeeping job (out of scope here), which is what the expires_at index is for.
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(128)             NOT NULL,
    request_hash    VARCHAR(64)              NOT NULL,
    order_id        UUID                     NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (idempotency_key),
    CONSTRAINT fk_idempotency_keys_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE INDEX ix_idempotency_keys_expires_at ON idempotency_keys (expires_at);
