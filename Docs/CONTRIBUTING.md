# Hướng dẫn đóng góp

Tài liệu này là nguồn chuẩn cho tiêu chuẩn code, kiểm thử, bảo mật và Definition of Done. Quy trình feature xem [DEVELOPMENT_PLAYBOOK.md](DEVELOPMENT_PLAYBOOK.md); thao tác Git xem [GIT_WORKFLOW.md](GIT_WORKFLOW.md).

## 1. Tiêu chuẩn mã nguồn

- Dùng Java 25 và quy ước đặt tên Java chuẩn.
- Package bắt đầu bằng `com.senvia.doangiuaky`.
- Controller chỉ nhận request, gọi service và trả response/view; không gọi Repository trực tiếp.
- Service chứa use case, business rule, transaction và kiểm tra ownership.
- DTO dùng cho input/output; không trả Entity có trường nội bộ hoặc nhạy cảm.
- Dữ liệu đầu vào phải được validate tại backend.
- Tiền tệ dùng `BigDecimal`; database dùng kiểu thập phân chính xác.
- Nghiệp vụ cập nhật nhiều bảng phải xem xét `@Transactional`.
- Không tạo abstraction hoặc dependency mới khi chưa có nhu cầu cụ thể.

Vị trí đặt code/test xem [PROJECT_STRUCTURE_GUIDE.md](PROJECT_STRUCTURE_GUIDE.md).

## 2. Ranh giới module

- Chỉ sửa module trong phạm vi issue.
- Module khác chỉ dùng contract trong `<module>.api`.
- Không import Entity, Repository hoặc Service nội bộ chéo module.
- State đặt trong `merchant.state`, Strategy trong `ordering.strategy`, listener Observer trong `engagement.event`.
- Template và static asset riêng đặt trong thư mục module sở hữu.
- Thay đổi contract, migration hoặc tài nguyên dùng chung phải có chủ sở hữu review.

Nguồn ownership chính thức: [MODULE_OWNERSHIP.md](MODULE_OWNERSHIP.md).

## 3. Bảo mật và dữ liệu

- Không commit password, token, API key, Cloudinary secret hoặc chuỗi kết nối thật.
- Không ghi thông tin xác thực vào log, exception hoặc response.
- Không dùng dữ liệu cá nhân thật trong seed/demo.
- Phân quyền và ownership phải được kiểm tra ở backend.
- Không commit `target/`, file IDE hoặc cấu hình máy cá nhân.

## 4. Kiểm thử tối thiểu

| Loại test | Áp dụng |
| --- | --- |
| Unit test | Service, State, Strategy và business rule |
| Repository test | Mapping, constraint và query tùy chỉnh |
| MVC test | Route, validation, quyền, model/redirect |
| Integration test | Transaction, rollback và contract chéo module |
| Manual test | Thymeleaf, responsive và luồng người dùng |

Test cần bao phủ luồng thành công, validation, ngoại lệ quan trọng và truy cập trái quyền. Lệnh chạy nằm tại [SETUP.md](SETUP.md).

## 5. Checklist trước review

- [ ] Requirement và acceptance criteria đã được đáp ứng.
- [ ] Chỉ thay đổi đúng phạm vi module/task.
- [ ] Validation, phân quyền và ownership ở backend đầy đủ.
- [ ] Transaction/rollback/idempotency phù hợp.
- [ ] Test mới và test liên quan đã chạy.
- [ ] Không có secret hoặc file sinh tự động.
- [ ] Migration, contract, config và tài nguyên chung có người review đúng.
- [ ] Tài liệu được cập nhật nếu hành vi thay đổi.
- [ ] UI có ảnh minh chứng nếu cần.

## 6. Definition of Done

Một task hoàn thành khi:

- Đáp ứng acceptance criteria trong issue/SRS.
- Code biên dịch và test ở mức phù hợp thành công.
- Không vi phạm kiến trúc hoặc ownership.
- Có validation, xử lý lỗi và bảo mật phù hợp.
- Contract và tài liệu liên quan được cập nhật.
- Hoàn tất checklist module.
- Pull request được review và merge vào `main`.

## 7. Issue

Bug report cần nêu môi trường, bước tái hiện, kết quả mong đợi/thực tế, log đã loại secret và ảnh nếu liên quan UI.

Feature proposal cần nêu vấn đề, requirement, tác động dữ liệu, phân quyền, contract, giao diện và phạm vi kiểm thử.
