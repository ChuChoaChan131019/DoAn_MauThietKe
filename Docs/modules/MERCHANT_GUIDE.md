# Hướng dẫn module Merchant

[← Playbook](../DEVELOPMENT_PLAYBOOK.md) · [Phân công](../PHAN_CONG_CONG_VIEC.md)

## Phạm vi

- **Chủ sở hữu:** Trần Thị Phương Trang (2314288) — Nhóm trưởng.
- **Requirement:** SHOP 01–10, PROD 01–11; cung cấp dữ liệu STAT 01.
- **Bảng:** `shops`, `categories`, `products`, `product_images`, `stock_restorations`.
- **Code/view:** `com.senvia.doangiuaky.merchant`, `templates/merchant`.

Merchant sở hữu vòng đời shop, category, product, ảnh và stock.

## Dependency và public contract

- Dùng `identity.api` để kiểm tra user, Admin, owner và account status.
- Cung cấp `merchant.api`: shop/product summary, giá, stock, khả năng bán, trừ/hoàn stock và product count.
- `CartProductView#isSaleable()` là kết quả khả năng bán chuẩn trong public contract; module tiêu thụ không truy cập Entity/Repository/Service nội bộ.
- `MerchantApi#findProductForCart` trả summary khi product tồn tại nhưng không bán được; `Optional.empty()` chỉ dành cho ID không hợp lệ hoặc product không tồn tại.
- `MerchantApi#findProductForCartMutation` yêu cầu transaction đang mở, khóa pessimistic row `products` đến hết transaction và không thay đổi stock; Shopping dùng contract này sau khi khóa cart để add/update quantity bằng stock authoritative.
- `MerchantApi#findShopSummary(shopId)` trả ID, owner ID, tên và cờ trạng thái tối thiểu, kể cả shop chưa được hiển thị công khai; không cần product của shop để tra cứu.
- `MerchantStockApi#deductForCheckout(shopId, productId, quantity, expectedUnitPrice)` yêu cầu transaction của Ordering, kiểm lại khả năng bán và giá, rồi trừ kho bằng conditional update. Không đủ stock hoặc giá đổi sẽ ném `MerchantStockException` để rollback checkout.
- `MerchantStockApi#restoreCancelledOrderItem(orderItemId, shopId, productId, quantity)` yêu cầu transaction hủy đơn của Ordering. `orderItemId` là khóa chống lặp; cùng payload gọi lại không cộng thêm, payload khác bị từ chối. Merchant không truy cập Entity của Ordering và vẫn hoàn kho cho product đã ẩn.
- `MerchantStatisticsApi#countProductsForShop(shopId)` yêu cầu user đang đăng nhập là owner ACTIVE của shop; đếm cả product ẩn hoặc hết hàng. `countProductsForCurrentShop()` lấy shop từ owner ACTIVE đang đăng nhập, không nhận shop ID từ client. Quyền vào trang dashboard của shop LOCKED do route consumer quyết định.
- Khi checkout khóa nhiều product, Ordering gọi theo `productId` tăng dần sau khi khóa cart. Các đường chủ shop sửa product cũng khóa hàng product để tránh ghi đè stock.
- Form sửa product gửi `originalStockQuantity` là tồn kho lúc mở form. Sau khi khóa và đọc lại product, nếu stock đã đổi (ví dụ checkout vừa trừ kho), Merchant từ chối lưu và yêu cầu tải lại trang; không ghi đè stock mới bằng dữ liệu form cũ. Luồng sửa ảnh dọn asset vừa upload khi phát hiện xung đột.
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

## Catalog công khai

- Menu danh mục trên header (desktop/mobile) và các thẻ danh mục trang chủ lấy danh mục active từ bảng `categories`; link lọc dùng `categoryId`. Danh mục bị ẩn không xuất hiện trong menu công khai.
- `GET /products` nhận `categoryId` cho liên kết topbar, `categoryIds` lặp để chọn nhiều danh mục active ở sidebar; tham số `category=<tên>` cũ vẫn được hỗ trợ cho bookmark/link cũ và chỉ tra trong danh mục active. `categoryIds` được ưu tiên, sau đó đến `categoryId`, rồi tên cũ.
- `GET /products` nhận `page` bắt đầu từ 1 và `size` thuộc 12/24/48; mặc định là trang 1 với 12 sản phẩm. Giá trị sai hiển thị lỗi và dùng giá trị mặc định an toàn; trang vượt cuối được đưa về trang cuối. Link trang giữ từ khóa, danh mục, giá, shop, sort và size; đổi bộ lọc hoặc size quay về trang 1.
- Catalog đếm kết quả theo shop đủ điều kiện công khai và lấy riêng trang cần hiển thị từ database sau khi áp dụng toàn bộ bộ lọc. Trang mặc định giữ thứ tự ID tăng dần hiện hành; sắp giá tăng/giảm dùng ID tăng dần làm thứ tự phụ để các trang không trùng sản phẩm khi giá bằng nhau. Ảnh chỉ được tải cho sản phẩm trên trang hiện tại.
- `GET /products?q=<keyword>` tìm theo tên sản phẩm, khớp một phần và không phân biệt hoa thường. Từ khóa được trim; null/rỗng/toàn khoảng trắng nghĩa là không áp dụng tìm kiếm. Keyword tối đa 200 ký tự; vượt giới hạn thì trả lại trang catalog với lỗi validation và không truy vấn kết quả.
- Tìm kiếm sản phẩm ở catalog và header desktop/mobile cùng gửi GET tới `/products` bằng tham số `q`. Tìm theo tên sản phẩm, không tìm mô tả hay tên shop.
- Ký tự `%`, `_` và `!` trong từ khóa được escape trước khi dùng trong điều kiện `LIKE`; người dùng tìm ký tự literal thay vì kích hoạt wildcard SQL.
- `min` và `max` là số thập phân không âm, tối đa 10 chữ số phần nguyên và 2 chữ số thập phân; hai cận bao gồm giá trị biên và cần thỏa `min <= max`. Input sai được hiển thị lại trên trang với lỗi validation, không chạy truy vấn lọc.
- Danh mục, khoảng giá, từ khóa và shop được kết hợp theo AND. Kết quả vẫn phải qua quy tắc saleable: owner ACTIVE, shop APPROVED, category active, product ACTIVE và stock > 0.
- Sidebar hiển thị mọi danh mục active dưới dạng ô chọn nhiều mục; ô đã chọn có nền đậm. Các ô lọc submit danh mục đã chọn cùng từ khóa, khoảng giá, gian hàng và thứ tự sắp xếp. Tiêu đề thể hiện danh mục đang chọn; nút bỏ các bộ lọc khác giữ nguyên danh mục. `Xóa tất cả bộ lọc` về `/products`.
- Tên danh mục cũ không tồn tại hoặc đã bị ẩn cho thông báo chung và danh sách rỗng; không phản hồi tên danh mục ẩn.
- Lọc catalog dùng repository Merchant; không làm thay đổi public contract `merchant.api`.

