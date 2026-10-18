CREATE TABLE orders.order_ledger (
    id          bigint      PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    order_id    uuid        NOT NULL REFERENCES orders.orders (id),
    drop_id     uuid        NOT NULL,
    user_id     uuid        NOT NULL,
    entry_type  varchar(20) NOT NULL CHECK (entry_type IN ('CONFIRMED', 'CANCELED')),
    occurred_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX order_ledger_occurred_idx ON orders.order_ledger (occurred_at);
CREATE INDEX order_ledger_drop_occurred_idx ON orders.order_ledger (drop_id, occurred_at);

CREATE TABLE orders.outbox (
    id             bigint      PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    aggregate_type varchar(30) NOT NULL,
    aggregate_id   uuid        NOT NULL,
    event_type     varchar(50) NOT NULL,
    payload        jsonb       NOT NULL,
    status         varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    attempts       int         NOT NULL DEFAULT 0,
    created_at     timestamptz NOT NULL DEFAULT now(),
    published_at   timestamptz NULL
);

CREATE INDEX outbox_pending_idx ON orders.outbox (created_at) WHERE status = 'PENDING';
