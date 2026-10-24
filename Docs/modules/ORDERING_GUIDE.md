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
- `BuyerOrderApi` trả danh sách đơn của USER đang đăng nhập (lọc trạng thái, phân trang) và chi tiết đơn của chính họ. `AdminOrderApi` trả danh sách toàn hệ thống (lọc trạng thái, buyer, shop, mã đơn; phân trang) và chi tiết cho ADMIN đang hoạt động. `OrderPage`, `OrderSummary`, `OrderDetails` chỉ công bố DTO; chi tiết gồm snapshot nhận hàng, order items, payment từ bảng `payments` và lịch sử. Sai vai trò hoặc tài khoản bị khóa trả 403; buyer xem đơn người khác hoặc mã đơn không tồn tại trả 404; bộ lọc/trang không hợp lệ trả 400.
- Khi nhánh ORDER 06 được merge, lệnh hủy của buyer, seller và admin dùng chung `OrderCancellationApi`; Buyer/Admin query API không định nghĩa thêm lệnh hủy. `OrderCancellationService` tiếp tục chịu trách nhiệm transaction, quyền sở hữu, hoàn kho, payment và history.
- `ReviewOrderApi.requireReviewableItem(orderItemId, productId)` xác minh USER đang đăng nhập là buyer của order item, productId khớp và order đã `COMPLETED`; trả `ReviewableOrderItem` để Engagement kiểm tra đánh giá. Thiếu/sai mã trả 400 hoặc 404, đơn chưa hoàn thành trả 409, sai vai trò/tài khoản bị khóa trả 403. Quy tắc một review mỗi order item thuộc Engagement và ràng buộc bảng `reviews`.
- `OrderingStatisticsApi.forOwnShop(shopId)` chỉ trả `ShopOrderStatistics` cho USER ACTIVE sở hữu shop có owner đang hoạt động. DTO gồm tổng đơn, số đơn theo từng trạng thái (kể cả 0) và tổng `BigDecimal` của đơn `COMPLETED`; truy vấn luôn giới hạn đúng `shopId`. Sai vai trò trả 403, shop không thuộc quyền trả 404.
- Engagement STAT 02 lấy `shopId` từ Merchant public API theo current user rồi gọi contract này; không query Ordering repository.
- `OrderQueryApi` trả danh sách đơn theo buyer hiện tại hoặc toàn bộ đơn cho Admin ACTIVE, và chi tiết đơn sau khi kiểm tra quyền. Danh sách buyer được lọc theo `buyer_id` ngay tại repository; DTO `OrderListEntry` và `OrderDetailEntry` chỉ chứa dữ liệu cần hiển thị, không trả Entity hoặc Repository. Buyer xem đơn của người khác nhận 404; sai vai trò hoặc tài khoản bị khóa nhận 403. Các trang `/account/orders` và `/admin/orders` hiện dùng contract này thay cho dữ liệu mẫu, kèm link chi tiết chứa mã đơn. Giao diện Buyer Order History cuối cùng thuộc Shopping; giao diện Admin order cuối cùng thuộc Engagement theo phân công nhóm.
- `OrderHistoryApi` trả danh sách `OrderHistoryEntry` theo `changed_at`, `id` tăng dần qua `forCurrentBuyer(orderCode)`, `forCurrentSeller(orderCode)` và `forCurrentAdmin(orderCode)`. DTO chỉ chứa mã history, trạng thái trước/sau, nhãn hiển thị, mã người thực hiện, ghi chú và thời điểm thay đổi (kèm chuỗi thời gian Việt Nam); không công bố Entity/Repository. Ordering lấy actor từ `identity.api`, yêu cầu tài khoản `ACTIVE`; buyer chỉ đọc đơn của mình, seller chỉ đọc đơn thuộc shop do mình sở hữu và có chủ shop đang hoạt động, Admin chỉ đọc với vai trò `ADMIN`. Trái vai trò hoặc tài khoản bị khóa trả 403; đơn không tồn tại hoặc không thuộc quyền buyer/seller trả 404. Các route `/account/orders/history`, `/admin/orders/history` và timeline trên trang chi tiết đơn seller sử dụng cùng contract này.
- `OrderCancellationApi` công bố `cancelAsBuyer(orderCode, reason)`, `cancelAsSeller(orderCode, reason)` và `cancelAsAdmin(orderCode, reason)` cho Shopping/Engagement qua `ordering.api`. Ordering lấy actor từ phiên đăng nhập, khóa đơn, kiểm tra quyền sở hữu trước khi xử lý lý do; lý do sau trim phải có 1–500 ký tự. Đơn chỉ được hủy ở `PENDING`, `CONFIRMED` hoặc `PREPARING`; thao tác hủy, hoàn kho, cập nhật payment và lưu history nằm trong cùng transaction. Hủy thành công phát `OrderStatusChangedEvent` có `eventId`; listener chỉ xử lý sau commit. Actor trái quyền hoặc mã đơn không tồn tại nhận 404, lý do không hợp lệ nhận 400, trạng thái không cho hủy nhận 409. Consumer không tự hoàn kho hoặc đổi payment.
- `OrderCreatedEvent(eventId, orderId, shopId, shopOwnerId, buyerId, actorId, orderCode, status, occurredAt)` là contract trong `ordering.api`. Checkout phát một event cho mỗi order sau khi lưu order, payment và history, trừ kho và xóa mục giỏ trong cùng transaction. `shopOwnerId` được lấy từ snapshot Merchant authoritative để Engagement xác định đúng receiver mà không truy cập nội bộ Ordering/Merchant sau commit. Listener xử lý bằng `@TransactionalEventListener(AFTER_COMMIT)` để chỉ nhận event khi checkout commit; `eventId` riêng cho từng order hỗ trợ chống xử lý trùng.
- `OrderStatusChangedEvent(eventId, orderId, shopId, buyerId, actorId, orderCode, oldStatus, newStatus, occurredAt)` là contract trong `ordering.api`. Mỗi lần chủ shop chuyển trạng thái hợp lệ, Ordering lưu order và history trong cùng transaction rồi phát event. Listener dùng `@TransactionalEventListener(AFTER_COMMIT)` để chỉ xử lý khi transaction commit; chuyển trạng thái thất bại hoặc rollback không gửi thông báo. `eventId` riêng cho từng lần chuyển hỗ trợ chống xử lý trùng.
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

