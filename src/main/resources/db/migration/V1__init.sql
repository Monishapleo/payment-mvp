CREATE TABLE users
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(30)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orders
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT         NOT NULL,
    amount     DECIMAL(19, 2) NOT NULL,
    status     VARCHAR(40)    NOT NULL,
    version    BIGINT         NOT NULL DEFAULT 0,
    created_at TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id)
            REFERENCES users (id),

    CONSTRAINT chk_orders_amount
        CHECK (amount > 0)
);

CREATE INDEX idx_orders_user_id
    ON orders (user_id);

CREATE INDEX idx_orders_status
    ON orders (status);


CREATE TABLE payments
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id           BIGINT         NOT NULL,
    amount             DECIMAL(19, 2) NOT NULL,
    idempotency_key    VARCHAR(255)   NOT NULL UNIQUE,
    provider_reference VARCHAR(255) UNIQUE,
    status             VARCHAR(40)    NOT NULL,
    payment_method     VARCHAR(30)    NOT NULL,
    created_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version            BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT fk_payments_order
        FOREIGN KEY (order_id)
            REFERENCES orders (id),

    CONSTRAINT chk_payments_amount
        CHECK (amount > 0)
);

CREATE INDEX idx_payments_order_id
    ON payments (order_id);

CREATE INDEX idx_payments_status
    ON payments (status);


CREATE TABLE outbox_events
(
    id             CHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSON         NOT NULL,
    status         VARCHAR(30)  NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at   TIMESTAMP NULL
);

CREATE INDEX idx_outbox_status_created
    ON outbox_events (status, created_at);