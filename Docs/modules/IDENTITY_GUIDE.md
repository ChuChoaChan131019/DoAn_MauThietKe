# Hướng dẫn module Identity

[← Playbook](../DEVELOPMENT_PLAYBOOK.md) · [Phân công](../PHAN_CONG_CONG_VIEC.md)

## Phạm vi

- **Chủ module:** Doàn Trương Duy Khang (2111844).
- **Feature owner AUTH 08 / Issue #68:** Trần Thị Phương Trang (2314288); Khang review theo ownership Identity.
- **Requirement:** AUTH 01–07 do Khang phụ trách; AUTH 08 do Trang triển khai.
- **Bảng:** `users`.
- **Code/view:** `com.senvia.doangiuaky.identity`, `com.senvia.doangiuaky.common`, `templates/identity`.

Identity sở hữu account, authentication, profile, role/status và Spring Security; Khang là chủ module và review tài nguyên common/config cùng feature AUTH 08 của Trang.

Trang quản trị tài khoản do Identity xử lý và dùng URL công khai `/admin/accounts`, thống nhất với các trang quản trị khác. Route mock cũ trong `engagement` đã được bỏ để Identity sở hữu URL này.

## Public contract

`identity.api` cung cấp `IdentityApi.findUser`, `findUserByEmail`, `userExists`, `isUserActive`,
`findActiveAdmins` và `CurrentUserApi.requireCurrentUser`. `CurrentUserApi` lấy account từ authenticated
principal và đọc lại trạng thái hiện tại trong database; module sử dụng không được truyền user ID do client
kiểm soát để thay thế danh tính này. DTO `UserSummary` chỉ có user ID, tên công khai, role và account
status; không công bố password hash, User Entity/Repository hoặc principal nội bộ.

## Thứ tự feature

1. Migration User, Role và AccountStatus.
2. Chuẩn hóa email và unique index `LOWER(email)`.
3. Registration và BCrypt.
4. Login/logout, LOCKED account và session invalidation.
5. Profile/avatar và đổi password.
6. AUTH 08: Admin list/search/filter/detail/lock/unlock USER (Trang; Khang review).
7. Identity API.
8. Security/common error pages và shared navigation.

## Quy tắc đặc thù

- Email trim/lowercase trước kiểm tra và lưu.
- Password chỉ lưu BCrypt và không xuất hiện trong DTO/log.
- User chỉ sửa profile/password của mình.
- Đổi password yêu cầu password hiện tại đúng.
- Admin không mua hàng; chức năng quản lý tài khoản chỉ khóa/mở USER, không thao tác với ADMIN.
- Khóa user bắt buộc lý do, người khóa và thời điểm.
- Owner LOCKED làm catalog ẩn và shop ngừng nhận order mới.

## Kiểm thử/bàn giao

Test email, BCrypt, authentication, LOCKED/session, profile/password ownership, USER/ADMIN authorization và lock/unlock. Trần Thị Phương Trang review contract phục vụ Merchant; Đỗ Đặng Diệu Linh review Admin ACTIVE phục vụ notification; thay đổi common/config cần PR riêng.

Checklist chất lượng chung xem [CONTRIBUTING.md](../CONTRIBUTING.md).