### PAY 08 — Vòng đời payment

- `payments.status` hỗ trợ `PENDING`, `COD_PENDING`, `PAID`, `FAILED`, `CANCELLED` và `REFUNDED`. Checkout chuyển khoản tạo `PENDING`; checkout COD tạo `COD_PENDING`. Bản mô phỏng hiện chưa có thao tác phát sinh `FAILED`, nhưng payment ở trạng thái này vẫn được đọc và có thể chuyển `CANCELLED` khi hủy đơn hợp lệ.
- Chủ shop xác nhận chuyển khoản `PENDING → PAID` và ghi `paid_at` một lần; COD chỉ chuyển `COD_PENDING → PAID` khi đơn `COMPLETED`. Các bước xử lý đơn yêu cầu payment có trạng thái và mốc thời gian phù hợp; không xác nhận lặp hoặc tiếp tục đơn với payment đã hủy/hoàn tiền.
- Hủy đơn hợp lệ chuyển `PENDING`, `COD_PENDING` hoặc `FAILED` thành `CANCELLED`; payment `PAID` có `paid_at` chuyển `REFUNDED`, giữ `paid_at` và ghi `refunded_at` cho hoàn tiền mô phỏng. `CANCELLED` và `REFUNDED` là trạng thái kết thúc; payment có phương thức, trạng thái hoặc mốc thời gian không nhất quán bị từ chối trước khi ghi dữ liệu.

## Trạng thái giao diện Ordering hiện tại

