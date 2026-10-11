# Hướng dẫn module Merchant

[← Playbook](../DEVELOPMENT_PLAYBOOK.md) · [Phân công](../PHAN_CONG_CONG_VIEC.md)

## Phạm vi

- **Chủ sở hữu:** Trần Thị Phương Trang (2314288) — Nhóm trưởng.
- **Requirement:** SHOP 01–10, PROD 01–11; cung cấp dữ liệu STAT 01.
- **Bảng:** `shops`, `categories`, `products`, `product_images`.
- **Code/view:** `com.senvia.doangiuaky.merchant`, `templates/merchant`.

Merchant sở hữu vòng đời shop, category, product, ảnh và stock.

## Dependency và public contract

- Dùng `identity.api` để kiểm tra user, Admin, owner và account status.
- Cung cấp `merchant.api`: shop/product summary, giá, stock, khả năng bán, trừ/hoàn stock và product count.
- `CartProductView#isSaleable()` là kết quả khả năng bán chuẩn trong public contract; module tiêu thụ không truy cập Entity/Repository/Service nội bộ.
- `MerchantApi#findProductForCart` trả summary khi product tồn tại nhưng không bán được; `Optional.empty()` chỉ dành cho ID không hợp lệ hoặc product không tồn tại.
- Phát event gửi/duyệt/từ chối shop có `eventId`.
- Không công bố Entity, Repository, Service nội bộ hoặc Cloudinary secret.

## Thứ tự feature

1. Migration và mapping shop/category/product/image.
2. State Pattern và test chuyển trạng thái.
3. User gửi/xem/sửa/gửi lại yêu cầu shop.
4. Admin duyệt, từ chối, khóa và mở khóa.
5. Category CRUD/ẩn.
6. Product CRUD/ẩn, ownership và ảnh Cloudinary.
7. Catalog công khai, tìm kiếm, lọc và phân trang.
8. Atomic stock API, statistics API và integration event.

## Quy tắc đặc thù

- Một user tối đa một shop; từ chối/khóa bắt buộc lý do.
- Chỉ Admin đổi trạng thái xét duyệt.
- PENDING/REJECTED/LOCKED không sửa catalog hoặc nhận order mới.
- LOCKED vẫn cho xử lý order đã tồn tại.
- Product chỉ `ACTIVE/HIDDEN`; hết hàng suy từ stock bằng 0.
- Giá dùng `BigDecimal` tương ứng `NUMERIC(12,2)`, phải lớn hơn 0 và có tối đa 2 chữ số thập phân; stock là số nguyên không âm và product có ít nhất một ảnh.
- Catalog chỉ hiện khi owner ACTIVE, shop APPROVED, category active, product ACTIVE và stock > 0.
- Trừ stock dùng conditional update nguyên tử; hoàn stock phải chống lặp.

## Kiểm thử/bàn giao

Test State, ownership, validation, catalog visibility, category/owner/shop bị khóa, stock concurrency và lỗi Cloudinary. Lê Anh Khoa review stock contract; Đỗ Đặng Diệu Linh review dữ liệu cart; Huỳnh Thiên Phúc review event/dashboard contract.

Checklist chất lượng chung xem [CONTRIBUTING.md](../CONTRIBUTING.md).
