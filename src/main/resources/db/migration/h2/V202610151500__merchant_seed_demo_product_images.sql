INSERT INTO product_images (product_id, image_url, public_id, display_order, created_at)
SELECT p.id,
       CASE c.name
           WHEN 'Thời trang' THEN
               CASE MOD(p.id, 3)
                   WHEN 0 THEN 'https://images.unsplash.com/photo-1584916201218-f4242ceb4809?w=800&q=85'
                   WHEN 1 THEN 'https://images.unsplash.com/photo-1490481651871-ab68de25d43d?w=800&q=85'
                   ELSE 'https://images.unsplash.com/photo-1475179532958-3602fc5ce64a?w=800&q=85'
               END
           WHEN 'Nhà cửa' THEN
               CASE MOD(p.id, 3)
                   WHEN 0 THEN 'https://images.unsplash.com/photo-1616486338812-3dadae4b4ace?w=800&q=85'
                   WHEN 1 THEN 'https://images.unsplash.com/photo-1507473885765-e6ed057f782c?w=800&q=85'
                   ELSE 'https://images.unsplash.com/photo-1505691938895-1758d7feb511?w=800&q=85'
               END
           WHEN 'Làm đẹp' THEN
               CASE MOD(p.id, 3)
                   WHEN 0 THEN 'https://images.unsplash.com/photo-1596462502278-27bf85033e5a?w=800&q=85'
                   WHEN 1 THEN 'https://images.unsplash.com/photo-1594035910387-fea47714263f?w=800&q=85'
                   ELSE 'https://images.unsplash.com/photo-1541643600914-78b084683702?w=800&q=85'
               END
           ELSE 'https://images.unsplash.com/photo-1513694203232-719a280e022f?w=800&q=85'
       END,
       'external-demo:' || CAST(p.id AS VARCHAR),
       0,
       CURRENT_TIMESTAMP
FROM products p
JOIN categories c ON c.id = p.category_id
WHERE NOT EXISTS (
    SELECT 1
    FROM product_images existing_image
    WHERE existing_image.product_id = p.id
);
