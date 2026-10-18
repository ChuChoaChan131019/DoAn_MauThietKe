# Hướng dẫn module Engagement

[← Playbook](../DEVELOPMENT_PLAYBOOK.md) · [Phân công](../PHAN_CONG_CONG_VIEC.md)

## Phạm vi

- **Chủ module:** Huỳnh Thiên Phúc (2113010).
- **Feature owner Notification/Observer:** Đỗ Đặng Diệu Linh (2312663); Phúc review quyền sở hữu và tích hợp trong module.
- **Requirement:** NOTI 01–07 do Linh phụ trách; REV 01–04 và dashboard/Admin order do Phúc phụ trách; STAT 01–05 do Phúc tổng hợp UI với dữ liệu TV1/TV2.
- **Phối hợp:** Admin giám sát/cancel order qua `ordering.api`.
- **Bảng:** `notifications`, `reviews`.
- **Code/view:** `com.senvia.doangiuaky.engagement`, `templates/engagement`.

## Dependency

- Dùng `identity.api` cho receiver/reviewer/Admin ACTIVE.
- Dùng `merchant.api` cho shop/product/product count.
- Dùng `ordering.api` cho review verification, order query/cancel và statistics.
- Linh sở hữu nghiệp vụ notification, bảng `notifications`, listener Observer và giao diện thông báo; Phúc sở hữu review, bảng `reviews`, dashboard và Admin order.
- Listener xử lý shop/order/review event sau commit; publisher vẫn thuộc module phát event.
- Không truy cập Entity/Repository/Service nội bộ module khác.

## Thứ tự feature

1. Linh: migration/schema và contract notification cùng Phúc review.
2. Linh: Observer listener cho ShopRequested, ShopReviewed, OrderCreated, OrderStatusChanged, ReviewCreated.
3. Linh: Notification list/unread/read-one/read-all.
4. Phúc: Review create/validation/list/average.
5. Phúc: Shop dashboard.
6. Phúc: Admin order list/filter/detail/cancel orchestration.
7. Linh chủ trì integration test event/notification; Phúc kiểm thử review/dashboard/Admin.

## Quy tắc đặc thù

- Event có `eventId`; unique `(event_id, receiver_id)`.
- Observer listener do Linh phụ trách, chạy sau commit và xử lý lặp không tạo notification trùng.
- Notification thuộc riêng receiver.
- Review chỉ cho order item COMPLETED của đúng buyer/product.
- Rating 1–5; unique `order_item_id`; không sửa/xóa trong MVP.
- Dashboard chỉ hiện đúng shop; revenue chỉ từ COMPLETED.
- Admin UI gọi Ordering API; không tự hủy/hoàn stock/refund.
- Review form đọc order item qua `ordering.api`; POST chỉ lưu sau khi contract xác minh buyer, product và trạng thái `COMPLETED`.
- Review list tại `/products/{productId}/reviews` hiển thị điểm trung bình và các review; product detail và buyer order detail liên kết tới luồng tương ứng.
- Seller dashboard tại `/engagement/dashboard` tổng hợp `MerchantStatisticsApi` và `OrderingStatisticsApi`; `/merchant/dashboard` chỉ chuyển hướng tới dashboard này.
- Admin order tại `/engagement/admin/orders` lọc qua `AdminOrderApi`; hủy đơn gọi `OrderCancellationApi`.
- `ReviewCreatedEvent` được phát trong transaction sau khi review lưu thành công; listener xử lý notification thuộc feature owner Notification/Observer.

## Kiểm thử/bàn giao

Linh kiểm thử listener sau commit/idempotency, receiver ownership và notification read state. Phúc kiểm thử review validation/unique/average, dashboard đúng shop và Admin query/cancel qua API. Doàn Trương Duy Khang review quyền; Trần Thị Phương Trang và Lê Anh Khoa review event do Merchant/Ordering phát.

Checklist chất lượng chung xem [CONTRIBUTING.md](../CONTRIBUTING.md).
