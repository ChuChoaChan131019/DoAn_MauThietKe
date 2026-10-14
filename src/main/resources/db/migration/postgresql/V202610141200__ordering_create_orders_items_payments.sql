-- PAY 02: Schema nen cho Ordering

-- 1. Bang don hang
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_code VARCHAR(30) NOT NULL UNIQUE,
    buyer_id BIGINT NOT NULL REFERENCES users(id),
    shop_id BIGINT NOT NULL REFERENCES shops(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    receiver_name VARCHAR(100) NOT NULL,
    receiver_phone VARCHAR(20) NOT NULL,
    shipping_address TEXT NOT NULL,
    note TEXT,
    total_amount NUMERIC(14,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'PREPARING', 'SHIPPING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_orders_total_positive CHECK (total_amount > 0)
);

CREATE INDEX idx_orders_buyer_created ON orders (buyer_id, created_at);

CREATE INDEX idx_orders_shop_created ON orders (shop_id, created_at);


-- 2. Bang chi tiet don hang
CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    product_name_snapshot VARCHAR(200) NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    quantity INTEGER NOT NULL,
    subtotal NUMERIC(14,2) NOT NULL,
    CONSTRAINT ck_order_items_unit_price_positive CHECK (unit_price > 0),
    CONSTRAINT ck_order_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_order_items_subtotal_positive CHECK (subtotal > 0),
    CONSTRAINT ck_order_items_subtotal_matches CHECK (subtotal = unit_price * quantity)
);

CREATE INDEX idx_order_items_order ON order_items(order_id);


-- 3. Bang thanh toan
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id),
    method VARCHAR(20) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    transaction_reference VARCHAR(100),
    paid_at TIMESTAMPTZ,
    refunded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_payments_method CHECK (method IN ('COD', 'BANK_TRANSFER')),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'COD_PENDING', 'PAID', 'FAILED', 'CANCELLED', 'REFUNDED')),
    CONSTRAINT ck_payments_amount_positive CHECK (amount > 0)
);