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
- `OrderCreatedEvent(eventId, orderId, shopId, buyerId, actorId, orderCode, status, occurredAt)` là contract trong `ordering.api`. Checkout phát một event cho mỗi order sau khi lưu order, payment và history, trừ kho và xóa mục giỏ trong cùng transaction. Listener xử lý bằng `@TransactionalEventListener(AFTER_COMMIT)` để chỉ nhận event khi checkout commit; `eventId` riêng cho từng order hỗ trợ chống xử lý trùng.
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

## Trạng thái giao diện checkout hiện tại

- Trang giỏ cho buyer chọn các `cartItemId` hợp lệ và chuyển chúng tới `GET /checkout`; form giữ danh sách này khi gửi `POST /checkout`.
- `POST /checkout` kiểm tra lựa chọn và biểu mẫu PAY 01/PAY 03, lấy lại đúng các mục đã chọn qua `shopping.api`, đọc lại giá, trạng thái, tồn kho và shop qua `merchant.api`, rồi hiển thị bản xem trước theo từng shop.
- Bản xem trước chỉ phản ánh dữ liệu tại thời điểm kiểm tra. Khi buyer xác nhận, checkout đọc lại dữ liệu, kiểm tra giá, trừ kho, tạo `orders`/`payments` theo shop và xóa các mục đã mua trong cùng transaction. Mục không chọn vẫn ở giỏ. Phí vận chuyển chưa được tính.
- Danh sách và chi tiết đơn của shop đọc order/item thực từ Ordering; phương thức, trạng thái, số tiền và mã tham chiếu thanh toán luôn đọc từ `payments`. Service kiểm tra chủ shop trước khi trả dữ liệu; shop `LOCKED` vẫn xem được đơn cũ khi tài khoản chủ shop còn hoạt động. Danh sách lọc trạng thái và phân trang 10 đơn/trang tại database, giữ bộ lọc khi chuyển trang; trang hoặc trạng thái không hợp lệ trả lỗi 400. Các thao tác chuyển trạng thái/hủy đơn trên giao diện shop chưa được kết nối.

## Kiểm thử/bàn giao

Test multi-shop rollback, Strategy, payment/order transition, ownership, cancel/idempotency, transaction/concurrency và statistics. Đỗ Đặng Diệu Linh review cart/Buyer Flow; Trần Thị Phương Trang review stock; Huỳnh Thiên Phúc review event/statistics.

Checklist chất lượng chung xem [CONTRIBUTING.md](../CONTRIBUTING.md).
