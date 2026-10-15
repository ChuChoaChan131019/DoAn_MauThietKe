CREATE TABLE stock_restorations (
    order_item_id BIGINT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_restorations_shop FOREIGN KEY (shop_id) REFERENCES shops(id),
    CONSTRAINT fk_stock_restorations_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT ck_stock_restorations_quantity CHECK (quantity > 0)
);
