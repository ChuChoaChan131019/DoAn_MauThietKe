# Kế hoạch triển khai Issue #75 — NOTI 07: Thông báo review mới cho shop

> **Trạng thái:** Đã triển khai và xác minh. Targeted tests và toàn bộ 775 test của project đều đạt bằng JDK 25 (Maven yêu cầu Java 21+).

## 1. Mục tiêu và nguồn đối chiếu

- **Issue:** [#75 — NOTI 07: Thông báo review mới cho shop](https://github.com/ChuChoaChan131019/DoAn_MauThietKe/issues/75).
- **Requirement chính:** `NOTI 07` — chủ shop nhận thông báo khi sản phẩm thuộc shop có đánh giá mới hợp lệ.
- **Requirement phụ thuộc:** `REV 01`, `REV 02`, `REV 03` — chỉ review order item thuộc đơn `COMPLETED` của đúng buyer, rating từ 1–5 và mỗi order item chỉ có một review.
- **Quy tắc nghiệp vụ:** `BR 25` — notification chỉ được tạo sau khi transaction nghiệp vụ commit và duy nhất theo `(event_id, receiver_id)`; `BR 26` — review duy nhất theo `order_item_id`.
- **Tiêu chí nghiệm thu:** `AT 08` và `AT 10`.
- **Kiến trúc:** Observer/Spring Event; listener đặt trong `engagement.event`; chỉ dùng public contract của module khác.

Kết quả cần đạt: khi `ReviewService` lưu thành công một review hợp lệ, hệ thống phát một `ReviewCreatedEvent` có `eventId`. Listener chỉ xử lý event sau commit, dùng `merchant.api` để xác định sản phẩm và chủ shop, rồi tạo một notification chưa đọc cho đúng chủ shop. Rollback hoặc event ngoài transaction không tạo notification; phát lại cùng event không tạo bản ghi trùng.

## 2. Diễn giải acceptance criteria

Cụm “phát `ReviewCreated` ... sau commit” trong issue được triển khai theo quy ước thống nhất của SRS và code hiện tại:

1. Publisher phát event trong transaction tạo review, sau khi `reviews` đã được `saveAndFlush` thành công.
2. Listener dùng `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = false)` nên chỉ phản ứng sau khi transaction commit.
3. Không chuyển việc publish sang một callback tự viết sau commit, vì cách đó làm khó kiểm thử rollback và không khớp mẫu đang dùng cho shop/order notification.

## 3. Trạng thái baseline và phần đã hoàn tất

Đã có:

- Luồng tạo review và các kiểm tra `REV 01–03` trong `ReviewService`/`ReviewOrderApi`.
- Unique constraint `uk_reviews_order_item` cho review.
- Bảng `notifications`, unique constraint `uk_notifications_event_receiver` và `NotificationWriter` chạy `REQUIRES_NEW`.
- Các listener shop/order theo mẫu `AFTER_COMMIT`, bỏ qua event không hợp lệ và xử lý replay an toàn.
- Trang danh sách notification hiển thị dữ liệu chung từ `title`/`content`, nên không cần UI riêng cho review.
- `CartProductView` trong `merchant.api` đã cung cấp `productId`, `shopId`, `shopOwnerId`, `productName`; không cần mở rộng Merchant contract.

Đã bổ sung trong implementation:

- Public contract `ReviewCreatedEvent`.
- Publisher trong transaction tạo review.
- `NotificationType.REVIEW_CREATED`.
- Listener `AFTER_COMMIT` tạo notification cho chủ shop.
- Unit/integration test cho payload event, after-commit, rollback, replay và xác định receiver/product.

## 4. Ownership, dependency và public contract

### 4.1 Ownership

| Phần việc | Owner/reviewer |
| --- | --- |
| Publisher và contract review | Huỳnh Thiên Phúc — owner nghiệp vụ review |
| Listener, `notifications`, idempotency | Đỗ Đặng Diệu Linh — feature owner Notification/Observer |
| Merchant contract dùng để phân giải owner/product | Trần Thị Phương Trang review |
| Quyền và receiver user | Không cần đổi Identity contract; Doàn Trương Duy Khang review bảo mật nếu phạm vi thay đổi |

Mọi thay đổi đều nằm trong module `engagement`; `merchant` chỉ được gọi qua `MerchantApi`/`CartProductView`. Không import Entity, Repository hoặc Service nội bộ từ module khác.

### 4.2 Contract đề xuất

Đặt `ReviewCreatedEvent` tại `com.senvia.doangiuaky.engagement.api` với các trường tối thiểu:

```java
UUID eventId
Long reviewId
Long productId
short rating
Instant occurredAt
```

Quy ước:

- `eventId` được tạo mới đúng một lần cho lần tạo review thành công.
- `reviewId` và `productId` lấy từ review đã `saveAndFlush`, không lấy lại từ request chưa xác minh.
- `rating` lấy từ entity đã lưu.
- Event không mang `shopOwnerId` hoặc `productName`; listener lấy hai giá trị này từ `MerchantApi#findProductForCart(productId)` để Merchant tiếp tục là nguồn sự thật về product/shop ownership.
- `reference_id` của notification lưu `productId`, vì đối tượng mà shop cần nhận diện là sản phẩm vừa được review.

## 5. Luồng xử lý dự kiến

```text
Buyer gửi review
  → ReviewController
  → ReviewService.createReview()
      → ReviewOrderApi.requireReviewableItem(orderItemId, productId)
      → validate rating/comment/unique order item
      → ReviewRepository.saveAndFlush(review)
      → publish ReviewCreatedEvent(eventId, reviewId, productId, rating, occurredAt)
  → transaction commit
  → ReviewCreatedNotificationListener (AFTER_COMMIT)
      → MerchantApi.findProductForCart(productId)
      → lấy shopOwnerId và productName có thẩm quyền
      → NotificationWriter.create(..., REVIEW_CREATED, ..., referenceId=productId)
      → unique(eventId, receiverId) chặn replay
```

Nội dung notification đề xuất:

- Tiêu đề: `Sản phẩm của bạn có đánh giá mới`.
- Nội dung: `Sản phẩm <tên sản phẩm> vừa nhận được đánh giá <n> sao.`
- Receiver: `CartProductView.shopOwnerId()`.
- Trạng thái ban đầu: chưa đọc, dùng mặc định hiện có.

Không lọc theo `isSaleable()`: review hợp lệ có thể được tạo cho sản phẩm đã mua trước đó nhưng hiện đã hết hàng/ẩn hoặc shop bị khóa. Requirement yêu cầu thông báo cho chủ sở hữu của sản phẩm, không chỉ cho catalog đang bán.

## 6. Kế hoạch thay đổi theo file

### Bước 1 — Khai báo integration event

- Tạo `src/main/java/com/senvia/doangiuaky/engagement/api/ReviewCreatedEvent.java`.
- Dùng `record`, tài liệu hóa ý nghĩa trường và giữ contract không phụ thuộc entity nội bộ.
- Event không chứa comment hoặc dữ liệu buyer để tránh phát tán dữ liệu không cần thiết.

### Bước 2 — Phát event từ transaction tạo review

- Sửa `src/main/java/com/senvia/doangiuaky/engagement/service/ReviewService.java`.
- Inject `ApplicationEventPublisher`.
- Chỉ publish sau `saveAndFlush` thành công và sau khi xử lý được lỗi duplicate.
- Tạo payload từ entity đã lưu; không publish ở các nhánh validation, ownership, duplicate hoặc persistence failure.
- Giữ transaction hiện tại để rollback ngăn listener chạy.

### Bước 3 — Bổ sung loại notification

- Sửa `src/main/java/com/senvia/doangiuaky/engagement/entity/NotificationType.java` để thêm `REVIEW_CREATED`.
- Không cần migration: cột `notifications.type` là `VARCHAR(50)` và không có check constraint theo enum.

### Bước 4 — Tạo listener Observer

- Tạo `src/main/java/com/senvia/doangiuaky/engagement/event/ReviewCreatedNotificationListener.java`.
- Dùng `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = false)`.
- Validate event: event khác null; `eventId`/`occurredAt` khác null; `reviewId`, `productId` dương; rating 1–5.
- Gọi `MerchantApi#findProductForCart(productId)` và kiểm tra summary hợp lệ, `shopOwnerId` dương, `productName` có nội dung.
- Gọi `NotificationWriter` với `NotificationType.REVIEW_CREATED` và `referenceId = productId`.
- Khi vi phạm `uk_notifications_event_receiver`, coi là replay và không tạo trùng.
- Event/payload không hợp lệ hoặc không phân giải được product/owner phải được log không chứa dữ liệu xác thực; không làm hỏng transaction review đã commit.

Không tạo abstraction dùng chung cho logic nhận diện unique constraint trong issue này. Việc trùng lặp nhỏ với các listener hiện có được giữ để diff hẹp; refactor listener toàn module là task riêng nếu nhóm thống nhất.

### Bước 5 — Cập nhật test hiện có

- Sửa `src/test/java/com/senvia/doangiuaky/engagement/service/ReviewServiceTest.java`:
  - mock/capture `ApplicationEventPublisher`;
  - xác minh review hợp lệ phát đúng một event với `reviewId`, product và rating đã xác minh;
  - xác minh không phát event khi comment/rating sai, Ordering từ chối, review trùng hoặc lưu thất bại.
- Sửa `src/test/java/com/senvia/doangiuaky/engagement/service/ReviewReadEventIntegrationTest.java` để cấp dependency publisher mới và tiếp tục chứng minh GET danh sách review không phát event.

### Bước 6 — Thêm test listener và integration

- Tạo `src/test/java/com/senvia/doangiuaky/engagement/event/ReviewCreatedNotificationListenerTest.java`:
  - event hợp lệ dùng Merchant summary để gửi đúng owner;
  - nội dung chứa đúng tên product/rating và reference là product;
  - không dùng owner từ request/event;
  - bỏ qua event/payload/Merchant result không hợp lệ;
  - replay unique được xử lý idempotent; lỗi khác không tạo notification thứ hai.
- Tạo `src/test/java/com/senvia/doangiuaky/engagement/event/ReviewCreatedNotificationIntegrationTest.java`:
  - event trong transaction commit tạo đúng một notification chưa đọc;
  - transaction rollback không tạo notification;
  - publish ngoài transaction không tạo notification vì `fallbackExecution = false`;
  - publish lại cùng `eventId` cho cùng receiver vẫn chỉ có một bản ghi;
  - hai event khác nhau cho cùng product/receiver tạo hai notification độc lập.
- Nếu fixture hiện tại cho phép ổn định, thêm một integration test gọi trực tiếp `ReviewService#createReview` để chứng minh chuỗi save → publish → commit → notification. Nếu fixture yêu cầu seed chéo module quá lớn, giữ ranh giới bằng service test + listener integration test, không truy cập repository nội bộ của Ordering/Merchant.

## 7. Trường hợp kiểm thử và acceptance mapping

| Trường hợp | Kết quả mong đợi | Requirement |
| --- | --- | --- |
| Review hợp lệ, commit thành công | Một notification `REVIEW_CREATED` cho đúng `shopOwnerId` | NOTI 07, AT 08 |
| Product hết hàng/ẩn sau khi mua nhưng vẫn tồn tại | Vẫn thông báo cho owner; không yêu cầu `isSaleable()` | NOTI 07 |
| Review sai buyer/product, order chưa COMPLETED | Ordering từ chối; không lưu review, không phát event | REV 01, AT 10 |
| Rating ngoài 1–5 hoặc comment quá dài | Không lưu và không phát event | REV 02 |
| Trùng `order_item_id` | HTTP 409; không phát event lần hai | REV 03, BR 26 |
| Transaction tạo review rollback | Không có notification | BR 25 |
| Event phát ngoài transaction | Listener không chạy | BR 25 |
| Phát lại cùng `(eventId, receiverId)` | Chỉ một notification | BR 25, AT 08 |
| Merchant không tìm thấy product/owner | Bỏ qua có log an toàn, không gửi sai người | Ownership/backend validation |
| Đọc danh sách review công khai | Không phát event | REV 04, giới hạn side effect |

## 8. Phạm vi file đã triển khai

### Tạo mới

- `src/main/java/com/senvia/doangiuaky/engagement/api/ReviewCreatedEvent.java`
- `src/main/java/com/senvia/doangiuaky/engagement/event/ReviewCreatedNotificationListener.java`
- `src/test/java/com/senvia/doangiuaky/engagement/event/ReviewCreatedNotificationListenerTest.java`
- `src/test/java/com/senvia/doangiuaky/engagement/event/ReviewCreatedNotificationIntegrationTest.java`

### Chỉnh sửa

- `src/main/java/com/senvia/doangiuaky/engagement/service/ReviewService.java`
- `src/main/java/com/senvia/doangiuaky/engagement/entity/NotificationType.java`
- `src/test/java/com/senvia/doangiuaky/engagement/service/ReviewServiceTest.java`
- `src/test/java/com/senvia/doangiuaky/engagement/service/ReviewReadEventIntegrationTest.java`

### Không thay đổi

- Migration/schema hiện tại.
- `pom.xml`, `application.properties`, Security config.
- Template/static/common fragment.
- Source nội bộ của `merchant`, `ordering`, `identity`.
- SRS, vì hành vi dự kiến đã được đặc tả đầy đủ và kế hoạch không thay đổi requirement.

## 9. Thứ tự thực hiện và điểm kiểm soát

1. Phúc và Linh review/chốt `ReviewCreatedEvent` và ý nghĩa `referenceId = productId` trước khi code.
2. Thêm event contract và publisher; chạy unit test `ReviewServiceTest` và test GET không phát event.
3. Thêm enum/listener; chạy listener unit test.
4. Thêm integration test commit/rollback/replay.
5. Chạy toàn bộ test module Engagement, sau đó full test project.
6. Kiểm tra dependency bằng tìm import chéo module và kiểm tra diff.
7. PR liên kết issue #75, ghi rõ test đã chạy; Phúc review publisher/contract, Linh review listener/idempotency, Trang review cách dùng Merchant API.

## 10. Lệnh xác minh

```powershell
.\mvnw.cmd -Dtest=ReviewServiceTest,ReviewReadEventIntegrationTest,ReviewCreatedNotificationListenerTest,ReviewCreatedNotificationIntegrationTest test
.\mvnw.cmd test
git diff --check
git status --short
```

Kiểm tra thủ công bổ sung:

1. Hoàn tất một order của buyer.
2. Buyer tạo review hợp lệ.
3. Đăng nhập chủ shop và mở `/account/notifications`.
4. Xác minh có đúng một notification chưa đọc, nội dung đúng product/rating.
5. Đánh dấu đã đọc và xác minh ownership/read state hiện có vẫn hoạt động.

## 11. Definition of Done cho Issue #75

- `ReviewCreatedEvent` là public contract nhỏ, không lộ entity/buyer/comment.
- Event chỉ phát sau khi review lưu thành công; listener chỉ chạy sau commit.
- Merchant là nguồn sự thật để xác định product và shop owner.
- Notification đúng receiver, type, nội dung, reference và trạng thái chưa đọc.
- Rollback, publish ngoài transaction và replay đều không tạo notification sai/trùng.
- Các nhánh validation, authorization và duplicate không phát event.
- Không có import nội bộ chéo module; không sửa migration/config/UI ngoài phạm vi.
- Test mục tiêu và full test chạy thành công, hoặc blocker môi trường được ghi rõ trong PR.
- `git diff --check` sạch; không có secret, `target/` hoặc file IDE trong diff.

## 12. Kết quả xác minh sau triển khai

- Targeted: `ReviewServiceTest`, `ReviewReadEventIntegrationTest`, `ReviewCreatedNotificationListenerTest`, `ReviewCreatedNotificationIntegrationTest` — đạt 22/22 test.
- Full suite: `.\mvnw.cmd test` — đạt 775/775 test.
- `git diff --check` — sạch.
