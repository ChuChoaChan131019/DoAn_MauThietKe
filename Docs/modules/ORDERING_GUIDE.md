# Hướng dẫn module Ordering

[← Playbook](../DEVELOPMENT_PLAYBOOK.md) · [Phân công](../PHAN_CONG_CONG_VIEC.md)

## Phạm vi

- **Chủ sở hữu:** Lê Anh Khoa (2312647).
- **Requirement core:** PAY 01–08, ORDER 02–09.
- **Phối hợp:** cung cấp API để Đỗ Đặng Diệu Linh thực hiện UI/query ORDER 01.
- **Dữ liệu:** STAT 02–04 và xác minh Review.
- **Bảng:** `orders`, `order_items`, `payments`, `order_status_histories`.
- **Code/view:** `com.senvia.doangiuaky.ordering`, `templates/ordering`.

## Dependency và public contract

- Dùng `identity.api` cho buyer/quyền.
- Dùng `shopping.api` cho checkout snapshot và xóa cart sau thành công.
- Chỉ xử lý các `cartItemId` người mua đã chọn, sau khi đối chiếu với snapshot giỏ
  và kiểm tra lại giá, tồn kho, trạng thái qua `merchant.api`. Item không được chọn
  không vào trang thanh toán hoặc đơn hàng và vẫn ở giỏ.
- Dùng `merchant.api` cho khả năng bán, giá và trừ/hoàn stock.
- Cung cấp `ordering.api`: Buyer/Admin query, cancel command, review verification, statistics và order event.
- Không công bố Order Entity/Repository/Service nội bộ.

## Thứ tự feature

1. Migration, mapping và enum.
2. Order transition và history.
3. COD/Bank Transfer Strategy và factory.
4. Checkout transaction all-or-nothing.
5. Seller order list/detail và payment confirmation.
6. Order transition/cancel, stock restore và payment update.
7. Public API, statistics và event.
8. Tích hợp Buyer Order History/Admin view qua contract.

## Quy tắc đặc thù

- Cart nhiều shop tạo một order/payment mỗi shop.
- Giá/tên được snapshot khi đặt thành công; tiền dùng `BigDecimal`.
- Giá đổi sau xác nhận phải yêu cầu buyer xác nhận lại.
- Một bước lỗi rollback toàn bộ order, payment, stock và cart.
- Bank transfer phải PAID trước CONFIRMED; COD chuyển PAID khi order COMPLETED.
- Chỉ PENDING/CONFIRMED/PREPARING được hủy và phải có lý do.
- Cancel hoàn stock và cập nhật payment đúng một lần.
- Revenue chỉ tính order COMPLETED của đúng shop.

## Kiểm thử/bàn giao

Test multi-shop rollback, Strategy, payment/order transition, ownership, cancel/idempotency, transaction/concurrency và statistics. Đỗ Đặng Diệu Linh review cart/Buyer Flow; Trần Thị Phương Trang review stock; Huỳnh Thiên Phúc review event/statistics.

Checklist chất lượng chung xem [CONTRIBUTING.md](../CONTRIBUTING.md).
