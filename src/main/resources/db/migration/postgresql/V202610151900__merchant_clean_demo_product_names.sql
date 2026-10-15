-- Clean up the names of generated products after their images have been assigned.
-- Restrict the update to seeded image IDs so merchant-created products are untouched.
UPDATE products
SET name = CASE
        WHEN name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - %' THEN
            CASE MOD(CAST(SUBSTRING(name FROM 13 FOR POSITION(' - ' IN name) - 13) AS INTEGER) - 1, 24)
                WHEN 0 THEN 'Áo sơ mi linen'
                WHEN 1 THEN 'Khay tre đa năng'
                WHEN 2 THEN 'Son tint dưỡng môi'
                WHEN 3 THEN 'Áo khoác denim'
                WHEN 4 THEN 'Khung tranh tối giản'
                WHEN 5 THEN 'Mặt nạ cấp ẩm'
                WHEN 6 THEN 'Giày sneaker'
                WHEN 7 THEN 'Đèn bàn trang trí'
                WHEN 8 THEN 'Kem chống nắng SPF50'
                WHEN 9 THEN 'Quần jeans'
                WHEN 10 THEN 'Chăn cotton'
                WHEN 11 THEN 'Sữa rửa mặt dịu nhẹ'
                WHEN 12 THEN 'Túi đeo chéo'
                WHEN 13 THEN 'Bình gốm trang trí'
                WHEN 14 THEN 'Nước hoa'
                WHEN 15 THEN 'Áo thun cotton'
                WHEN 16 THEN 'Bình hoa trang trí'
                WHEN 17 THEN 'Serum dưỡng da'
                WHEN 18 THEN 'Váy nữ'
                WHEN 19 THEN 'Bộ ly gốm'
                WHEN 20 THEN 'Sản phẩm chăm sóc da'
                WHEN 21 THEN 'Áo cardigan dệt kim'
                WHEN 22 THEN 'Hộp đựng đồ'
                ELSE 'Serum cấp ẩm'
            END
        ELSE REGEXP_REPLACE(
            REGEXP_REPLACE(name, '^Senvia Demo [0-9]+ - ', ''),
            ' - [^-]+$', '')
    END,
    description = CASE
        WHEN description LIKE 'Sản phẩm demo được tuyển chọn%' THEN
            'Sản phẩm mẫu trong bộ sưu tập của Senvia.'
        ELSE description
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE name LIKE 'Senvia Demo %'
  AND EXISTS (
      SELECT 1 FROM product_images image
      WHERE image.product_id = products.id
        AND image.public_id LIKE 'senvia/merchant/product-images/demo/%'
  );
