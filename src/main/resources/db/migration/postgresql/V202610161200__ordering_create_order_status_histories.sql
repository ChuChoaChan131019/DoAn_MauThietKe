-- ORDER 03: Lich su trang thai don hang
-- PostgreSQL / Supabase

CREATE TABLE order_status_histories (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    changed_by BIGINT NOT NULL REFERENCES users(id),
    note TEXT,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_order_history_old_status
        CHECK (
            old_status IS NULL
            OR old_status IN (
                'PENDING',
                'CONFIRMED',
                'PREPARING',
                'SHIPPING',
                'COMPLETED',
                'CANCELLED'
            )
        ),

    CONSTRAINT ck_order_history_new_status
        CHECK (
            new_status IN (
                'PENDING',
                'CONFIRMED',
                'PREPARING',
                'SHIPPING',
                'COMPLETED',
                'CANCELLED'
            )
        ),

    CONSTRAINT ck_order_history_initial
        CHECK (
            old_status IS NOT NULL
            OR new_status = 'PENDING'
        ),

    CONSTRAINT uq_order_history_new_status UNIQUE (order_id, new_status)
);

-- Ho tro truy van lich su theo thu tu thoi gian.
CREATE INDEX idx_order_history_order_time
ON order_status_histories (order_id, changed_at, id);

-- Bo sung moc khoi tao cho cac don da ton tai
-- truoc khi trien khai ORDER 03.
INSERT INTO order_status_histories (order_id, old_status, new_status, changed_by, note, changed_at)

SELECT id, NULL, 'PENDING', buyer_id, 'Don hang duoc tao truoc ORDER 03', created_at
FROM orders;