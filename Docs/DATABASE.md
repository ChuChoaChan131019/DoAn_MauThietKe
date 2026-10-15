# Thiết kế cơ sở dữ liệu

Tài liệu này tập trung vào thiết kế vật lý, constraint, index, migration và toàn vẹn giao dịch trên PostgreSQL. Yêu cầu dữ liệu nghiệp vụ lấy theo [SRS.md](SRS.md); chủ sở hữu bảng lấy theo [MODULE_OWNERSHIP.md](MODULE_OWNERSHIP.md).

## 1. Tổng quan

- PostgreSQL/Supabase.
- 15 bảng, tên dạng số nhiều.
- Tiền tệ dùng `NUMERIC`; Java dùng `BigDecimal`.
- Thời gian ưu tiên `TIMESTAMPTZ`.
- Không lưu file ảnh trong database; chỉ lưu URL và Cloudinary `public_id`.

## 2. Bảng và ownership

| Bảng | Module | Mục đích |
| --- | --- | --- |
| `users` | `identity` | Account, role và trạng thái |
| `shops` | `merchant` | Shop và quy trình xét duyệt |
| `categories` | `merchant` | Category |
| `products` | `merchant` | Product, giá và tồn kho |
| `product_images` | `merchant` | Ảnh Cloudinary |
| `stock_restorations` | `merchant` | Dấu hoàn kho theo order item, chống cộng stock lặp |
| `carts` | `shopping` | Cart hiện tại |
| `cart_items` | `shopping` | Product và quantity trong cart |
| `favorites` | `shopping` | Product yêu thích |
| `orders` | `ordering` | Order tách theo shop |
| `order_items` | `ordering` | Snapshot product/giá |
| `payments` | `ordering` | Payment mô phỏng |
| `order_status_histories` | `ordering` | Lịch sử trạng thái |
| `notifications` | `engagement` | Notification nội bộ |
| `reviews` | `engagement` | Rating và comment |

## 3. Quan hệ chính

```text
users 1 ─── 0..1 shops
users 1 ─── 1 carts
carts 1 ─── * cart_items
shops 1 ─── * products
categories 1 ─── * products
products 1 ─── * product_images
products 1 ─── * stock_restorations
users 1 ─── * orders
shops 1 ─── * orders
orders 1 ─── * order_items
orders 1 ─── 1 payments
orders 1 ─── * order_status_histories
users 1 ─── * notifications
users * ─── * products qua favorites
users * ─── * products qua reviews/order_items
```

## 4. Constraint bắt buộc

| Constraint | Mục đích |
| --- | --- |
| Unique index `LOWER(users.email)` | Email duy nhất không phân biệt hoa thường |
| `UNIQUE(shops.owner_id)` | Một user tối đa một shop |
| `UNIQUE(carts.user_id)` | Một user một cart |
| `UNIQUE(cart_items.cart_id, product_id)` | Không trùng product trong cart |
| `UNIQUE(favorites.user_id, product_id)` | Không trùng favorite |
| `UNIQUE(payments.order_id)` | Một payment cho mỗi order |
| `UNIQUE(reviews.order_item_id)` | Một review cho mỗi order item |
| `UNIQUE(notifications.event_id, receiver_id)` | Listener idempotent theo receiver |
| `CHECK(products.price > 0)` | Giá hợp lệ |
| `CHECK(products.stock_quantity >= 0)` | Tồn kho không âm |
| `PRIMARY KEY(stock_restorations.order_item_id)` | Mỗi dòng đơn chỉ hoàn kho một lần; ID từ Ordering là khóa tham chiếu dạng giá trị, không có FK chéo module |
| `CHECK(stock_restorations.quantity > 0)` | Số lượng hoàn kho hợp lệ |
| `CHECK(cart_items.quantity > 0)` | Quantity hợp lệ |
| `CHECK(reviews.rating BETWEEN 1 AND 5)` | Rating hợp lệ |

Khóa ngoại giao dịch không được xóa tùy tiện; ưu tiên trạng thái ẩn/khóa.

## 5. Snapshot và nguồn dữ liệu chuẩn

- Cart không lưu giá; đọc `products.price` hiện tại.
- `order_items` lưu tên, đơn giá, quantity và subtotal tại thời điểm đặt.
- `payments` là nguồn chuẩn duy nhất cho payment method/status.
- `products.stock_quantity = 0` biểu diễn hết hàng; không tạo trạng thái OUT_OF_STOCK.
- Shop bị khóa lưu lý do, người khóa và thời điểm.
- Tài khoản USER bị khóa lưu lý do, Admin thực hiện và thời điểm; mở khóa xóa toàn bộ metadata khóa.
- Notification lưu `event_id` để chống xử lý lặp.

## 6. Index đề xuất

Ngoài unique index và index do FK yêu cầu, cân nhắc:

- `products(shop_id, status)`.
- `products(category_id, status)`.
- Index tìm kiếm tên product phù hợp với chiến lược query.
- `orders(buyer_id, created_at DESC)`.
- `orders(shop_id, status, created_at DESC)`.
- `order_status_histories(order_id, changed_at)`.
- `notifications(receiver_id, is_read, created_at DESC)`.
- `reviews(product_id, created_at DESC)`.

Chỉ thêm index khi có query cụ thể và xác minh kế hoạch truy vấn phù hợp.

## 7. Transaction và concurrency

- Checkout nhiều shop chạy all-or-nothing trong một transaction.
- Trừ kho dùng conditional update nguyên tử với điều kiện tồn kho đủ.
- Một bước tạo order/payment, trừ kho hoặc xóa cart lỗi phải rollback toàn bộ checkout.
- Hủy order hoàn kho và cập nhật payment đúng một lần.
- Contract hoàn kho cần khóa/idempotency phù hợp để request lặp không cộng kho hai lần.
- `stock_restorations` và thao tác cộng stock nằm cùng transaction hủy đơn; cùng `order_item_id` và payload chỉ được xử lý một lần.
- Event notification chỉ được xử lý sau commit.
- Review và favorite chống trùng ở cả Service và database constraint.

## 8. Migration

- Đặt tại `src/main/resources/db/migration`; migration dùng cú pháp riêng theo cơ sở dữ liệu có thể đặt trong thư mục vendor (`h2`, `postgresql`).
- Tạo migration mới; không sửa migration đã dùng ở môi trường chung.
- Tên: `VyyyyMMddHHmm__module_description.sql`.
- Chủ bảng tạo migration; khóa ngoại chéo module cần hai chủ module review.
- Migration được review cùng Entity/Repository liên quan.
- Không chỉnh database dùng chung mà không có script tương ứng.
- Không chứa secret hoặc dữ liệu cá nhân thật.

Ví dụ:

```text
V202609301430__merchant_create_products.sql
V202609301500__ordering_create_orders.sql
```

## 9. Dữ liệu demo và backup

Dữ liệu demo tối thiểu lấy theo SRS: Admin/User, shop ở nhiều trạng thái, category, product và order phục vụ toàn bộ luồng trình diễn. Trước buổi bảo vệ cần xuất bản sao schema và dữ liệu demo đã loại thông tin nhạy cảm.
