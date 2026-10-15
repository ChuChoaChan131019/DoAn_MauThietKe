# Quyền sở hữu module

Tài liệu này là nguồn chuẩn để xác định ranh giới khi năm thành viên phát triển song song. Mục tiêu là giảm xung đột Git và ngăn module phụ thuộc trực tiếp vào chi tiết triển khai của nhau.

## 1. Phân chia module

| Module | Chủ sở hữu | Nghiệp vụ | Bảng sở hữu |
| --- | --- | --- | --- |
| [`merchant`](modules/MERCHANT_GUIDE.md) | Trần Thị Phương Trang (2314288) — Nhóm trưởng | Gian hàng, xét duyệt, danh mục, sản phẩm | `shops`, `categories`, `products`, `product_images`, `stock_restorations` |
| [`ordering`](modules/ORDERING_GUIDE.md) | Lê Anh Khoa (2312647) | Checkout, đơn hàng, lịch sử trạng thái, thanh toán | `orders`, `order_items`, `payments`, `order_status_histories` |
| [`shopping`](modules/SHOPPING_GUIDE.md) | Đỗ Đặng Diệu Linh (2312663) | Giỏ hàng, sản phẩm yêu thích và Buyer Order History qua `ordering.api` | `carts`, `cart_items`, `favorites` |
| [`identity`](modules/IDENTITY_GUIDE.md) | Doàn Trương Duy Khang (2111844) | Đăng ký, đăng nhập, tài khoản, phân quyền | `users` |
| [`engagement`](modules/ENGAGEMENT_GUIDE.md) | Đỗ Đặng Diệu Linh (2312663) — notifications/Observer; Huỳnh Thiên Phúc (2113010) — reviews, dashboard/Admin order | Thông báo, đánh giá, trang quản trị và báo cáo | `notifications` (Linh), `reviews` (Phúc) |

`common` do Doàn Trương Duy Khang (2111844) quản lý và chỉ chứa thành phần kỹ thuật thật sự dùng chung. Không đặt nghiệp vụ của một module vào `common` để tránh ownership không rõ ràng.

Feature AUTH 08 (Issue #68) do Trần Thị Phương Trang thực hiện trong `identity`; Doàn Trương Duy Khang vẫn là chủ module và review thay đổi theo ranh giới Identity.

## 2. Hướng phụ thuộc

```text
common       → không phụ thuộc module nghiệp vụ
identity     → common
merchant     → common, identity.api
shopping     → common, identity.api, merchant.api
ordering     → common, identity.api, merchant.api, shopping.api
engagement   → common, identity.api, merchant.api, ordering.api
```

- Module khác chỉ được import kiểu và interface từ `<module>.api`.
- Không gọi Repository hoặc sử dụng Entity của module khác.
- Module cung cấp chịu trách nhiệm thiết kế contract trong `api`; module sử dụng phải cùng review nếu contract thay đổi.
- Thông báo phát sinh từ `merchant` và `ordering` được chuyển tới `engagement` bằng Spring Event thay vì gọi Repository thông báo trực tiếp.
- Linh là feature owner Notification/Observer trong `engagement`; Phúc phối hợp và review ownership của module. Contract event vẫn do module phát event sở hữu.
- Trang quản trị không sở hữu lại nghiệp vụ. Nó gọi public API của module chịu trách nhiệm dữ liệu.

## 3. Tài nguyên do module sở hữu

Mỗi chủ module quản lý đồng thời:

```text
src/main/java/com/senvia/doangiuaky/<module>/
src/main/resources/templates/<module>/
src/main/resources/static/css/modules/<module>/
src/main/resources/static/js/modules/<module>/
src/test/java/com/senvia/doangiuaky/<module>/
```

Tài nguyên dùng chung cần Doàn Trương Duy Khang (2111844) review:

- `pom.xml` và `application.properties`.
- `com.senvia.doangiuaky.common`.
- `templates/fragments`.
- `static/css/common` và `static/js/common`.
- Cấu hình CI và build.

## 4. Quy trình thay đổi chéo module

1. Tạo issue mô tả contract hoặc dữ liệu cần sử dụng.
2. Chủ module cung cấp bổ sung hoặc điều chỉnh interface/DTO trong `api`.
3. Hai chủ module cùng review contract trước khi viết phần tích hợp.
4. Mỗi module triển khai trong pull request riêng nếu thay đổi lớn.
5. Chạy test của cả module cung cấp và module sử dụng trước khi merge.

Không giải quyết thiếu contract bằng cách import trực tiếp Entity, Repository hoặc Service nội bộ của module khác.