- Trang giỏ cho buyer chọn các `cartItemId` hợp lệ và chuyển chúng tới `GET /checkout`; form giữ danh sách này khi gửi `POST /checkout`.
- `POST /checkout` kiểm tra lựa chọn và biểu mẫu PAY 01/PAY 03, lấy lại đúng các mục đã chọn qua `shopping.api`, đọc lại giá, trạng thái, tồn kho và shop qua `merchant.api`, rồi hiển thị bản xem trước theo từng shop.
- Bản xem trước chỉ phản ánh dữ liệu tại thời điểm kiểm tra. Khi buyer xác nhận, checkout đọc lại dữ liệu, kiểm tra giá, trừ kho, tạo `orders`/`payments` theo shop và xóa các mục đã mua trong cùng transaction. Mục không chọn vẫn ở giỏ. Phí vận chuyển chưa được tính.
- Danh sách và chi tiết đơn của shop đọc order/item thực từ Ordering; phương thức, trạng thái, số tiền và mã tham chiếu thanh toán luôn đọc từ `payments`. Service kiểm tra chủ shop trước khi trả dữ liệu; shop `LOCKED` vẫn xem được đơn cũ khi tài khoản chủ shop còn hoạt động. Danh sách lọc trạng thái và phân trang 10 đơn/trang tại database, giữ bộ lọc khi chuyển trang; trang hoặc trạng thái không hợp lệ trả lỗi 400.
- Với đơn chuyển khoản còn `PENDING`, chủ shop (kể cả shop `LOCKED` có tài khoản chủ còn hoạt động) xác nhận đã nhận tiền mô phỏng trên trang chi tiết. Ordering khóa đơn, kiểm tra quyền sở hữu và payment, rồi ghi `PAID` cùng `paid_at` trong một transaction; đơn vẫn `PENDING` để ORDER 04 xử lý bước xác nhận đơn. Xác nhận lặp hoặc sai trạng thái bị từ chối.
- Chủ shop có tài khoản còn hoạt động (kể cả shop `LOCKED`) chuyển đơn cũ lần lượt `PENDING → CONFIRMED → PREPARING → SHIPPING → COMPLETED` trên trang chi tiết. Đơn chuyển khoản phải `PAID` trước khi xác nhận; COD giữ `COD_PENDING` đến lúc hoàn thành rồi chuyển `PAID`. Ordering khóa đơn, kiểm tra quyền sở hữu và trạng thái payment, ghi history, và phát `OrderStatusChangedEvent` cho mỗi lần chuyển hợp lệ; chuyển sai hoặc lặp bị từ chối.
- Buyer, chủ shop và Admin hủy đơn ở `PENDING`, `CONFIRMED` hoặc `PREPARING` bằng lý do hợp lệ. Ordering khóa đơn, kiểm tra quyền, hoàn từng order item qua `merchant.api`, chuyển payment chưa trả sang `CANCELLED` hoặc đã trả sang `REFUNDED` (ghi `refunded_at`), lưu history và phát `OrderStatusChangedEvent` trong cùng transaction. Hủy lặp hoặc lỗi giữa chừng không hoàn kho thêm và rollback toàn bộ. Trang chi tiết đơn buyer/Admin hiện có form hủy với CSRF và mã đơn thật, chỉ hiển thị ở trạng thái được hủy; lỗi validation hoặc trạng thái đơn được đưa về trang danh sách tương ứng bằng flash message. Shopping/Engagement có thể dùng `OrderCancellationApi` cho giao diện cuối cùng của họ.

## Kiểm thử/bàn giao

Test multi-shop rollback, Strategy, payment/order transition, ownership, cancel/idempotency, transaction/concurrency và statistics. Đỗ Đặng Diệu Linh review cart/Buyer Flow; Trần Thị Phương Trang review stock; Huỳnh Thiên Phúc review event/statistics.

Checklist chất lượng chung xem [CONTRIBUTING.md](../CONTRIBUTING.md).
