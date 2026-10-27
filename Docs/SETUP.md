# Hướng dẫn cài đặt và chạy dự án

## 1. Yêu cầu môi trường

| Thành phần | Yêu cầu                               |
| ------------ | --------------------------------------- |
| JDK          | Java 25                                 |
| Build        | Maven Wrapper đi kèm repository       |
| Database     | H2 local/test hoặc PostgreSQL/Supabase |
| Lưu ảnh    | Cloudinary khi triển khai upload       |
| Công cụ    | Git và IDE hỗ trợ Java 25            |

Kiểm tra:

```powershell
java -version
git --version
.\mvnw.cmd -version
```

Maven phải sử dụng JDK 25.

## 2. Lấy mã nguồn

```bash
git clone <repository-url>
cd MauThietKe_DoAnGiuaKy
```

## 3. Cấu hình PostgreSQL

Không ghi thông tin đăng nhập thật vào source code hoặc Git.

| Biến môi trường               | Ý nghĩa                                | Ví dụ                                        |
| --------------------------------- | ---------------------------------------- | ---------------------------------------------- |
| `SPRING_DATASOURCE_URL`         | JDBC URL                                 | `jdbc:postgresql://localhost:5432/ecommerce` |
| `SPRING_DATASOURCE_USERNAME`    | Database user                            | `postgres`                                   |
| `SPRING_DATASOURCE_PASSWORD`    | Database password                        | `your_password`                              |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | Chính sách schema                      | `validate`                                   |
| `INITIAL_ADMIN_EMAIL`           | Email khởi tạo Admin (tùy chọn)      | `admin@example.test`                         |
| `INITIAL_ADMIN_PASSWORD`        | Mật khẩu Admin khởi tạo (tùy chọn) | Chỉ cung cấp qua môi trường               |

Để khởi tạo Admin đầu tiên sau khi migration tạo bảng `users`, đặt `INITIAL_ADMIN_EMAIL` và `INITIAL_ADMIN_PASSWORD` trong môi trường chạy. Có thể đặt `INITIAL_ADMIN_FULL_NAME`; mặc định là `Platform Administrator`. Tài khoản chỉ được tạo khi email chưa tồn tại; nếu email đã thuộc USER, ứng dụng dừng khởi động và không tự nâng quyền.

### 3.1. Kết nối Supabase dùng chung

Nhóm sử dụng PostgreSQL của Supabase cho môi trường tích hợp. Người tạo project thực hiện:

1. Tạo một project Supabase dùng chung cho nhóm và lưu Database Password ở nơi an toàn.
2. Trong project, chọn **Connect** và dùng **Session pooler**. Lấy `Host`, `Port`, `Database` và `User` từ phần connection parameters.
3. Không tạo bảng thủ công bằng Table Editor. Bảng phải được tạo bằng migration của module sở hữu.
4. Mỗi thành viên cấu hình biến môi trường trên máy của mình. Ví dụ PowerShell:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require"
$env:SPRING_DATASOURCE_USERNAME="postgres.odazeignupnklcxgomrq"
$env:SPRING_DATASOURCE_PASSWORD="<DATABASE_PASSWORD>"
$env:SPRING_JPA_HIBERNATE_DDL_AUTO="validate"
$env:SPRING_FLYWAY_LOCATIONS="classpath:db/migration/postgresql"
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME="org.postgresql.Driver"
```

Thay các giá trị trong dấu `<...>` bằng thông tin thật của project. Các lệnh `$env:` chỉ có hiệu lực trong cửa sổ PowerShell hiện tại; cần chạy lại khi mở terminal mới. Không đưa password hoặc connection string có password vào source code, issue, ảnh chụp hay Git.

Nếu chỉ chạy unit test hoặc phát triển khi chưa có schema tích hợp, có thể dùng cấu hình H2 mặc định của repository. Khi chạy ứng dụng với Supabase, cần bảo đảm migration đã được chạy theo đúng thứ tự.

## 4. Cấu hình Cloudinary

Upload ảnh đại diện dùng biến môi trường; không lưu thông tin xác thực thật trong repository:

```text
CLOUDINARY_CLOUD_NAME
CLOUDINARY_API_KEY
CLOUDINARY_API_SECRET
```

Lấy đúng ba giá trị thuộc cùng một Product Environment tại **Cloudinary → Settings → API Keys**:

```text
CLOUDINARY_CLOUD_NAME=<CLOUDINARY_CLOUD_NAME>
CLOUDINARY_API_KEY=<CLOUDINARY_API_KEY>
CLOUDINARY_API_SECRET=<cấu hình riêng trên máy/server, không commit>
```

có thể tự tạo API để sử dụng

PowerShell:

```powershell
$env:CLOUDINARY_CLOUD_NAME="cewdqekr"
$env:CLOUDINARY_API_KEY="<CLOUDINARY_API_KEY>"
$env:CLOUDINARY_API_SECRET="<CLOUDINARY_API_SECRET>"
```

Thay cả ba placeholder bằng Cloud name, API Key và API Secret lấy từ cùng một dòng credential tại **Cloudinary → Settings → API Keys**.

Các biến trên được ánh xạ vào `app.cloudinary.*`. Nếu khai báo bằng lệnh `$env:` thì biến chỉ tồn tại trong cửa sổ PowerShell hiện tại; mở terminal mới cần khai báo lại. Nếu lưu trong Windows Environment Variables hoặc Environment Variables của server thì chỉ cần cấu hình một lần, không cần nhập lại mỗi lần chạy ứng dụng. Thiếu cấu hình không ngăn ứng dụng khởi động, nhưng thao tác upload sẽ báo lỗi cấu hình rõ ràng. Không đưa API secret vào log, issue, ảnh chụp hoặc dữ liệu demo.

## 5. Chạy và đóng gói

Windows:

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test
.\mvnw.cmd clean package
```

Linux/macOS:

```bash
chmod +x mvnw
./mvnw spring-boot:run
./mvnw test
./mvnw clean package
```

Ứng dụng mặc định chạy tại `http://localhost:8081`. File JAR được tạo trong `target/`; không commit thư mục này.

## 6. Database migration

Migration được đặt tại:

```text
src/main/resources/db/migration
```

Quy tắc đặt tên, review và dữ liệu mẫu xem [DATABASE.md](DATABASE.md). Không chỉnh trực tiếp schema dùng chung mà không có migration tương ứng.
Mặc định repository dùng H2 và migration H2 để chạy local/test; dữ liệu H2 trong bộ nhớ sẽ mất khi ứng dụng khởi động lại. Khi kết nối PostgreSQL hoặc Supabase, đặt `SPRING_FLYWAY_LOCATIONS=classpath:db/migration/postgresql` để Flyway chạy đúng bộ migration PostgreSQL.

Chủ module tạo migration cho các bảng thuộc module mình. `identity` tạo `users` trước; các module có khóa ngoại tới `users` cần được review và chạy sau khi bảng này tồn tại. Khi repository chưa có công cụ migration tự động, người quản lý môi trường chạy từng file SQL trên Supabase SQL Editor theo thứ tự tên phiên bản, sau khi đã review.

## 7. Lỗi thường gặp

### Sai Java

Kiểm tra `JAVA_HOME` và kết quả `.\mvnw.cmd -version`.

### Không kết nối PostgreSQL

- Kiểm tra URL, username, password và database.
- Với Supabase, kiểm tra host, port và SSL.
- Không đăng log chứa chuỗi kết nối hoặc mật khẩu.

### Test ApplicationContext thất bại

Các test tải Spring context cần datasource/test database phù hợp. Không xóa test hoặc vô hiệu auto-configuration chỉ để build xanh.

### Cổng 8081 bị chiếm

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8082"
```

## 8. Bước tiếp theo

Sau khi chạy được dự án, đọc [CONTRIBUTING.md](CONTRIBUTING.md), [DEVELOPMENT_PLAYBOOK.md](DEVELOPMENT_PLAYBOOK.md) và guide của module được giao.
