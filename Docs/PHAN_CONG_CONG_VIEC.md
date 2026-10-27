# PHÂN CÔNG CÔNG VIỆC CHI TIẾT THEO MODULE

## 1. Thông tin chung

- **Dự án:** Website Sàn thương mại điện tử
- **Nhóm:** 2
- **Nhóm trưởng:** Trần Thị Phương Trang (2314288)
- **Môn học:** Mẫu thiết kế

Tài liệu này là nguồn chi tiết về đầu việc, contract, kiểm thử và bàn giao của từng thành viên. Requirement và acceptance criteria lấy theo [SRS.md](SRS.md); ownership lấy theo [MODULE_OWNERSHIP.md](MODULE_OWNERSHIP.md); tiêu chuẩn hoàn thành lấy theo [CONTRIBUTING.md](CONTRIBUTING.md).

## 2. Thành viên và phạm vi

| TV | MSSV | Họ và tên | Phạm vi chính |
| --- | --- | --- | --- |
| TV1 | 2314288 | Trần Thị Phương Trang — **Nhóm trưởng** | `merchant`, State Pattern, shop, category, product, stock; feature AUTH 08 trong `identity` |
| TV2 | 2312647 | Lê Anh Khoa | `ordering`, checkout, payment, Strategy, order lifecycle |
| TV3 | 2312663 | Đỗ Đặng Diệu Linh | `shopping`, thông báo/Observer trong `engagement`, cart, favorite, Buyer Order History |
| TV4 | 2111844 | Doàn Trương Duy Khang | Chủ module `identity`, `common`, Security và AUTH 01–07; review feature AUTH 08 của Trang |
| TV5 | 2113010 | Huỳnh Thiên Phúc | Review, dashboard và Admin order view trong `engagement` |

---

## 3. TV1 — Trần Thị Phương Trang: Merchant

### Phạm vi

- **Requirement:** SHOP 01–10, PROD 01–11, AUTH 08; dữ liệu cho STAT 01.
- **Bảng:** `shops`, `categories`, `products`, `product_images`.
- **Guide:** [MERCHANT_GUIDE.md](modules/MERCHANT_GUIDE.md).

### Công việc

