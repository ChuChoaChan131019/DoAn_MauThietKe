UPDATE products
SET description = 'Chưa có mô tả.'
WHERE description IS NULL OR LENGTH(TRIM(description)) = 0;

ALTER TABLE products
    ALTER COLUMN description SET NOT NULL;

ALTER TABLE products
    ADD CONSTRAINT ck_products_description_not_blank CHECK (LENGTH(TRIM(description)) > 0);
