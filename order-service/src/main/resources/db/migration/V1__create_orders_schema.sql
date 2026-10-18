CREATE TABLE orders.drops (
    id                     uuid         PRIMARY KEY,
    name                   varchar(100) NOT NULL,
    total_quantity         int          NOT NULL CHECK (total_quantity > 0),
    remaining_quantity     int          NOT NULL,
    open_at                timestamptz  NOT NULL,
    close_at               timestamptz  NOT NULL,
    status                 varchar(20)  NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'OPEN', 'SOLD_OUT', 'CLOSED')),
    admission_rate_per_sec int          NOT NULL DEFAULT 50 CHECK (admission_rate_per_sec BETWEEN 1 AND 10000),
    created_by             uuid         NOT NULL,
    created_at             timestamptz  NOT NULL DEFAULT now(),
    updated_at             timestamptz  NOT NULL DEFAULT now(),
    CHECK (remaining_quantity BETWEEN 0 AND total_quantity),
    CHECK (close_at > open_at)
);

CREATE INDEX drops_status_open_at_idx ON orders.drops (status, open_at);

CREATE TABLE orders.orders (
    id           uuid        PRIMARY KEY,
    drop_id      uuid        NOT NULL REFERENCES orders.drops (id),
    user_id      uuid        NOT NULL,
    quantity     int         NOT NULL CHECK (quantity = 1),
    status       varchar(20) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELED')),
    confirmed_at timestamptz NOT NULL,
    canceled_at  timestamptz NULL,
    CHECK ((status = 'CANCELED') = (canceled_at IS NOT NULL)),
    UNIQUE (drop_id, user_id)
);

CREATE INDEX orders_user_confirmed_idx ON orders.orders (user_id, confirmed_at DESC);
CREATE INDEX orders_drop_status_idx ON orders.orders (drop_id, status);
