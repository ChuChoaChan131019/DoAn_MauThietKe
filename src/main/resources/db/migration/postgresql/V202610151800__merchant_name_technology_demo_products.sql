-- Replace generic technology demo names and images with matching product data.
-- Higher ranks are updated first so lower ranks stay stable as names change.

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080218/senvia/merchant/product-images/demo/technology/tech-25.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-25'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 24)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Điện thoại iPhone 6',
    description = 'Sản phẩm công nghệ mẫu: Điện thoại iPhone 6.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 24)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-25');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080218/senvia/merchant/product-images/demo/technology/tech-24.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-24'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 23)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Điện thoại Oppo K1',
    description = 'Sản phẩm công nghệ mẫu: Điện thoại Oppo K1.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 23)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-24');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080217/senvia/merchant/product-images/demo/technology/tech-23.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-23'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 22)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Máy tính bảng Samsung Galaxy Tab',
    description = 'Sản phẩm công nghệ mẫu: Máy tính bảng Samsung Galaxy Tab.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 22)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-23');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080216/senvia/merchant/product-images/demo/technology/tech-22.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-22'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 21)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Điện thoại iPhone X',
    description = 'Sản phẩm công nghệ mẫu: Điện thoại iPhone X.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 21)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-22');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080216/senvia/merchant/product-images/demo/technology/tech-21.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-21'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 20)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Laptop Dell XPS 13 9300',
    description = 'Sản phẩm công nghệ mẫu: Laptop Dell XPS 13 9300.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 20)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-21');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080215/senvia/merchant/product-images/demo/technology/tech-20.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-20'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 19)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Tai nghe Beats Flex',
    description = 'Sản phẩm công nghệ mẫu: Tai nghe Beats Flex.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 19)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-20');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080215/senvia/merchant/product-images/demo/technology/tech-19.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-19'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 18)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Điện thoại Vivo V9',
    description = 'Sản phẩm công nghệ mẫu: Điện thoại Vivo V9.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 18)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-19');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080214/senvia/merchant/product-images/demo/technology/tech-18.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-18'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 17)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Đế sạc không dây Apple AirPower',
    description = 'Sản phẩm công nghệ mẫu: Đế sạc không dây Apple AirPower.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 17)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-18');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080213/senvia/merchant/product-images/demo/technology/tech-17.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-17'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 16)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Laptop Asus Zenbook Pro',
    description = 'Sản phẩm công nghệ mẫu: Laptop Asus Zenbook Pro.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 16)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-17');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080212/senvia/merchant/product-images/demo/technology/tech-16.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-16'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 15)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Loa thông minh Amazon Echo Plus',
    description = 'Sản phẩm công nghệ mẫu: Loa thông minh Amazon Echo Plus.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 15)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-16');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080212/senvia/merchant/product-images/demo/technology/tech-15.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-15'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 14)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Điện thoại Realme C35',
    description = 'Sản phẩm công nghệ mẫu: Điện thoại Realme C35.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 14)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-15');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080211/senvia/merchant/product-images/demo/technology/tech-14.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-14'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 13)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Laptop Lenovo Yoga 920',
    description = 'Sản phẩm công nghệ mẫu: Laptop Lenovo Yoga 920.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 13)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-14');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080211/senvia/merchant/product-images/demo/technology/tech-13.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-13'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 12)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Pin sạc Apple MagSafe',
    description = 'Sản phẩm công nghệ mẫu: Pin sạc Apple MagSafe.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 12)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-13');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080210/senvia/merchant/product-images/demo/technology/tech-12.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-12'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 11)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Máy tính bảng Samsung Galaxy Tab S8 Plus',
    description = 'Sản phẩm công nghệ mẫu: Máy tính bảng Samsung Galaxy Tab S8 Plus.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 11)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-12');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080209/senvia/merchant/product-images/demo/technology/tech-11.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-11'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 10)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Điện thoại Oppo A57',
    description = 'Sản phẩm công nghệ mẫu: Điện thoại Oppo A57.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 10)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-11');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080209/senvia/merchant/product-images/demo/technology/tech-10.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-10'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 9)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Tai nghe Apple AirPods Max',
    description = 'Sản phẩm công nghệ mẫu: Tai nghe Apple AirPods Max.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 9)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-10');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080208/senvia/merchant/product-images/demo/technology/tech-09.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-09'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 8)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Laptop Huawei MateBook X Pro',
    description = 'Sản phẩm công nghệ mẫu: Laptop Huawei MateBook X Pro.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 8)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-09');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080207/senvia/merchant/product-images/demo/technology/tech-08.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-08'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 7)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Loa Apple HomePod Mini',
    description = 'Sản phẩm công nghệ mẫu: Loa Apple HomePod Mini.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 7)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-08');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080206/senvia/merchant/product-images/demo/technology/tech-07.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-07'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 6)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Đồng hồ thông minh Apple Watch Series 4',
    description = 'Sản phẩm công nghệ mẫu: Đồng hồ thông minh Apple Watch Series 4.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 6)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-07');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080206/senvia/merchant/product-images/demo/technology/tech-06.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-06'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 5)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Điện thoại Samsung Galaxy S10',
    description = 'Sản phẩm công nghệ mẫu: Điện thoại Samsung Galaxy S10.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 5)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-06');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080206/senvia/merchant/product-images/demo/technology/tech-05.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-05'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 4)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Củ sạc iPhone',
    description = 'Sản phẩm công nghệ mẫu: Củ sạc iPhone.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 4)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-05');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080204/senvia/merchant/product-images/demo/technology/tech-04.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-04'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 3)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Máy tính bảng iPad Mini 2021',
    description = 'Sản phẩm công nghệ mẫu: Máy tính bảng iPad Mini 2021.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 3)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-04');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080204/senvia/merchant/product-images/demo/technology/tech-03.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-03'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 2)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Laptop MacBook Pro 14 inch',
    description = 'Sản phẩm công nghệ mẫu: Laptop MacBook Pro 14 inch.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 2)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-03');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080204/senvia/merchant/product-images/demo/technology/tech-02.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-02'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 1)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Điện thoại iPhone 13 Pro',
    description = 'Sản phẩm công nghệ mẫu: Điện thoại iPhone 13 Pro.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 1)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-02');

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792080204/senvia/merchant/product-images/demo/technology/tech-01.webp',
    public_id = 'senvia/merchant/product-images/demo/technology/tech-01'
WHERE product_id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 0)
  AND public_id LIKE 'senvia/merchant/product-images/demo/%';

UPDATE products
SET name = 'Tai nghe Apple AirPods',
    description = 'Sản phẩm công nghệ mẫu: Tai nghe Apple AirPods.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT p.id FROM products p JOIN categories c ON c.id = p.category_id WHERE p.name LIKE 'Senvia Demo % - Sản phẩm tuyển chọn - Công nghệ' AND c.name = 'Công nghệ' ORDER BY p.id LIMIT 1 OFFSET 0)
  AND EXISTS (SELECT 1 FROM product_images i WHERE i.product_id = products.id AND i.public_id = 'senvia/merchant/product-images/demo/technology/tech-01');
