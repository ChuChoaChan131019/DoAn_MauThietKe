INSERT INTO products (shop_id, category_id, name, description, price, stock_quantity, status, created_at, updated_at)
SELECT s.id, c.id, 'Áo thun cotton basic', 'Áo thun cotton mềm, thoáng mát, dễ phối đồ hằng ngày.',
       329000.00, 36, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM shops s CROSS JOIN categories c
WHERE s.shop_name = 'NOLA Store' AND c.name = 'Thời trang'
  AND NOT EXISTS (SELECT 1 FROM products p WHERE p.shop_id = s.id AND p.name = 'Áo thun cotton basic');

INSERT INTO products (shop_id, category_id, name, description, price, stock_quantity, status, created_at, updated_at)
SELECT s.id, c.id, 'Áo khoác denim classic', 'Áo khoác denim dáng cơ bản, phù hợp nhiều phong cách.',
       1250000.00, 18, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM shops s CROSS JOIN categories c
WHERE s.shop_name = 'MORI Store' AND c.name = 'Thời trang'
  AND NOT EXISTS (SELECT 1 FROM products p WHERE p.shop_id = s.id AND p.name = 'Áo khoác denim classic');

INSERT INTO products (shop_id, category_id, name, description, price, stock_quantity, status, created_at, updated_at)
SELECT s.id, c.id, 'Túi đeo chéo mini', 'Túi đeo chéo nhỏ gọn với ngăn chứa tiện dụng.',
       680000.00, 24, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM shops s CROSS JOIN categories c
WHERE s.shop_name = 'ASTER Store' AND c.name = 'Thời trang'
  AND NOT EXISTS (SELECT 1 FROM products p WHERE p.shop_id = s.id AND p.name = 'Túi đeo chéo mini');

INSERT INTO products (shop_id, category_id, name, description, price, stock_quantity, status, created_at, updated_at)
SELECT s.id, c.id, 'Áo cardigan dệt kim', 'Cardigan dệt kim nhẹ, mặc được trong nhiều mùa.',
       890000.00, 16, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM shops s CROSS JOIN categories c
WHERE s.shop_name = 'NOLA Store' AND c.name = 'Thời trang'
  AND NOT EXISTS (SELECT 1 FROM products p WHERE p.shop_id = s.id AND p.name = 'Áo cardigan dệt kim');

INSERT INTO products (shop_id, category_id, name, description, price, stock_quantity, status, created_at, updated_at)
SELECT s.id, c.id, 'Đèn ngủ gỗ sồi', 'Đèn ngủ ánh sáng dịu với chân đế gỗ sồi tối giản.',
       790000.00, 14, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM shops s CROSS JOIN categories c
WHERE s.shop_name = 'KANSO Store' AND c.name = 'Nhà cửa'
  AND NOT EXISTS (SELECT 1 FROM products p WHERE p.shop_id = s.id AND p.name = 'Đèn ngủ gỗ sồi');

INSERT INTO products (shop_id, category_id, name, description, price, stock_quantity, status, created_at, updated_at)
SELECT s.id, c.id, 'Bộ ly gốm thủ công', 'Bộ hai chiếc ly gốm men mộc dùng cho trà hoặc cà phê.',
       420000.00, 30, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM shops s CROSS JOIN categories c
WHERE s.shop_name = 'FORM Store' AND c.name = 'Nhà cửa'
  AND NOT EXISTS (SELECT 1 FROM products p WHERE p.shop_id = s.id AND p.name = 'Bộ ly gốm thủ công');

INSERT INTO products (shop_id, category_id, name, description, price, stock_quantity, status, created_at, updated_at)
SELECT s.id, c.id, 'Khay tre đa năng', 'Khay tre nhẹ, tiện dùng để sắp xếp đồ dùng trong nhà.',
       259000.00, 42, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM shops s CROSS JOIN categories c
WHERE s.shop_name = 'MORI Store' AND c.name = 'Nhà cửa'
  AND NOT EXISTS (SELECT 1 FROM products p WHERE p.shop_id = s.id AND p.name = 'Khay tre đa năng');

INSERT INTO products (shop_id, category_id, name, description, price, stock_quantity, status, created_at, updated_at)
SELECT s.id, c.id, 'Chăn cotton waffle', 'Chăn cotton dệt waffle mềm nhẹ, phù hợp dùng quanh năm.',
       950000.00, 11, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM shops s CROSS JOIN categories c
WHERE s.shop_name = 'ASTER Store' AND c.name = 'Nhà cửa'
  AND NOT EXISTS (SELECT 1 FROM products p WHERE p.shop_id = s.id AND p.name = 'Chăn cotton waffle');

INSERT INTO products (shop_id, category_id, name, description, price, stock_quantity, status, created_at, updated_at)
SELECT s.id, c.id, 'Serum cấp ẩm Hyaluronic', 'Serum dưỡng ẩm nhẹ với kết cấu thấm nhanh.',
       459000.00, 27, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM shops s CROSS JOIN categories c
WHERE s.shop_name = 'MAISON 28 Store' AND c.name = 'Làm đẹp'
  AND NOT EXISTS (SELECT 1 FROM products p WHERE p.shop_id = s.id AND p.name = 'Serum cấp ẩm Hyaluronic');

INSERT INTO products (shop_id, category_id, name, description, price, stock_quantity, status, created_at, updated_at)
SELECT s.id, c.id, 'Kem chống nắng SPF50', 'Kem chống nắng SPF50 kết cấu mỏng nhẹ dùng hằng ngày.',
       389000.00, 33, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM shops s CROSS JOIN categories c
WHERE s.shop_name = 'MAISON 28 Store' AND c.name = 'Làm đẹp'
  AND NOT EXISTS (SELECT 1 FROM products p WHERE p.shop_id = s.id AND p.name = 'Kem chống nắng SPF50');

INSERT INTO products (shop_id, category_id, name, description, price, stock_quantity, status, created_at, updated_at)
SELECT s.id, c.id, 'Sữa rửa mặt dịu nhẹ', 'Sữa rửa mặt tạo bọt nhẹ, dùng được cho quy trình chăm sóc da cơ bản.',
       249000.00, 40, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM shops s CROSS JOIN categories c
WHERE s.shop_name = 'ASTER Store' AND c.name = 'Làm đẹp'
  AND NOT EXISTS (SELECT 1 FROM products p WHERE p.shop_id = s.id AND p.name = 'Sữa rửa mặt dịu nhẹ');

INSERT INTO products (shop_id, category_id, name, description, price, stock_quantity, status, created_at, updated_at)
SELECT s.id, c.id, 'Son tint dưỡng nhẹ', 'Son tint màu tự nhiên với cảm giác ẩm nhẹ trên môi.',
       319000.00, 22, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM shops s CROSS JOIN categories c
WHERE s.shop_name = 'MAISON 28 Store' AND c.name = 'Làm đẹp'
  AND NOT EXISTS (SELECT 1 FROM products p WHERE p.shop_id = s.id AND p.name = 'Son tint dưỡng nhẹ');
