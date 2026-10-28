# Hướng dẫn module Shopping

[← Playbook](../DEVELOPMENT_PLAYBOOK.md) · [Phân công](../PHAN_CONG_CONG_VIEC.md)

## Phạm vi

- **Chủ sở hữu:** Đỗ Đặng Diệu Linh (2312663).
- **Requirement:** CART 01–07, FAV 01–03.
- **Phối hợp:** giao diện/query Buyer Order History cho ORDER 01 qua `ordering.api`; Linh đồng thời phụ trách feature Notification/Observer trong module `engagement` theo phân công nhóm.
- **Bảng:** `carts`, `cart_items`, `favorites`.
- **Code/view:** `com.senvia.doangiuaky.shopping`, `templates/shopping`.

## Dependency và public contract

- Dùng `identity.api` cho user hiện tại và role.
- Dùng `merchant.api` cho product/shop, giá, stock và khả năng bán.
- Add/update cart khóa theo thứ tự `cart → product`; sau khi khóa cart, gọi `MerchantApi#findProductForCartMutation` trong cùng transaction để đọc stock authoritative và giữ product row lock đến commit.
- Dùng `ordering.api` cho lịch sử/chi tiết/cancel order của buyer.
- Chi tiết đơn `COMPLETED` hiển thị nút viết review theo từng item, truyền `orderItemId` và `productId` tới form Engagement.
- Cung cấp `shopping.api`: checkout snapshot và xóa item sau checkout thành công.
- Không tạo Order, Payment hoặc cập nhật stock.

### Contract checkout cho Ordering

`shopping.api.CheckoutCartItem` gồm `Long cartItemId`, `Long productId`, `int quantity`.
`shopping.api.CheckoutCartApi` cung cấp `List<CheckoutCartItem> getCheckoutSnapshot()`
và `void removePurchasedItems(List<CheckoutCartItem> purchasedItems)`.
Contract không nhận `buyerId`; Shopping lấy USER hiện tại qua `identity.api.CurrentUserApi`,
kiểm tra USER ACTIVE và quyền sở hữu giỏ. Vì vậy client hoặc Ordering không thể chọn buyer mục tiêu.

Hai phương thức yêu cầu transaction checkout đang mở (`Propagation.MANDATORY`) và
khóa hàng cart đến hết transaction. Mọi thao tác sửa giỏ phải dùng cùng khóa cart.
Snapshot trả mọi cart item để Ordering kiểm tra lại giá, trạng thái và tồn kho qua
`merchant.api`. Xóa chỉ nhận các item hợp lệ, không trùng, còn thuộc giỏ và khớp
product/quantity; lỗi làm rollback checkout. Ordering gọi xóa sau khi tạo order/payment,
trừ kho thành công và trước commit.

Trên trang giỏ, mỗi item hợp lệ có ô chọn thanh toán; item lỗi vẫn hiện cảnh báo
nhưng ô chọn bị vô hiệu hóa. Shopping gửi các `cartItemId` được chọn tới Ordering.
Ordering đối chiếu chúng với snapshot giỏ thuộc buyer hiện tại và kiểm tra lại
khả năng bán trước khi hiển thị hoặc xử lý thanh toán. Item không được chọn vẫn ở giỏ.

## Thứ tự feature

1. Migration và mapping cart/cart item/favorite.
2. Tạo/lấy một cart cho user.
3. Thêm/cộng dồn/cập nhật/xóa cart item.
4. Nhóm theo shop, subtotal giá hiện tại và cảnh báo trước checkout.
5. Favorite thêm/xóa/danh sách/chống trùng.
6. Checkout snapshot và command xóa item.
7. Buyer Order History qua `ordering.api`.
8. Integration test Buyer Flow.

## Quy tắc đặc thù

- Một user một cart; một product một dòng trong cart.
- Quantity > 0 và không vượt stock hiện tại.
- Cart không lưu giá; subtotal luôn đọc giá hiện tại.
- Admin không mua; owner không mua product của chính shop.
- Không đọc/sửa cart hoặc favorite người khác.
- Favorite chống trùng ở Service và database.
- Cart chỉ bị xóa sau khi Ordering báo checkout thành công.
- Buyer Order UI không truy cập Order Entity/Repository/Service nội bộ.

## Kiểm thử/bàn giao

Test cart/favorite ownership, unique constraint, price/stock/status thay đổi, snapshot checkout và Buyer Order History. Chạy integration flow Product → Cart → Checkout → Order → Cancel → Stock restored. Lê Anh Khoa review checkout/order contract; Trần Thị Phương Trang review product/shop contract.

Checklist chất lượng chung xem [CONTRIBUTING.md](../CONTRIBUTING.md).