| Nhóm | Đầu việc và kết quả |
| --- | --- |
| Shop request | User tạo tối đa một shop; nhập tên, mô tả, logo, điện thoại, địa chỉ; yêu cầu mới ở `PENDING`; xem trạng thái; sửa và gửi lại `REJECTED` |
| Admin shop | Danh sách/chi tiết yêu cầu; duyệt, từ chối có lý do; khóa có lý do, người khóa, thời điểm; mở khóa |
| State Pattern | `ShopState`, Pending/Approved/Rejected/Locked State và factory; đóng gói quyền sửa catalog, nhận order, gửi lại yêu cầu và xử lý order cũ |
| Category | Admin thêm/sửa/ẩn; category ẩn không dùng cho product mới; product cũ không công khai nhưng owner vẫn quản lý/chuyển category |
| Product | Tạo/sửa/ẩn đúng ownership; giá > 0, stock >= 0; trạng thái `ACTIVE/HIDDEN`; ít nhất một ảnh |
| Cloudinary | Upload/thay thế/xóa logo và ảnh; lưu URL/`public_id`; xử lý bù khi upload hoặc lưu database lỗi |
| Catalog | Danh sách, chi tiết, tìm tên, lọc category/khoảng giá; phân trang khi cần |
| Điều kiện bán | Chỉ bán khi owner ACTIVE, shop APPROVED, category active, product ACTIVE và stock > 0 |
| Stock | Đọc giá/stock; conditional update nguyên tử; hoàn kho theo cơ chế chống lặp thống nhất với TV2 |
| Event | Phát event gửi/duyệt/từ chối shop có `eventId` |
| AUTH 08 (Issue #68) | Trong module `identity`, làm Admin list/search/filter/detail và khóa/mở USER; lưu lý do/người/thời điểm; không tự khóa hoặc khóa ADMIN; Khang review theo ownership Identity |

### Merchant API phải cung cấp

- Product/shop summary tối thiểu.
- Giá, stock, shop owner và khả năng bán.
- Trừ/hoàn stock.
- Tổng product của đúng shop cho dashboard.
- Dữ liệu product/shop cho cart, favorite, order và review.

### Kiểm thử và bàn giao

- State transition và hành vi từng State.
- Một user tối đa một shop; lý do từ chối/khóa bắt buộc.
- Shop/owner không hợp lệ không sửa catalog hoặc nhận order mới.
- Ownership product, giá, stock, category và ảnh.
- Điều kiện catalog công khai.
- Concurrent stock deduction và hoàn stock không lặp.
- Cloudinary/database failure.
- Contract được TV2 review về stock và TV5 review về event.

---

## 4. TV2 — Lê Anh Khoa: Ordering

### Phạm vi

- **Requirement:** PAY 01–08, ORDER 02–09; API hỗ trợ TV3 thực hiện UI/query ORDER 01.
- **Dữ liệu:** STAT 02–04 và xác minh Review.
- **Bảng:** `orders`, `order_items`, `payments`, `order_status_histories`.
- **Guide:** [ORDERING_GUIDE.md](modules/ORDERING_GUIDE.md).

### Công việc

| Nhóm | Đầu việc và kết quả |
| --- | --- |
| Checkout | Nhận snapshot từ TV3; đọc lại product từ TV1; kiểm tra account/shop/category/product/giá/stock; không cho Admin hoặc owner mua product của chính shop |
| Multi-shop | Nhóm theo shop, tạo một order/payment mỗi shop, snapshot tên/giá bằng `BigDecimal` |
| Transaction | All-or-nothing; lỗi bất kỳ product/shop/bước nào rollback order, payment, stock và cart |
| Price change | Dừng checkout và yêu cầu buyer xác nhận lại khi giá thay đổi sau màn hình xác nhận |
| Strategy | `PaymentStrategy`, COD, Bank Transfer, factory và result |
| COD | Checkout tạo `COD_PENDING`; order COMPLETED chuyển payment sang `PAID` |
| Bank transfer | Checkout tạo `PENDING` và reference; shop xác nhận `PAID` trước khi order được `CONFIRMED` |
| Seller order | Danh sách/chi tiết đúng shop; chuyển PENDING → CONFIRMED → PREPARING → SHIPPING → COMPLETED; ghi history |
| Cancel | Buyer/shop/Admin chỉ hủy PENDING/CONFIRMED/PREPARING; lý do trim không rỗng, tối đa 500; hoàn stock/payment đúng một lần |
| Event | Phát OrderCreated/OrderStatusChanged sau commit, có `eventId` |
| Statistics | Số order theo trạng thái và doanh thu COMPLETED của đúng shop |

### Ordering API phải cung cấp

- Buyer/Admin order list, detail và cancel command.
- Order item, payment và history cho Buyer Order History.
- Xác minh order item COMPLETED, buyer và product cho Review.
- Statistics theo shop.
- Order event cho Engagement.

### Kiểm thử và bàn giao

- Checkout nhiều shop, rollback toàn bộ và giá thay đổi.
- COD/Bank Transfer Strategy và factory.
- Payment/order transition hợp lệ và không cập nhật lặp.
- Cancel theo ba actor; lý do, ownership, hoàn stock/refund đúng một lần.
- Transaction/concurrency và doanh thu đúng shop.
- Contract cart được TV3 review, stock được TV1 review, event/statistics được TV5 review.

---

## 5. TV3 — Đỗ Đặng Diệu Linh: Shopping, Notification/Observer và Buyer Order History

### Phạm vi

- **Requirement:** CART 01–07, FAV 01–03, NOTI 01–07 và giao diện/query ORDER 01.
- **Bảng:** `carts`, `cart_items`, `favorites`; bảng `notifications` thuộc phần feature Linh phụ trách trong `engagement`.
- **Guide:** [SHOPPING_GUIDE.md](modules/SHOPPING_GUIDE.md).

### Công việc

| Nhóm | Đầu việc và kết quả |
| --- | --- |
| Cart | Một cart/user; thêm, cộng dồn, cập nhật, xóa item; quantity > 0 và không vượt stock |
| Validation | Không thêm product không bán được; Admin không mua; owner không mua product của chính shop; không truy cập cart người khác |
| Giá | Cart không lưu giá; subtotal luôn tính bằng giá hiện tại từ TV1; cảnh báo khi giá/stock/trạng thái thay đổi |
| Hiển thị | Nhóm cart item theo shop; cảnh báo product/category/shop/owner không hợp lệ trước checkout |
| Favorite | Thêm, xóa, danh sách; chống trùng ở Service/database; giới hạn đúng owner |
| Shopping API | Checkout snapshot gồm cart item/product/quantity; xóa đúng item chỉ sau khi TV2 báo thành công |
| Buyer orders | Danh sách/chi tiết order, items, payment, history; nút/form cancel theo trạng thái; gọi `ordering.api`, không tự xử lý order |
| Observer/Notification | Listener trong `engagement.event` nhận ShopRequested/ShopReviewed, OrderCreated/OrderStatusChanged và ReviewCreated; tạo thông báo đúng receiver sau commit, chống trùng theo eventId; danh sách, unread count, đánh dấu đã đọc một/tất cả và kiểm tra ownership |

### Kiểm thử và bàn giao

- Cart unique, cộng dồn quantity, stock và giá hiện tại.
- Product hết hàng/ẩn, category/shop/owner bị khóa.
- Favorite trùng và truy cập trái quyền.
- Snapshot checkout và chỉ xóa cart sau thành công.
- Buyer không xem order người khác.
- Listener xử lý sau commit; event lặp không tạo thông báo trùng; receiver và read state đúng ownership.
- Integration flow: Product → Cart → Checkout → Order → Cancel → Stock restored.
- Contract product/shop được TV1 review; checkout/order contract được TV2 review; event publisher contract được module phát event sở hữu và TV5 review phần tương tác với Review.

---

## 6. TV4 — Doàn Trương Duy Khang: Identity, Security và Common

### Phạm vi

- **Requirement:** AUTH 01–07; review kỹ thuật AUTH 08 do Trang thực hiện.
- **Bảng:** `users`.
- **Tài nguyên:** `identity`, `common`, config và shared UI.
- **Guide:** [IDENTITY_GUIDE.md](modules/IDENTITY_GUIDE.md).

### Công việc

| Nhóm | Đầu việc và kết quả |
| --- | --- |
| User schema | User, role/status; email trim/lowercase và unique `LOWER(email)`; lock reason/by/at |
| Registration | Validation họ tên/email/password; BCrypt; chống email trùng không phân biệt hoa thường |
| Authentication | Login/logout; chặn LOCKED; vô hiệu session hiện tại khi account bị khóa; role USER/ADMIN; Admin không mua |
| Profile | Xem/sửa họ tên, phone, address, avatar; đổi password sau khi xác minh password hiện tại |
| Locked owner | Identity API phản ánh ngay LOCKED để catalog ẩn và shop ngừng nhận order; Admin xử lý order cũ |
| Common | Security config, password encoder, access denied, global exception, validation thật sự dùng chung, trang 403/404/500, header/footer/navigation |
| Shared review | Review `pom.xml`, `application.properties`, `common`, fragment và static common |

### Identity API phải cung cấp

- User ID, tên, role, account status.
- Kiểm tra user tồn tại/ACTIVE.
- Danh sách Admin ACTIVE phục vụ notification.

Không công bố password hash, User Entity/Repository hoặc principal nội bộ.

### Kiểm thử và bàn giao

- Email sai/trùng/khác hoa thường và chuẩn hóa.
- BCrypt, sai password, account LOCKED/session invalidation.
- Profile/password ownership.
- USER/ADMIN authorization và Admin không mua.
- Identity contract và Security; review khóa/mở USER của AUTH 08 do Trang thực hiện.
- Public DTO/API không lộ password hash.
- Identity contract được TV1 và TV5 review theo nhu cầu sử dụng.

---

## 7. TV5 — Huỳnh Thiên Phúc: Review, Dashboard và Admin Order trong Engagement

### Phạm vi

- **Requirement:** REV 01–04, STAT 01–05.
- **Giao diện phối hợp:** Admin giám sát order.
- **Bảng:** `reviews`; bảng `notifications` do Linh phụ trách trong cùng module.
- **Guide:** [ENGAGEMENT_GUIDE.md](modules/ENGAGEMENT_GUIDE.md).

### Công việc

| Nhóm | Đầu việc và kết quả |
| --- | --- |
| Review | Tạo và kiểm tra review: chỉ buyer có order item COMPLETED đúng product; rating 1–5; unique order item; không sửa/xóa MVP |
| Review view/event | Danh sách review và điểm trung bình công khai, DTO không lộ buyer/order-item ID, empty state rõ ràng, GET không ghi dữ liệu/phát event; link từ chi tiết sản phẩm; phối hợp Linh để kiểm tra listener nhận ReviewCreatedEvent |
| Dashboard | Tổng product từ TV1; order/status/revenue COMPLETED từ TV2; chỉ dữ liệu đúng shop; thẻ số/bảng là đủ |
| Admin order | Danh sách toàn hệ thống, tìm code, lọc buyer/shop/status, chi tiết, payment, history, form cancel |
| Cancel orchestration | Admin UI gọi `ordering.api`; TV2 thực hiện validation, stock, payment và history |

### Kiểm thử và bàn giao

- Review buyer/order/product/rating/unique.
- Điểm trung bình.
- Dashboard đúng shop và revenue COMPLETED.
- Admin filter/detail/cancel qua API.
- Không import Entity/Repository nội bộ module khác.
- TV4 review quyền; TV1/TV2 review contract dữ liệu.

---

## 8. Ma trận tích hợp

| Bên cung cấp | Contract | Bên sử dụng | Review |
| --- | --- | --- | --- |
| TV1 `merchant` | Product/shop, giá/stock, trừ/hoàn stock, product count, shop events | TV2, TV3, TV5; TV3 notification listener nhận shop events | TV2 stock; TV3 cart/notification; TV5 dashboard/review |
| TV2 `ordering` | Buyer/Admin query, cancel, review verification, statistics, order events | TV3, TV5; TV3 notification listener nhận order events | TV3 Buyer Flow/notification; TV5 Admin/review/dashboard |
| TV3 `shopping` | Checkout snapshot và xóa cart sau thành công | TV2 | TV2 |
| TV4 `identity` | User/role/status và Admin ACTIVE | TV1, TV2, TV3, TV5 | TV1 và TV5 |
| TV1/TV2/TV5 | Integration event | TV3 notification listener; TV5 dùng ReviewCreated cho nghiệp vụ review | Chủ module phát event review contract của mình; TV5 review event ReviewCreated, Linh kiểm tra luồng thông báo |

TV3 chỉ làm UI/query Buyer Order History qua `ordering.api`; nghiệp vụ order thuộc TV2. TV5 chỉ làm Admin orchestration; nghiệp vụ hủy order thuộc TV2.

## 9. Trình tự phối hợp

1. TV4 công bố Identity API; TV1 công bố Merchant API nền tảng.
2. TV3 hoàn thiện cart/favorite bằng mock Merchant API.
3. TV2 hoàn thiện Ordering/Strategy bằng mock Shopping và Merchant API.
4. Tích hợp Cart → Checkout → Stock → Order → Cancel.
5. TV3 tích hợp notification listener với event từ các module; TV5 hoàn thiện Review, Dashboard và Admin order view.
6. Chạy kịch bản demo SRS và kiểm thử phân quyền toàn hệ thống.

## 10. Bàn giao chung

Mỗi thành viên bàn giao code, migration, test, UI và tài liệu thuộc phạm vi của mình; ghi rõ test đã chạy và blocker còn lại. Definition of Done và checklist review áp dụng theo [CONTRIBUTING.md](CONTRIBUTING.md); branch/commit/PR áp dụng theo [GIT_WORKFLOW.md](GIT_WORKFLOW.md).
