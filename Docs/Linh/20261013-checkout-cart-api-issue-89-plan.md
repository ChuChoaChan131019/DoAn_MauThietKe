# Kế hoạch triển khai issue #89 — Checkout snapshot và cart cleanup API

Ngày lập: 13/10/2026
Issue: [#89 — Checkout snapshot và cart cleanup API](https://github.com/ChuChoaChan131019/MauThietKe_DoAnGiuaKy/issues/89)
Parent issue: #10
Module sở hữu: `shopping`
Bảng sở hữu: `carts`, `cart_items`
Người triển khai: Đỗ Đặng Diệu Linh
Người review contract/transaction: Lê Anh Khoa

## 1. Mục tiêu và căn cứ đặc tả

Shopping công bố contract tối thiểu để Ordering lấy snapshot giỏ và xóa đúng các mục đã checkout mà không truy cập `Cart`, `CartItem`, Repository hoặc Service nội bộ của Shopping.

Kế hoạch bám theo các nguồn chuẩn sau:

- `PAY 02`, `PAY 06`: checkout nhiều shop là all-or-nothing; chỉ xóa mục đã mua sau khi tạo order/payment và trừ kho thành công.
- `UC 06 — Đặt hàng`: Ordering phải kiểm tra lại owner, shop, category, product, giá và stock; lỗi tạo order/payment, trừ kho hoặc xóa cart phải rollback toàn bộ.
- `BR 19`, `NFR 06`, `AT 06`: order, payment, stock và cart tham gia cùng một transaction và rollback cùng nhau.
- `CART 05`, `CART 07`, `BR 21`: cart không lưu giá; giá hiện tại không thuộc snapshot checkout của Shopping và phải được Ordering đọc lại từ Merchant.
- `BR 23`, `AT 12`: Admin không mua hàng; kiểm tra quyền và ownership phải nằm ở backend.
- Cấu trúc `carts`, `cart_items` trong mục 7.1.6–7.1.7 của SRS: một cart/user, item định danh bằng `id`, thuộc `cart_id`, chứa `product_id` và `quantity`.
- `Docs/modules/SHOPPING_GUIDE.md`: contract chuẩn gồm `CheckoutCartItem` và `CheckoutCartApi`; cả hai thao tác dùng `Propagation.MANDATORY`, khóa cart đến cuối transaction và cleanup chạy sau các bước checkout thành công nhưng trước commit.
- `Docs/ARCHITECTURE.md` và `Docs/MODULE_OWNERSHIP.md`: `ordering` chỉ phụ thuộc `shopping.api`; Shopping sở hữu `carts`, `cart_items` và việc xác minh ownership của cart.

## 2. Actor, luồng và public contract

### Actor và tiền điều kiện

- Actor nghiệp vụ là USER đang đăng nhập, có account `ACTIVE`.
- `buyerId` phải do Ordering lấy từ principal đã xác thực, không lấy từ request/form do client tự gửi.
- Ordering phải mở transaction checkout trước khi gọi Shopping API.
- ADMIN, account không tồn tại/LOCKED hoặc ID không hợp lệ phải bị từ chối ở backend.

### Contract phải công bố

```java
public record CheckoutCartItem(Long cartItemId, Long productId, int quantity) {}

public interface CheckoutCartApi {
    List<CheckoutCartItem> getCheckoutSnapshot(Long buyerId);
    void removePurchasedItems(Long buyerId, List<CheckoutCartItem> purchasedItems);
}
```

Contract chỉ chứa `cartItemId`, `productId`, `quantity`; không chứa Entity, giá, stock, trạng thái bán hoặc Repository/Service nội bộ. Ordering có thể mock interface và record này mà không khởi động Shopping/database.

### Luồng thành công

1. Ordering mở một transaction checkout và truyền `buyerId` lấy từ principal.
2. `getCheckoutSnapshot` xác minh buyer là USER ACTIVE, khóa pessimistic cart của buyer và trả toàn bộ item theo thứ tự ổn định.
3. Ordering dùng `productId`/`quantity` để đọc lại dữ liệu chuẩn từ `merchant.api`, kiểm tra giá/stock/trạng thái, nhóm theo shop, tạo order/payment và trừ stock.
4. Sau khi tất cả bước trên thành công, Ordering gọi `removePurchasedItems` trong cùng transaction với đúng snapshot/item đã mua.
5. Shopping kiểm tra toàn bộ command trước khi xóa: ID hợp lệ, không trùng `cartItemId`, item còn thuộc cart của buyer và `productId`/`quantity` vẫn khớp.
6. Shopping xóa đúng các item được truyền; item khác trong cart được giữ nguyên. Transaction chỉ commit khi toàn bộ checkout thành công.

### Luồng lỗi và hậu điều kiện

- Cart chưa tồn tại hoặc rỗng: snapshot trả danh sách rỗng để Ordering dừng checkout phù hợp.
- Cleanup null/rỗng, ID không hợp lệ hoặc trùng: từ chối, không xóa item nào.
- Item thuộc buyer khác, đã biến mất, đổi product hoặc quantity: từ chối toàn bộ cleanup để checkout rollback.
- Lỗi order/payment/stock/cart cleanup: rollback order, payment, stock và mọi thay đổi cart trong cùng transaction.
- Request cleanup lặp không được xóa nhầm item mới hoặc item khác. Theo `SHOPPING_GUIDE`, command stale/missing phải thất bại an toàn thay vì được coi là thành công idempotent.

## 3. Điểm cần thống nhất trước khi sửa

### 3.1 Ý nghĩa “buyer hiện tại”

Public API hiện nhận một `buyerId`, không nhận `actorId` riêng và không có contract Identity để đọc principal. Vì vậy ranh giới trách nhiệm cần được review/chốt như sau:

- Ordering chịu trách nhiệm lấy `buyerId` từ authenticated principal và không expose tham số buyer tùy ý ra HTTP.
- Shopping xác minh `buyerId` tồn tại, là USER ACTIVE; chỉ query cart theo `carts.user_id = buyerId`; cleanup chỉ chấp nhận item thuộc cart đó.
- Test consumer của Ordering phải chứng minh request không thể checkout bằng ID user khác. Test provider của Shopping phải chứng minh không đọc/xóa item thuộc cart khác.

Nếu reviewer yêu cầu Shopping tự đối chiếu principal thay vì tin precondition trên, phải bổ sung/chốt một public contract phù hợp với Identity trước; không import `identity.security` hoặc principal nội bộ sang Shopping.

### 3.2 Ý nghĩa “chống request lặp”

Issue dùng cụm “chống request lặp”, còn `SHOPPING_GUIDE` yêu cầu item phải “còn thuộc giỏ và khớp product/quantity; lỗi làm rollback checkout”. Kế hoạch áp dụng fail-safe:

- Trùng `cartItemId` trong cùng command bị từ chối trước khi xóa.
- Gọi lại cleanup sau lần xóa đã commit là command stale và bị từ chối, nhưng không xóa thêm dữ liệu nào.
- Không biến cleanup thành “missing item = success”, vì điều đó che giấu snapshot stale và trái contract trong guide.

Lê Anh Khoa cần xác nhận cách hiểu này khi review contract. Nếu muốn idempotent-success qua nhiều transaction, cần một checkout/idempotency key có nơi lưu trạng thái; đó là thay đổi contract/schema ngoài phạm vi hiện tại và phải tách issue.

## 4. Kế hoạch triển khai theo vertical slice

### Bước 1 — Chốt contract với Ordering

- Review chữ ký, DTO, precondition transaction và semantics lỗi với owner Ordering.
- Ghi rõ snapshot không phải nguồn chuẩn cho giá/stock/saleability.
- Xác nhận thứ tự gọi: snapshot → Merchant validation/stock → order/payment → cleanup → commit.
- Tạo test mock phía Ordering để chứng minh consumer chỉ phụ thuộc `shopping.api`.

### Bước 2 — Hoàn thiện provider Shopping

- Đặt interface/record trong `shopping.api`; giữ DTO immutable và không lộ entity.
- Service triển khai contract trong `shopping.service`; dùng `@Transactional(propagation = MANDATORY)` cho cả snapshot và cleanup.
- Xác minh buyer qua `identity.api`, không import implementation/security nội bộ của Identity.
- Dùng query khóa pessimistic theo `user_id`; giữ lock đến khi transaction checkout kết thúc.
- Validate toàn bộ danh sách cleanup trước khi gọi delete để tránh xóa một phần.
- Flush delete trong transaction để lỗi constraint/database xảy ra trước commit và kích hoạt rollback checkout.

### Bước 3 — Đồng bộ mọi thao tác sửa cart với cùng khóa

- Rà soát add/update/remove item hiện có đều khóa cart bằng cùng cơ chế `findByUserIdForUpdate` trước khi sửa.
- Không thêm khóa JVM vì không bảo vệ được nhiều instance.
- Thêm test concurrency giữa checkout snapshot và thao tác sửa cart để chứng minh edit bị chặn đến khi checkout kết thúc.

### Bước 4 — Kiểm thử provider/contract

1. Snapshot đúng ba trường, đúng item và thứ tự; không có giá/stock.
2. Cart không tồn tại/rỗng trả empty snapshot.
3. ADMIN, LOCKED, user không tồn tại và ID không hợp lệ bị từ chối.
4. Cleanup chỉ xóa item được chọn, giữ item chưa checkout.
5. Item của cart khác, ID không tồn tại, product/quantity thay đổi bị từ chối và không xóa một phần.
6. Input null/rỗng, phần tử null, ID/quantity không hợp lệ và `cartItemId` trùng bị từ chối.
7. Hai API gọi ngoài transaction ném lỗi do `MANDATORY`.
8. Rollback transaction sau cleanup phục hồi cart item.
9. Lỗi cleanup sau các thay đổi khác trong transaction rollback toàn bộ phần cart; integration phía Ordering chứng minh cả order/payment/stock cũng rollback.
10. Gọi lặp command đã commit thất bại an toàn, không tác động item khác hoặc item mới.
11. Snapshot giữ cart lock đến hết transaction; concurrent edit chỉ tiếp tục sau commit/rollback.
12. Contract có thể mock độc lập trong unit test Ordering và không kéo dependency nội bộ Shopping.

### Bước 5 — Tích hợp và bàn giao

- Ordering tích hợp chỉ qua `CheckoutCartApi`; tuyệt đối không import Shopping Entity/Repository/Service.
- Chạy test provider Shopping, test consumer Ordering và integration flow nhiều shop.
- Review diff để bảo đảm không sửa `pom.xml`, `application.properties`, common asset hoặc schema nếu không có yêu cầu mới.
- PR liên kết issue #89 và parent #10, mô tả contract, semantics request lặp, transaction boundary và test đã chạy.

## 5. Phạm vi file dự kiến

### File thuộc Shopping

- `src/main/java/com/senvia/doangiuaky/shopping/api/CheckoutCartApi.java`
- `src/main/java/com/senvia/doangiuaky/shopping/api/CheckoutCartItem.java`
- `src/main/java/com/senvia/doangiuaky/shopping/service/CheckoutCartService.java`
- `src/main/java/com/senvia/doangiuaky/shopping/repository/CartRepository.java`
- `src/main/java/com/senvia/doangiuaky/shopping/service/CartService.java` và các service mutation cart khác, chỉ khi cần đồng bộ cơ chế khóa
- `src/test/java/com/senvia/doangiuaky/shopping/service/CheckoutCartServiceTest.java` nếu tách unit/contract test
- `src/test/java/com/senvia/doangiuaky/shopping/service/CheckoutCartServiceIntegrationTest.java`
- Các test mutation/concurrency hiện có của Shopping nếu hành vi khóa thay đổi

### File phối hợp nhưng không do Linh tự sửa ngoài contract đã review

- Unit test Checkout/Ordering mock `CheckoutCartApi`.
- Integration test Ordering cho transaction order/payment/stock/cart.

Không dự kiến migration, controller, view, static asset, `pom.xml` hoặc `application.properties`. Nếu phát sinh nhu cầu idempotency key/schema mới, phải dừng và tách phạm vi để review.

## 6. Đối chiếu implementation hiện tại

Nhánh `feature/shopping/10-checkout-cart-api` đã có `CheckoutCartApi`, `CheckoutCartItem`, `CheckoutCartService`, query khóa cart và `CheckoutCartServiceIntegrationTest`. Trước khi coi issue hoàn tất cần:

- Review lại precondition “buyerId từ principal” với Ordering và bổ sung test consumer chống checkout chéo user.
- Bổ sung test buyer ADMIN/LOCKED/không tồn tại cho provider.
- Bổ sung test request cleanup lặp sau commit để khóa rõ semantics fail-safe.
- Xác minh mọi mutation cart đều dùng cùng khóa; hiện luồng add đã dùng `findByUserIdForUpdate`, còn update/remove phải được kiểm tra khi các feature đó tồn tại.
- Bổ sung unit/contract test nếu reviewer yêu cầu tách khỏi integration test hiện có.
- Chạy lại test sau khi tích hợp với CheckoutService thật của Ordering; test Shopping riêng chưa đủ chứng minh rollback order/payment/stock.

## 7. Acceptance matrix và Definition of Done

| Acceptance criterion issue #89 | Bằng chứng cần có |
| --- | --- |
| Snapshot có cart item ID, product ID, quantity; không coi giá/stock cart là nguồn chuẩn | Public record ba trường; test snapshot; consumer gọi `merchant.api` để revalidate |
| Chỉ dữ liệu buyer hiện tại; chặn Admin và truy cập chéo user | Precondition buyerId từ principal; provider kiểm tra USER ACTIVE và cart ownership; consumer authorization test |
| Xóa đúng item, chống request lặp, không xóa khi transaction thất bại | Test selected-only, duplicate/stale/foreign item, repeat-after-commit, rollback và concurrency |
| Ordering mock được contract | Unit test Ordering mock interface/record, không import nội bộ Shopping |
| Contract nằm trong `<module>.api` | Chỉ interface và immutable DTO ở `shopping.api` |
| Provider/consumer cùng review | Linh và Khoa xác nhận contract/transaction semantics trên PR |
| Có unit/contract/integration test cho thành công và lỗi | Test Shopping + mock consumer + checkout integration nhiều module |

Issue chỉ đạt DoD khi:

- `PAY 06`, `BR 19`, `NFR 06`, `AT 06` được chứng minh bằng test transaction, không chỉ bằng annotation.
- Không có import chéo module ngoài `.api`.
- Không còn điểm chưa thống nhất về buyer hiện tại hoặc request lặp.
- Không có secret, file build hoặc thay đổi ngoài phạm vi.
- Các lệnh kiểm tra phù hợp chạy thành công:

```powershell
.\mvnw.cmd -q -Dtest=CheckoutCartServiceTest,CheckoutCartServiceIntegrationTest test
.\mvnw.cmd -q -Dtest=*Checkout*Test test
.\mvnw.cmd -q test
.\mvnw.cmd -q clean package
git diff --check
git status --short
```

Nếu `CheckoutCartServiceTest` chưa được tạo thì lệnh đầu chỉ chạy test hiện hữu hoặc được điều chỉnh đúng tên; không bỏ qua test lỗi/authorization để làm build xanh.

## 8. Trạng thái kiểm tra khi lập kế hoạch

- `git diff --check`: đạt đối với các file tracked hiện tại.
- `CheckoutCartServiceIntegrationTest`: đã thử chạy nhưng môi trường đang dùng Java 11, trong khi JUnit dependency yêu cầu tối thiểu Java 17 và dự án quy định Java 21. Maven dừng với `UnsupportedClassVersionError` (class file 61, runtime chỉ hỗ trợ đến 55), nên chưa có kết luận pass/fail cho test.
- Trước khi triển khai/xác minh tiếp phải chuyển `JAVA_HOME`/runtime Maven sang JDK 21 theo README; không sửa test hoặc dependency để che lỗi môi trường.

## 9. Kết quả thực hiện

Ngày thực hiện: 13/10/2026.

- Giữ nguyên chữ ký `CheckoutCartApi` và `CheckoutCartItem`; bổ sung mô tả public contract về nguồn `buyerId`, việc Ordering phải đọc lại giá/stock và semantics fail-safe khi replay cleanup.
- Bổ sung contract test chứng minh DTO chỉ có `cartItemId`, `productId`, `quantity` và Ordering có thể mock interface mà không dùng nội bộ Shopping.
- Bổ sung unit test cho snapshot theo cart đã khóa; USER ACTIVE; từ chối ID sai, user không tồn tại, ADMIN, LOCKED và cart không thuộc buyer.
- Bổ sung integration test cho cleanup lặp sau commit: command cũ bị từ chối và không xóa item thay thế hoặc item khác.
- Xác minh code Ordering hiện không import `shopping.entity`, `shopping.repository`, `shopping.service`, `shopping.dto` hoặc `shopping.controller`.
- Không thay đổi migration, entity, controller, view, `pom.xml`, `application.properties` hoặc tài nguyên common.
- Máy không có JDK 21 nhưng có JDK 25; các lệnh xác minh được chạy bằng JDK 25 với project source level 21. Targeted tests, toàn bộ test suite và `clean package` đều thành công.
- Phần tích hợp CheckoutService thật của Ordering vẫn thuộc owner Ordering và cần được Khoa thực hiện/review khi luồng order/payment/stock được triển khai; Shopping contract hiện đã sẵn sàng để mock và tích hợp.
