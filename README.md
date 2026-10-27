# E-commerce Marketplace

![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Supabase-4169E1)
![Status](https://img.shields.io/badge/status-in%20development-yellow)

Website sàn thương mại điện tử được xây dựng cho đồ án giữa kỳ môn **Mẫu thiết kế**. Hệ thống hỗ trợ người mua, chủ gian hàng và quản trị viên trong một ứng dụng Spring Boot modular monolith.

## Chức năng chính

- Đăng ký, đăng nhập, hồ sơ và phân quyền.
- Gửi, xét duyệt, từ chối và khóa gian hàng.
- Quản lý category, product, hình ảnh và tồn kho.
- Tìm kiếm, lọc và xem sản phẩm.
- Cart, favorite và checkout nhiều shop.
- COD hoặc chuyển khoản mô phỏng.
- Theo dõi, xử lý và hủy order.
- Notification, review và dashboard shop.
- Admin quản lý account, shop, category và giám sát order.

## Công nghệ

| Thành phần | Công nghệ |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1.1, Spring MVC |
| Giao diện | Thymeleaf, Bootstrap 5, HTML/CSS/JavaScript |
| Bảo mật | Spring Security, BCrypt |
| Dữ liệu | Spring Data JPA, Hibernate, PostgreSQL/Supabase |
| Hình ảnh | Cloudinary |
| Build/Test | Maven Wrapper, JUnit, Mockito, MockMvc |

## Kiến trúc module

```text
com.senvia.doangiuaky
├── common
├── identity
├── merchant
├── shopping
├── ordering
└── engagement
```

Hệ thống áp dụng State Pattern cho shop, Strategy Pattern cho payment và Observer/Spring Event cho notification. Chi tiết nằm tại [ARCHITECTURE.md](Docs/ARCHITECTURE.md) và [MODULE_OWNERSHIP.md](Docs/MODULE_OWNERSHIP.md).

## Chạy nhanh

Yêu cầu JDK 25 và PostgreSQL/Supabase đã cấu hình.

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test
```

Ứng dụng mặc định chạy tại `http://localhost:8081`. Hướng dẫn biến môi trường, Cloudinary, đóng gói và xử lý lỗi nằm tại [SETUP.md](Docs/SETUP.md).

## Nhóm thực hiện

- **Nhóm:** 2
- **Nhóm trưởng:** Trần Thị Phương Trang (2314288)
- **Giảng viên hướng dẫn:** Đoàn Minh Khuê

Danh sách thành viên: [TEAM.md](Docs/TEAM.md).  
Phân công chi tiết: [PHAN_CONG_CONG_VIEC.md](Docs/PHAN_CONG_CONG_VIEC.md).

## Bắt đầu phát triển

1. Đọc [SRS.md](Docs/SRS.md) để xác định requirement và acceptance criteria.
2. Đọc [ARCHITECTURE.md](Docs/ARCHITECTURE.md) và [MODULE_OWNERSHIP.md](Docs/MODULE_OWNERSHIP.md).
3. Xem vị trí đặt code tại [PROJECT_STRUCTURE_GUIDE.md](Docs/PROJECT_STRUCTURE_GUIDE.md).
4. Đọc [CONTRIBUTING.md](Docs/CONTRIBUTING.md), [DEVELOPMENT_PLAYBOOK.md](Docs/DEVELOPMENT_PLAYBOOK.md) và guide module.
5. Tạo branch/commit/PR theo [GIT_WORKFLOW.md](Docs/GIT_WORKFLOW.md).

AI phải bắt đầu từ [AGENTS.md](AGENTS.md).

## Tài liệu

Xem danh mục đầy đủ tại [Docs/README.md](Docs/README.md).

| Nhu cầu | Tài liệu |
| --- | --- |
| Yêu cầu nghiệp vụ | [SRS](Docs/SRS.md) |
| Kiến trúc | [Architecture](Docs/ARCHITECTURE.md) |
| Database | [Database](Docs/DATABASE.md) |
| Thành viên | [Team](Docs/TEAM.md) |
| Phân công | [Phân công công việc](Docs/PHAN_CONG_CONG_VIEC.md) |
| Cài đặt | [Setup](Docs/SETUP.md) |
| Quy trình feature | [Development Playbook](Docs/DEVELOPMENT_PLAYBOOK.md) |
| Git/PR | [Git Workflow](Docs/GIT_WORKFLOW.md) |
| Tiêu chuẩn hoàn thành | [Contributing](Docs/CONTRIBUTING.md) |

## Bảo mật

Không commit password, token, API key, Cloudinary secret, chuỗi kết nối thật, dữ liệu cá nhân thật, `target/` hoặc cấu hình IDE cá nhân.