## Quy tắc đặc thù

Trang quản lý sản phẩm người bán `/merchant/products` phân trang ở database theo `id` tăng dần, mặc định 12 sản phẩm/trang; có thể chọn 24 hoặc 48. `page` và `size` sai được thay bằng giá trị mặc định kèm thông báo; trang vượt cuối chuyển về trang cuối. Truy vấn luôn giới hạn theo shop của người bán đã xác thực.

- Một user tối đa một shop; từ chối/khóa bắt buộc lý do.
- Chỉ Admin đổi trạng thái xét duyệt.
- PENDING/REJECTED/LOCKED không sửa catalog hoặc nhận order mới.
- LOCKED vẫn cho xử lý order đã tồn tại.
- Product chỉ `ACTIVE/HIDDEN`; hết hàng suy từ stock bằng 0.
- Giá dùng `BigDecimal` tương ứng `NUMERIC(12,2)`, phải lớn hơn 0 và có tối đa 2 chữ số thập phân; stock là số nguyên không âm và product có ít nhất một ảnh.
- Catalog chỉ hiện khi owner ACTIVE, shop APPROVED, category active, product ACTIVE và stock > 0.
- Trừ stock dùng conditional update nguyên tử; hoàn stock phải chống lặp.

## Kiểm thử/bàn giao

Ảnh của 120 sản phẩm mẫu được lưu trên Cloudinary trong `senvia/merchant/product-images/demo/`, mỗi sản phẩm có public ID riêng. Migration `V202610151700` cập nhật cả `image_url` và `public_id` của ảnh `external-demo:`; ảnh shop tự upload không bị thay thế. Migration chỉ lưu metadata do Cloudinary trả về, không gọi API upload và không chứa thông tin xác thực. Không sửa các migration seed đã áp dụng.

Migration `V202610151800` đổi các sản phẩm mẫu có tên chung trong danh mục `Công nghệ` thành tên thiết bị cụ thể, cập nhật mô tả và thay bằng ảnh Cloudinary tương ứng. Chỉ các sản phẩm còn giữ tên demo chung và ảnh demo mới được cập nhật.

Migration `V202610151900` bỏ tiền tố `Senvia Demo` và hậu tố danh mục khỏi tên của mọi sản phẩm mẫu còn lại. Các tên `Sản phẩm tuyển chọn` chưa được migration Công nghệ xử lý được thay bằng tên loại hàng theo ảnh mẫu. Chỉ sản phẩm gắn ảnh seed trong thư mục Cloudinary `demo/` được đổi; sản phẩm do shop tạo không bị tác động.

Test State, ownership, validation, catalog visibility, category/owner/shop bị khóa, stock concurrency và lỗi Cloudinary. Lê Anh Khoa review stock contract; Đỗ Đặng Diệu Linh review dữ liệu cart; Huỳnh Thiên Phúc review event/dashboard contract.

Checklist chất lượng chung xem [CONTRIBUTING.md](../CONTRIBUTING.md).

## Contract trạng thái nhận đơn của shop

- `MerchantApi#findShopOrderCapabilities(shopId)` là contract để Ordering kiểm tra khả năng xử lý đơn mà không truy cập nội bộ Merchant.
- `canReceiveNewOrders` chỉ bật khi owner đang hoạt động và trạng thái shop cho phép nhận đơn mới.
- `canHandleExistingOrders` là quyền độc lập với nhận đơn mới. Shop `LOCKED` trả về `false/true`: không nhận đơn mới nhưng vẫn được xử lý các đơn đã tạo trước lúc khóa.
- Shop `PENDING` hoặc `REJECTED` trả về `false/false`; ID không hợp lệ hoặc shop không tồn tại trả về `Optional.empty()`.
- Contract chỉ cung cấp khả năng của shop. Ordering vẫn chịu trách nhiệm xác định đơn đã tồn tại trước lúc khóa và kiểm tra ownership của đơn.
