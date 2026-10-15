# Nhóm phát triển

## Thông tin chung

- **Nhóm:** 2
- **Môn học:** Mẫu thiết kế
- **Giảng viên hướng dẫn:** Đoàn Minh Khuê
- **Nhóm trưởng:** Trần Thị Phương Trang (2314288)
- **Quy mô:** 5 thành viên

## Thành viên

| TV | Họ và tên | MSSV | Vai trò chính | Module |
| --- | --- | --- | --- | --- |
| TV1 | Trần Thị Phương Trang | 2314288 | Merchant Owner; AUTH 08 Feature Owner | [`merchant`](modules/MERCHANT_GUIDE.md), AUTH 08 trong [`identity`](modules/IDENTITY_GUIDE.md) |
| TV2 | Lê Anh Khoa | 2312647 | Ordering Owner | [`ordering`](modules/ORDERING_GUIDE.md) |
| TV3 | Đỗ Đặng Diệu Linh | 2312663 | Shopping Owner; Notification/Observer Feature Owner | [`shopping`](modules/SHOPPING_GUIDE.md), notifications trong [`engagement`](modules/ENGAGEMENT_GUIDE.md) |
| TV4 | Doàn Trương Duy Khang | 2111844 | Identity/Common Owner | [`identity`](modules/IDENTITY_GUIDE.md), `common` |
| TV5 | Huỳnh Thiên Phúc | 2113010 | Engagement Owner; Review/Dashboard/Admin Order | [`engagement`](modules/ENGAGEMENT_GUIDE.md) |

Phạm vi công việc chi tiết, contract, test và đầu ra của từng người nằm tại [PHAN_CONG_CONG_VIEC.md](PHAN_CONG_CONG_VIEC.md). Quyền sở hữu bảng và tài nguyên lấy theo [MODULE_OWNERSHIP.md](MODULE_OWNERSHIP.md).

## Ma trận review

| Phạm vi | Phụ trách chính | Review chính |
| --- | --- | --- |
| `merchant` | Trần Thị Phương Trang | Lê Anh Khoa |
| `ordering` | Lê Anh Khoa | Đỗ Đặng Diệu Linh |
| `shopping` | Đỗ Đặng Diệu Linh | Lê Anh Khoa |
| Notifications/Observer trong `engagement` | Đỗ Đặng Diệu Linh | Huỳnh Thiên Phúc; Lê Anh Khoa review event Ordering; Trần Thị Phương Trang review event Merchant |
| `identity`, `common` | Doàn Trương Duy Khang | Trần Thị Phương Trang |
| AUTH 08 / Issue #68 trong `identity` | Trần Thị Phương Trang | Doàn Trương Duy Khang (Identity owner) |
| Review, dashboard và Admin order trong `engagement` | Huỳnh Thiên Phúc | Doàn Trương Duy Khang |

Review chuyên môn chéo contract được xác định thêm trong module guide và tài liệu phân công.

## Quy tắc ghi nhận đóng góp

- Mỗi công việc phải có issue/task, người thực hiện và người review.
- Commit và pull request dùng tài khoản GitHub của đúng người thực hiện.
- Người review không đồng thời là tác giả duy nhất của pull request.
- Thay đổi chéo module phải được chủ module liên quan review.
- AUTH 08 do Trang triển khai trong `identity`; Khang giữ module ownership và review theo ranh giới Identity.
- Thay đổi notification/listener do Linh phụ trách vẫn cần Phúc review ownership trong `engagement`; event contract cần owner module phát event review.
- `pom.xml`, `application.properties`, `common`, fragment và static common cần Doàn Trương Duy Khang review.
- Thành viên cập nhật trạng thái trước mỗi buổi họp nhóm.

## Thông tin cần bổ sung

- GitHub username của từng thành viên.
- Kênh trao đổi chính của nhóm.
- Nơi lưu biên bản họp và quyết định kỹ thuật.
