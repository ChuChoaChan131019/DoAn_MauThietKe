INSERT INTO products (
    shop_id, category_id, name, description, price, stock_quantity, status, created_at, updated_at
)
SELECT shop_seed.shop_id,
       category_seed.category_id,
       'Senvia Demo ' || CAST(demo_sequence.sequence_number AS VARCHAR) || ' - ' ||
           CASE category_seed.category_name
               WHEN 'Thời trang' THEN
                   CASE MOD(demo_sequence.sequence_number, 8)
                       WHEN 0 THEN 'Áo thun cotton'
                       WHEN 1 THEN 'Áo sơ mi linen'
                       WHEN 2 THEN 'Quần jeans classic'
                       WHEN 3 THEN 'Váy midi thanh lịch'
                       WHEN 4 THEN 'Áo khoác denim'
                       WHEN 5 THEN 'Túi đeo chéo mini'
                       WHEN 6 THEN 'Áo cardigan dệt kim'
                       ELSE 'Giày sneaker tối giản'
                   END
               WHEN 'Nhà cửa' THEN
                   CASE MOD(demo_sequence.sequence_number, 8)
                       WHEN 0 THEN 'Đèn bàn gỗ sồi'
                       WHEN 1 THEN 'Bình gốm trang trí'
                       WHEN 2 THEN 'Khay tre đa năng'
                       WHEN 3 THEN 'Chăn cotton waffle'
                       WHEN 4 THEN 'Bộ ly gốm thủ công'
                       WHEN 5 THEN 'Khung tranh tối giản'
                       WHEN 6 THEN 'Gối tựa linen'
                       ELSE 'Hộp đựng đồ tiện dụng'
                   END
               WHEN 'Làm đẹp' THEN
                   CASE MOD(demo_sequence.sequence_number, 8)
                       WHEN 0 THEN 'Serum cấp ẩm'
                       WHEN 1 THEN 'Kem chống nắng SPF50'
                       WHEN 2 THEN 'Sữa rửa mặt dịu nhẹ'
                       WHEN 3 THEN 'Son tint dưỡng môi'
                       WHEN 4 THEN 'Toner cân bằng da'
                       WHEN 5 THEN 'Kem dưỡng ban đêm'
                       WHEN 6 THEN 'Mặt nạ cấp ẩm'
                       ELSE 'Nước hoa hương gỗ'
                   END
               ELSE 'Sản phẩm tuyển chọn'
           END || ' - ' || category_seed.category_name,
       'Sản phẩm demo được tuyển chọn cho danh mục ' || category_seed.category_name ||
           ', phù hợp sử dụng hằng ngày.',
       CAST(120000 + MOD(demo_sequence.sequence_number * 19371, 4300000) AS NUMERIC(12, 2)),
       8 + MOD(demo_sequence.sequence_number * 17, 83),
       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    SELECT 1 AS sequence_number
    UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
    UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
    UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
    UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
    UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24 UNION ALL SELECT 25
    UNION ALL SELECT 26 UNION ALL SELECT 27 UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 30
    UNION ALL SELECT 31 UNION ALL SELECT 32 UNION ALL SELECT 33 UNION ALL SELECT 34 UNION ALL SELECT 35
    UNION ALL SELECT 36 UNION ALL SELECT 37 UNION ALL SELECT 38 UNION ALL SELECT 39 UNION ALL SELECT 40
    UNION ALL SELECT 41 UNION ALL SELECT 42 UNION ALL SELECT 43 UNION ALL SELECT 44 UNION ALL SELECT 45
    UNION ALL SELECT 46 UNION ALL SELECT 47 UNION ALL SELECT 48 UNION ALL SELECT 49 UNION ALL SELECT 50
    UNION ALL SELECT 51 UNION ALL SELECT 52 UNION ALL SELECT 53 UNION ALL SELECT 54 UNION ALL SELECT 55
    UNION ALL SELECT 56 UNION ALL SELECT 57 UNION ALL SELECT 58 UNION ALL SELECT 59 UNION ALL SELECT 60
    UNION ALL SELECT 61 UNION ALL SELECT 62 UNION ALL SELECT 63 UNION ALL SELECT 64 UNION ALL SELECT 65
    UNION ALL SELECT 66 UNION ALL SELECT 67 UNION ALL SELECT 68 UNION ALL SELECT 69 UNION ALL SELECT 70
    UNION ALL SELECT 71 UNION ALL SELECT 72 UNION ALL SELECT 73 UNION ALL SELECT 74 UNION ALL SELECT 75
    UNION ALL SELECT 76 UNION ALL SELECT 77 UNION ALL SELECT 78 UNION ALL SELECT 79 UNION ALL SELECT 80
    UNION ALL SELECT 81 UNION ALL SELECT 82 UNION ALL SELECT 83 UNION ALL SELECT 84 UNION ALL SELECT 85
    UNION ALL SELECT 86 UNION ALL SELECT 87 UNION ALL SELECT 88 UNION ALL SELECT 89 UNION ALL SELECT 90
    UNION ALL SELECT 91 UNION ALL SELECT 92 UNION ALL SELECT 93 UNION ALL SELECT 94 UNION ALL SELECT 95
    UNION ALL SELECT 96 UNION ALL SELECT 97 UNION ALL SELECT 98 UNION ALL SELECT 99 UNION ALL SELECT 100
) demo_sequence
CROSS JOIN (
    SELECT id AS category_id,
           name AS category_name,
           ROW_NUMBER() OVER (ORDER BY id) - 1 AS category_index,
           COUNT(*) OVER () AS category_count
    FROM categories
    WHERE active = TRUE
) category_seed
CROSS JOIN (
    SELECT s.id AS shop_id,
           ROW_NUMBER() OVER (ORDER BY s.id) - 1 AS shop_index,
           COUNT(*) OVER () AS shop_count
    FROM shops s
    JOIN users owner ON owner.id = s.owner_id AND owner.account_status = 'ACTIVE'
    WHERE s.status = 'APPROVED'
) shop_seed
WHERE MOD(demo_sequence.sequence_number - 1, category_seed.category_count) = category_seed.category_index
  AND MOD(demo_sequence.sequence_number - 1, shop_seed.shop_count) = shop_seed.shop_index
  AND NOT EXISTS (
      SELECT 1
      FROM products existing_product
      WHERE existing_product.category_id = category_seed.category_id
        AND existing_product.name = 'Senvia Demo ' || CAST(demo_sequence.sequence_number AS VARCHAR) || ' - ' ||
            CASE category_seed.category_name
                WHEN 'Thời trang' THEN
                    CASE MOD(demo_sequence.sequence_number, 8)
                        WHEN 0 THEN 'Áo thun cotton'
                        WHEN 1 THEN 'Áo sơ mi linen'
                        WHEN 2 THEN 'Quần jeans classic'
                        WHEN 3 THEN 'Váy midi thanh lịch'
                        WHEN 4 THEN 'Áo khoác denim'
                        WHEN 5 THEN 'Túi đeo chéo mini'
                        WHEN 6 THEN 'Áo cardigan dệt kim'
                        ELSE 'Giày sneaker tối giản'
                    END
                WHEN 'Nhà cửa' THEN
                    CASE MOD(demo_sequence.sequence_number, 8)
                        WHEN 0 THEN 'Đèn bàn gỗ sồi'
                        WHEN 1 THEN 'Bình gốm trang trí'
                        WHEN 2 THEN 'Khay tre đa năng'
                        WHEN 3 THEN 'Chăn cotton waffle'
                        WHEN 4 THEN 'Bộ ly gốm thủ công'
                        WHEN 5 THEN 'Khung tranh tối giản'
                        WHEN 6 THEN 'Gối tựa linen'
                        ELSE 'Hộp đựng đồ tiện dụng'
                    END
                WHEN 'Làm đẹp' THEN
                    CASE MOD(demo_sequence.sequence_number, 8)
                        WHEN 0 THEN 'Serum cấp ẩm'
                        WHEN 1 THEN 'Kem chống nắng SPF50'
                        WHEN 2 THEN 'Sữa rửa mặt dịu nhẹ'
                        WHEN 3 THEN 'Son tint dưỡng môi'
                        WHEN 4 THEN 'Toner cân bằng da'
                        WHEN 5 THEN 'Kem dưỡng ban đêm'
                        WHEN 6 THEN 'Mặt nạ cấp ẩm'
                        ELSE 'Nước hoa hương gỗ'
                    END
                ELSE 'Sản phẩm tuyển chọn'
            END || ' - ' || category_seed.category_name
  );
